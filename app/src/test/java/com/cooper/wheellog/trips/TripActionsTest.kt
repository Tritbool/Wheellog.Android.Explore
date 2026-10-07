package com.cooper.wheellog.trips

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.cooper.wheellog.R
import com.cooper.wheellog.data.TripItemState
import com.cooper.wheellog.views.TripActions
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TripActionsTest {
    private val item = TripItemState(Uri.parse("content://media/external/downloads/42"), "ride.csv", null, "ride", "", "")

    @Test fun `sharing launches CSV chooser with stream clip and read grants`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            activity.startActivity(TripActions.shareIntent(activity, item))
            val chooser = shadowOf(activity).nextStartedActivity
            assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
            assertThat(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
            assertThat(send.type).isEqualTo("text/csv")
            assertThat(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).isEqualTo(item.uri)
            assertThat(send.clipData!!.getItemAt(0).uri).isEqualTo(item.uri)
            assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun `delete confirmation includes exact filename cancel is inert and OK uses captured stable item`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.OriginalTheme)
        val deleted = mutableListOf<TripItemState>()
        try {
            val cancel = TripActions.confirmDelete(activity, item, deleted::add)
            assertThat(cancel.findViewById<android.widget.TextView>(android.R.id.message)!!.text.toString())
                .isEqualTo(activity.getString(R.string.trip_menu_delete_file_confirmation) + " ride.csv")
            cancel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertThat(deleted).isEmpty()
            val confirm = TripActions.confirmDelete(activity, item, deleted::add)
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertThat(deleted).containsExactly(item)
        } finally { controller.pause().stop().destroy() }
    }
}
