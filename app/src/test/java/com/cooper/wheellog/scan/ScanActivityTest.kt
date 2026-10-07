package com.cooper.wheellog.scan

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.R
import com.cooper.wheellog.ScanActivity
import com.cooper.wheellog.ble.BleSessionState
import com.cooper.wheellog.ble.BleSessionViewModel
import com.google.android.material.textfield.TextInputLayout
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
        config.useComposeScan = false
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
    private fun TextInputLayout.submit() =
        findViewById<View>(com.google.android.material.R.id.text_input_end_icon).performClick()
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
        val root = dialog(activity)
        verify(exactly = 1) { session.startScan() }
        assertThat(root.findViewById<TextInputLayout>(R.id.manual_address)!!.visibility).isEqualTo(View.GONE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertThat(root.findViewById<TextInputLayout>(R.id.manual_address)!!.visibility).isEqualTo(View.VISIBLE)
        verify(exactly = 1) { session.stopScan() }
        assertThat(activity.isFinishing).isFalse()
    }

    @Test fun `manual validation keeps dialog then returns only MAC and clears password`() {
        val activity = launch()
        completeScan()
        val input = dialog(activity).findViewById<TextInputLayout>(R.id.manual_address)!!
        input.editText!!.setText("bad")
        input.submit()
        assertThat(input.error.toString()).isEqualTo("incorrect MAC")
        assertThat(activity.isFinishing).isFalse()
        config.passwordForWheel = "old"
        input.editText!!.setText("12:34:56:78:9A:BC")
        input.submit()
        val result = shadowOf(activity).resultIntent
        assertThat(result.getStringExtra("MAC")).isEqualTo("12:34:56:78:9A:BC")
        assertThat(result.hasExtra("NAME")).isFalse()
        assertThat(result.hasExtra("PROTOCOL_ID")).isFalse()
        assertThat(config.passwordForWheel).isEmpty()
        assertThat(config.lastMac).isEqualTo("12:34:56:78:9A:BC")
    }

    @Test fun `discovery click retains order raw name and the activity result contract`() {
        val activity = launch()
        val first = device(name = null)
        val second = device("AA:BB:CC:DD:EE:00", "Other")
        completeScan(listOf(first, second))
        val list = dialog(activity).findViewById<ListView>(android.R.id.list)!!
        assertThat(list.adapter.count).isEqualTo(2)
        list.performItemClick(null, 1, 1)
        val result = shadowOf(activity).resultIntent
        assertThat(result.getStringExtra("MAC")).isEqualTo(second.address)
        assertThat(result.getStringExtra("NAME")).isEqualTo("Other")
        assertThat(result.hasExtra("PROTOCOL_ID")).isFalse()
        assertThat(config.advDataForWheel).isEmpty()
        verify(exactly = 0) { session.connect(any()) }
    }

    @Test fun `protocol picker sends exact class ID and cancel does not select a device`() {
        val protocol = mockk<EUCProtocol>(relaxed = true)
        every { protocol.manufacturer } returns "Custom"
        every { session.getAvailableProtocols() } returns listOf(protocol)
        val activity = launch()
        completeScan(listOf(device()))
        val list = dialog(activity).findViewById<ListView>(android.R.id.list)!!
        list.onItemLongClickListener.onItemLongClick(list, null, 0, 0)
        var picker = ShadowDialog.getLatestDialog() as AlertDialog
        picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertThat(activity.isFinishing).isFalse()
        list.onItemLongClickListener.onItemLongClick(list, null, 0, 0)
        picker = ShadowDialog.getLatestDialog() as AlertDialog
        picker.listView.performItemClick(null, 1, 1)
        assertThat(shadowOf(activity).resultIntent.getStringExtra("PROTOCOL_ID"))
            .isEqualTo(protocol.javaClass.simpleName)
        verify(exactly = 0) { session.forceProtocol(any()) }
    }

    @Test fun `auto protocol choice leaves protocol extra absent`() {
        val activity = launch()
        completeScan(listOf(device()))
        val list = dialog(activity).findViewById<ListView>(android.R.id.list)!!
        list.onItemLongClickListener.onItemLongClick(list, null, 0, 0)
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
        val input = dialog(activity).findViewById<TextInputLayout>(R.id.manual_address)!!
        input.editText!!.setText("draft")
        input.submit()
        val saved = Bundle()
        controller!!.saveInstanceState(saved).pause().stop().destroy()
        controller = null
        val recreated = launch(saved)
        completeScan()
        val restored = dialog(recreated).findViewById<TextInputLayout>(R.id.manual_address)!!
        assertThat(restored.editText!!.text.toString()).isEqualTo("draft")
        assertThat(restored.error.toString()).isEqualTo("incorrect MAC")
    }

    @Test fun `fallback is independent persisted and does not restart scanning on a switch`() {
        val activity = launch()
        completeScan(listOf(device()))
        val root = dialog(activity)
        clearMocks(session, answers = false, recordedCalls = true)
        config.useComposeScan = true
        idle()
        assertThat(root.findViewById<View>(R.id.scan_compose_view)!!.visibility).isEqualTo(View.VISIBLE)
        assertThat(root.findViewById<View>(R.id.scan_views_content)!!.visibility).isEqualTo(View.GONE)
        assertThat(AppConfig(application).useComposeScan).isTrue()
        config.useComposeScan = false
        idle()
        assertThat(root.findViewById<View>(R.id.scan_views_content)!!.visibility).isEqualTo(View.VISIBLE)
        assertThat(config.useComposeTelemetry && config.useComposeUI && config.useComposeBms).isTrue()
        verify(exactly = 0) { session.startScan() }
        verify(exactly = 0) { session.stopScan() }
    }

    @Test fun `Compose callback uses the same address based result action as Views`() {
        config.useComposeScan = true
        val activity = launch()
        completeScan(listOf(device(name = "Compose wheel")))
        activity.javaClass.getDeclaredMethod("selectDevice", String::class.java)
            .apply { isAccessible = true }.invoke(activity, "11:22:33:44:55:66")
        val result = shadowOf(activity).resultIntent
        assertThat(result.getStringExtra("MAC")).isEqualTo("11:22:33:44:55:66")
        assertThat(result.getStringExtra("NAME")).isEqualTo("Compose wheel")
        verify(exactly = 0) { session.connect(any()) }
    }

    @Test fun `scan refusal restores idle header and manual field without waiting timeout`() {
        val activity = launch()
        flow.value = flow.value.copy(lastError = "Failed to start scan", isScanning = false)
        idle()
        val root = dialog(activity)
        assertThat(root.findViewById<View>(R.id.scanProgress)!!.visibility).isEqualTo(View.GONE)
        assertThat(root.findViewById<TextInputLayout>(R.id.manual_address)!!.visibility).isEqualTo(View.VISIBLE)
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
