package com.cooper.wheellog.scan

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.State
import androidx.compose.ui.platform.ComposeView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ScanActivity
import com.cooper.wheellog.ScanPageRenderer
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.google.common.truth.Truth.assertThat
import io.github.tritbool.euc.ble.models.EUCDevice
import io.github.tritbool.euc.ble.protocols.EUCProtocol
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ScanActivityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var config: AppConfig
    private lateinit var session: BleSessionViewModel
    private val flow = MutableStateFlow(BleSessionState.EMPTY)
    private var controller: ActivityController<ScanActivity>? = null

    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(application).edit().clear()
            .putInt("versionSettings", 1).commit()
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setEnabled(true)
        config = AppConfig(application)
        config.setValue("use_compose_scan", false)
        config.lastMac = "AA:BB:CC:DD:EE:FF"
        session = mockk(relaxed = true)
        every { session.sessionState } returns flow
        every { session.getAvailableProtocols() } returns emptyList()
        startKoin { modules(module { single { config }; single { session } }) }
    }

    @After fun cleanup() {
        controller?.pause()?.stop()?.destroy()
        idle()
        stopKoin()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun renderer(activity: ScanActivity) = activity.javaClass.getDeclaredField("renderer")
        .apply { isAccessible = true }.get(activity) as ScanPageRenderer
    @Suppress("UNCHECKED_CAST")
    private fun state(activity: ScanActivity) = (ScanPageRenderer::class.java.getDeclaredField("state")
        .apply { isAccessible = true }.get(renderer(activity)) as State<ScanUiState>).value
    @Suppress("UNCHECKED_CAST")
    private fun <T> callback(activity: ScanActivity, name: String): T =
        ScanPageRenderer::class.java.getDeclaredField(name).apply { isAccessible = true }
            .get(renderer(activity)) as T
    private fun edit(activity: ScanActivity, value: String) {
        callback<(String) -> Unit>(activity, "onAddressChanged")(value)
    }
    private fun submit(activity: ScanActivity) {
        callback<() -> Unit>(activity, "onManualSelect")()
    }
    private fun select(activity: ScanActivity, address: String) {
        callback<(String) -> Unit>(activity, "onSelect")(address)
    }
    private fun forceProtocol(activity: ScanActivity, address: String) {
        callback<(String) -> Unit>(activity, "onForceProtocol")(address)
    }
    private fun launch(saved: Bundle? = null): ScanActivity {
        controller = Robolectric.buildActivity(ScanActivity::class.java).create(saved).start().resume().visible()
        idle()
        return controller!!.get()
    }

    private fun dialog(activity: ScanActivity) =
        activity.javaClass.getDeclaredField("alertDialog").apply { isAccessible = true }.get(activity) as AlertDialog

    private fun device(address: String = "11:22:33:44:55:66", name: String? = "Wheel") =
        mockk<EUCDevice>(relaxed = true).also {
            every { it.address } returns address
            every { it.name } returns name
        }

    private fun completeScan(devices: List<EUCDevice> = emptyList()) {
        flow.value = flow.value.copy(isScanning = true, scanResults = devices)
        idle()
        flow.value = flow.value.copy(isScanning = false)
        idle()
    }

    @Test fun `one scan per resume timeout stops radio and shows manual entry`() {
        val activity = launch()
        verify(exactly = 1) { session.startScan() }
        assertThat(state(activity).scanning).isTrue()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertThat(state(activity).scanning).isFalse()
        verify(exactly = 1) { session.stopScan() }
        assertThat(activity.isFinishing).isFalse()
    }

    @Test fun `manual validation keeps dialog then returns only MAC and clears password`() {
        val activity = launch()
        assertThat(dialog(activity).isShowing).isTrue()
        assertThat(dialog(activity).window!!.attributes.flags and
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM).isEqualTo(0)
        completeScan()
        edit(activity, "bad")
        submit(activity)
        assertThat(state(activity).invalidAddress).isTrue()
        assertThat(activity.isFinishing).isFalse()
        config.passwordForWheel = "old"
        edit(activity, "12:34:56:78:9A:BC")
        submit(activity)
        val result = shadowOf(activity).resultIntent
        assertThat(result.getStringExtra("MAC")).isEqualTo("12:34:56:78:9A:BC")
        assertThat(result.hasExtra("NAME")).isFalse()
        assertThat(result.hasExtra("PROTOCOL_ID")).isFalse()
        assertThat(config.passwordForWheel).isEmpty()
        assertThat(config.lastMac).isEqualTo("12:34:56:78:9A:BC")
    }

    @Test fun `discovery click retains order raw name and the activity result contract`() {
        val activity = launch()
        config.advDataForWheel = "previous-wheel-payload"
        config.passwordForWheel = "previous-wheel-password"
        val first = device(name = null)
        val second = device("AA:BB:CC:DD:EE:00", "Other")
        completeScan(listOf(first, second))
        assertThat(state(activity).devices.map { it.address }).containsExactly(first.address, second.address).inOrder()
        select(activity, second.address)
        val result = shadowOf(activity).resultIntent
        assertThat(result.getStringExtra("MAC")).isEqualTo(second.address)
        assertThat(result.getStringExtra("NAME")).isEqualTo("Other")
        assertThat(result.hasExtra("PROTOCOL_ID")).isFalse()
        assertThat(config.advDataForWheel).isEmpty()
        assertThat(config.passwordForWheel).isEmpty()
        assertThat(config.lastMac).isEqualTo(second.address)
        verify(exactly = 0) { session.connect(any()) }
    }

    @Test fun `protocol picker sends exact class ID and cancel does not select a device`() {
        val protocol = mockk<EUCProtocol>(relaxed = true)
        every { protocol.manufacturer } returns "Custom"
        every { session.getAvailableProtocols() } returns listOf(protocol)
        val activity = launch()
        config.advDataForWheel = "previous-wheel-payload"
        config.passwordForWheel = "previous-wheel-password"
        completeScan(listOf(device()))
        forceProtocol(activity, state(activity).devices.first().address)
        var picker = ShadowDialog.getLatestDialog() as AlertDialog
        picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertThat(activity.isFinishing).isFalse()
        forceProtocol(activity, state(activity).devices.first().address)
        picker = ShadowDialog.getLatestDialog() as AlertDialog
        picker.listView.performItemClick(null, 1, 1)
        assertThat(shadowOf(activity).resultIntent.getStringExtra("PROTOCOL_ID"))
            .isEqualTo(protocol.javaClass.simpleName)
        assertThat(shadowOf(activity).resultIntent.getStringExtra("MAC")).isEqualTo("11:22:33:44:55:66")
        assertThat(shadowOf(activity).resultIntent.getStringExtra("NAME")).isEqualTo("Wheel")
        assertThat(config.advDataForWheel).isEmpty()
        assertThat(config.passwordForWheel).isEmpty()
        verify(exactly = 0) { session.forceProtocol(any()) }
    }

    @Test fun `auto protocol choice leaves protocol extra absent`() {
        val activity = launch()
        completeScan(listOf(device()))
        forceProtocol(activity, state(activity).devices.first().address)
        (ShadowDialog.getLatestDialog() as AlertDialog).listView.performItemClick(null, 0, 0)
        assertThat(shadowOf(activity).resultIntent.hasExtra("PROTOCOL_ID")).isFalse()
        assertThat(config.advDataForWheel).isEmpty()
    }

    @Test fun `pause and Back stop scan remove timeout and never return success`() {
        val activity = launch()
        controller!!.pause()
        clearMocks(session, answers = false, recordedCalls = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        verify(exactly = 0) { session.startScan() }
        verify(exactly = 0) { session.stopScan() }
        controller!!.resume()
        idle()
        activity.onBackPressedDispatcher.onBackPressed()
        assertThat(activity.isFinishing).isTrue()
        assertThat(shadowOf(activity).resultCode).isEqualTo(ScanActivity.RESULT_CANCELED)
        verify(exactly = 1) { session.startScan() }
    }

    @Test fun `only nonempty matching permission grants may start a scan`() {
        val activity = launch()
        completeScan()
        clearMocks(session, answers = false, recordedCalls = true)
        activity.onRequestPermissionsResult(99, emptyArray(), intArrayOf())
        activity.onRequestPermissionsResult(1, emptyArray(), intArrayOf())
        verify(exactly = 0) { session.startScan() }
        activity.onRequestPermissionsResult(1, arrayOf(Manifest.permission.BLUETOOTH),
            intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED))
        activity.onRequestPermissionsResult(1, arrayOf(Manifest.permission.BLUETOOTH),
            intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED))
        verify(exactly = 1) { session.startScan() }
    }

    @Test fun `MAC edit and error survive activity recreation`() {
        val activity = launch()
        completeScan()
        edit(activity, "draft")
        submit(activity)
        val saved = Bundle()
        controller!!.saveInstanceState(saved).pause().stop().destroy()
        controller = null
        val recreated = launch(saved)
        completeScan()
        assertThat(state(recreated).manualAddress).isEqualTo("draft")
        assertThat(state(recreated).invalidAddress).isTrue()
    }

    @Test fun `saved false is ignored and preference writes cannot switch renderer or restart scanning`() {
        val activity = launch()
        completeScan(listOf(device()))
        val root = dialog(activity)
        clearMocks(session, answers = false, recordedCalls = true)
        config.setValue("use_compose_scan", true)
        idle()
        assertThat(root.findViewById<View>(R.id.scan_compose_view)!!.visibility).isEqualTo(View.VISIBLE)
        config.setValue("use_compose_scan", false)
        idle()
        val compose = root.findViewById<ComposeView>(R.id.scan_compose_view)!!
        assertThat(compose.visibility).isEqualTo(View.VISIBLE)
        assertThat(compose.hasComposition).isTrue()
        verify(exactly = 0) { session.startScan() }
        verify(exactly = 0) { session.stopScan() }
        controller!!.pause()
        assertThat(compose.hasComposition).isFalse()
        controller!!.resume()
        idle()
        assertThat(compose.hasComposition).isTrue()
        clearMocks(session, answers = false, recordedCalls = true)
        verify(exactly = 0) { session.startScan() }
        verify(exactly = 0) { session.stopScan() }
    }

    @Test fun `Compose callback uses the address based result action`() {
        val activity = launch()
        completeScan(listOf(device(name = "Compose wheel")))
        select(activity, "11:22:33:44:55:66")
        val result = shadowOf(activity).resultIntent
        assertThat(result.getStringExtra("MAC")).isEqualTo("11:22:33:44:55:66")
        assertThat(result.getStringExtra("NAME")).isEqualTo("Compose wheel")
        verify(exactly = 0) { session.connect(any()) }
    }

    @Test fun `scan refusal restores idle header and manual field without waiting timeout`() {
        val activity = launch()
        flow.value = flow.value.copy(lastError = "Failed to start scan", isScanning = false)
        idle()
        assertThat(state(activity).scanning).isFalse()
        clearMocks(session, answers = false, recordedCalls = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        verify(exactly = 0) { session.stopScan() }
    }

    @Test
    @Config(sdk = [31])
    fun `Android 12 uses nearby device permissions and does not scan while paused`() {
        shadowOf(application).grantPermissions(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT)
        val activity = launch()
        verify(exactly = 1) { session.startScan() }
        controller!!.pause()
        clearMocks(session, answers = false, recordedCalls = true)
        activity.onRequestPermissionsResult(1, arrayOf(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT), intArrayOf(0, 0))
        verify(exactly = 0) { session.startScan() }
    }
}
