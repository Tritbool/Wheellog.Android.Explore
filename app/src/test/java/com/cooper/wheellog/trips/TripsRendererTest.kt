package com.cooper.wheellog.trips

import android.app.Application
import android.net.Uri
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.*
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.data.TripDao
import com.cooper.wheellog.data.TripRepository
import com.cooper.wheellog.data.TripItemState
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TripsRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before fun setup() {
        context.setTheme(R.style.OriginalTheme)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt("versionSettings", 1).commit()
    }

    @Test fun `lifecycle stops IO collection and refreshes while stopped are loaded on restart`() {
        val owner = mockk<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(owner)
        every { owner.lifecycle } returns lifecycle
        lifecycle.currentState = Lifecycle.State.CREATED
        val repository = mockk<TripRepository>()
        coEvery { repository.loadItems(any(), any(), any()) } returns emptyList()
        val renderer = TripsPageRenderer(
            LayoutInflater.from(context).inflate(R.layout.main_view_trips, null),
            repository, AppConfig(context), TripsScroll(), owner
        )
        try {
            renderer.start()
            idle()
            coVerify(exactly = 0) { repository.loadItems(any(), any(), any()) }
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            coVerify(exactly = 1) { repository.loadItems(any(), false, any()) }
            renderer.refresh()
            idle()
            coVerify(exactly = 2) { repository.loadItems(any(), false, any()) }
            lifecycle.currentState = Lifecycle.State.CREATED
            idle()
            renderer.refresh()
            idle()
            coVerify(exactly = 2) { repository.loadItems(any(), false, any()) }
            lifecycle.currentState = Lifecycle.State.STARTED
            idle()
            coVerify(exactly = 3) { repository.loadItems(any(), false, any()) }
            renderer.preferences(true, R.style.AJDMTheme, true, true)
            idle()
            coVerify(exactly = 1) { repository.loadItems(any(), true, any()) }
            renderer.stop()
            idle()
            renderer.refresh()
            idle()
            coVerify(exactly = 4) { repository.loadItems(any(), any(), any()) }
        } finally {
            renderer.dispose()
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
        }
    }

    @Test fun `real pager trips route defaults on with persistent independent fallback and cleanup`() {
        val config = AppConfig(context)
        val model = mockk<BleSessionViewModel>(relaxed = true)
        val dao = mockk<TripDao>(relaxed = true)
        val activity = mockk<MainActivity>(relaxed = true)
        val lifecycle = LifecycleRegistry(activity)
        every { activity.lifecycle } returns lifecycle
        lifecycle.currentState = Lifecycle.State.CREATED
        startKoin { modules(module {
            single { config }
            single { model }
            single { dao }
        }) }
        val recycler = RecyclerView(context)
        val adapter = MainPageAdapter(mutableListOf(R.layout.main_view_trips), activity)
        try {
            val holder = adapter.createViewHolder(recycler, R.layout.main_view_trips)
            adapter.bindViewHolder(holder, 0)
            val page = holder.itemView
            val compose = page.findViewById<ComposeView>(R.id.tripsComposeView)
            val views = page.findViewById<RecyclerView>(R.id.list_trips)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            assertThat(views.visibility).isEqualTo(View.GONE)
            config.useComposeTelemetry = false
            config.useComposeEvents = false
            holder.tripsRenderer!!.preferences(config.useComposeTrips, config.appTheme, false, false)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            config.useComposeTrips = false
            holder.tripsRenderer!!.preferences(config.useComposeTrips, config.appTheme, false, false)
            assertThat(views.visibility).isEqualTo(View.VISIBLE)
            assertThat(compose.visibility).isEqualTo(View.GONE)
            assertThat(AppConfig(context).useComposeTrips).isFalse()
            config.useComposeTrips = true
            holder.tripsRenderer!!.preferences(config.useComposeTrips, config.appTheme, false, false)
            assertThat(holder.itemView).isSameInstanceAs(page)
            assertThat(compose.visibility).isEqualTo(View.VISIBLE)
            adapter.onViewRecycled(holder)
            assertThat(holder.tripsRenderer).isNull()
            assertThat(views.adapter).isNull()
            verify { model wasNot Called }
            verify { dao wasNot Called }
        } finally {
            adapter.onDetachedFromRecyclerView(recycler)
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
            stopKoin()
        }
    }

    @Test fun `delete failure still requests a fresh file list after possible disk mutation`() {
        val owner = mockk<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(owner)
        every { owner.lifecycle } returns lifecycle
        lifecycle.currentState = Lifecycle.State.STARTED
        val repository = mockk<TripRepository>()
        coEvery { repository.loadItems(any(), any(), any()) } returns emptyList()
        coEvery { repository.deleteFile(any(), any()) } throws IllegalStateException("DB failed after file deletion")
        val renderer = TripsPageRenderer(
            LayoutInflater.from(context).inflate(R.layout.main_view_trips, null),
            repository, AppConfig(context), TripsScroll(), owner
        )
        try {
            renderer.start()
            idle()
            coVerify(exactly = 1) { repository.loadItems(any(), any(), any()) }
            val item = TripItemState(Uri.parse("content://media/external/downloads/42"), "ride.csv", null, "ride", "", "")
            TripsPageRenderer::class.java.getDeclaredMethod("delete", TripItemState::class.java).apply {
                isAccessible = true
            }.invoke(renderer, item)
            idle()
            coVerify(exactly = 1) { repository.deleteFile(any(), item) }
            coVerify(exactly = 2) { repository.loadItems(any(), any(), any()) }
        } finally {
            renderer.dispose()
            lifecycle.currentState = Lifecycle.State.DESTROYED
            idle()
        }
    }
}
