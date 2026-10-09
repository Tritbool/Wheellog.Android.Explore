package com.cooper.wheellog

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.databinding.ActivityScanBinding
import com.cooper.wheellog.scan.ScanPresentation
import com.cooper.wheellog.scan.ScanUiState
import com.cooper.wheellog.utils.PermissionsUtil
import com.cooper.wheellog.utils.StringUtil
import io.github.tritbool.euc.ble.models.EUCDevice
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber


class ScanActivity : AppCompatActivity() {
    private val appConfig: AppConfig by inject()
    // Shared app-wide singleton (see bleModule); must use `inject()`, not `viewModel()`.
    private val viewModel: BleSessionViewModel by inject()
    private var renderer: ScanPageRenderer? = null
    private var uiState = ScanUiState()
    private var scanRequested = false
    private var scanStarted = false
    private var closing = false
    private var protocolDialog: AlertDialog? = null

    // Stops scanning after 10 seconds.
    private val scanPeriodHandler = Handler(Looper.getMainLooper())
    private val scanPeriod: Long = 10_000
    private lateinit var alertDialog: AlertDialog
    private val timeout = Runnable { scanLeDevice(false) }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityScanBinding.inflate(layoutInflater, null, false)
        uiState = ScanUiState(
            manualAddress = savedInstanceState?.getString("scanManualAddress") ?: appConfig.lastMac,
            invalidAddress = savedInstanceState?.getBoolean("scanInvalidAddress") ?: false
        )
        binding.root.setViewTreeLifecycleOwner(this)
        binding.root.setViewTreeSavedStateRegistryOwner(this)
        renderer = ScanPageRenderer(binding, uiState,
            ::selectDevice, ::forceProtocol,
            { address -> updateUi(uiState.copy(manualAddress = address)) }, ::selectManualAddress)
        updateUi(uiState)
        alertDialog = AlertDialog.Builder(this, R.style.OriginalTheme_Dialog_Alert)
            .setView(binding.root)
            .setCancelable(true)
            .setOnCancelListener { close() }
            .create()
        alertDialog.setCanceledOnTouchOutside(false)
        alertDialog.show()
        // Position the dialog at the top of the screen without dimming, so MainActivity remains
        // visible behind the transparent ScanActivity window.
        alertDialog.window?.apply {
            setGravity(Gravity.TOP)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // AppCompat cannot detect Compose text editors when configuring the dialog.
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessionState
                    .map { Triple(it.scanResults, it.isScanning, it.lastError) }
                    .distinctUntilChanged()
                    .collect { (devices, scanning, error) ->
                        updateUi(uiState.copy(devices = ScanPresentation.devices(
                            devices, getString(R.string.unknown_device))))
                        if (scanRequested && (scanning || scanStarted || error != null)) {
                            scanStarted = scanStarted || scanning
                            if (!scanning) finishScanUi()
                        }
                    }
            }
        }

        // Dialogs own their Back dispatcher, including system Back gestures.
        alertDialog.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            override fun handleOnBackPressed() {
                close()
            }
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            override fun handleOnBackPressed() {
                close()
            }
        })
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun close() {
        if (closing) return
        closing = true
        stopScanning()
        protocolDialog?.dismiss()
        alertDialog.dismiss()
        finish()
    }

    /**
     * Unconditionally stops the scan. `stopScan()` is idempotent, so this is safe to call even
     * when no scan is running - and calling it unconditionally means a stale `isScanning` flag
     * can never leave the radio scanning in the background.
     */
    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        scanPeriodHandler.removeCallbacksAndMessages(null)
        finishScanUi()
        runCatching { viewModel.stopScan() }
    }

    private fun finishScanUi() {
        scanRequested = false
        scanStarted = false
        scanPeriodHandler.removeCallbacks(timeout)
        updateUi(uiState.copy(scanning = false))
    }

    private fun updateUi(state: ScanUiState) {
        uiState = state
        renderer?.render(state)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onResume() {
        super.onResume()
        renderer?.start(uiState)
        if (closing || protocolDialog?.isShowing == true) return
        updateUi(uiState)
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled == true) {
            if (!PermissionsUtil.checkBlePermissions(this)) {
                if (PermissionsUtil.isMaxBleReq) {
                    killMe()
                }
                return
            }
            scanLeDevice(true)
        } else {
            if (PermissionsUtil.checkBlePermissions(this)) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                ActivityCompat.startActivityForResult(this, enableBtIntent, 2, null)
            } else {
                killMe()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun killMe() {
        close()
    }

    /**
     * Covers every way out of this activity that does not go through [close] (Home, recents,
     * task switching, activity recreation, the system "enable Bluetooth" dialog, ...).
     */
    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        stopScanning()
        renderer?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("scanManualAddress", uiState.manualAddress)
        outState.putBoolean("scanInvalidAddress", uiState.invalidAddress)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        renderer?.dispose()
        renderer = null
        stopScanning()
        protocolDialog?.dismiss()
        if (::alertDialog.isInitialized) alertDialog.dismiss()
        super.onDestroy()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults.all { r -> r == PackageManager.PERMISSION_GRANTED } &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true && !closing
        ) {
            scanLeDevice(true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun selectManualAddress() {
        if (closing) return
        val address = uiState.manualAddress
        if (!StringUtil.isCorrectMac(address)) {
            updateUi(uiState.copy(invalidAddress = true))
            return
        }
        val intent = Intent().putExtra("MAC", address)
        appConfig.lastMac = address
        appConfig.passwordForWheel = ""
        setResult(RESULT_OK, intent)
        close()
    }

    @SuppressLint("MissingPermission")
    private fun selectDevice(address: String) {
        if (closing) return
        val item = uiState.devices.firstOrNull { it.address == address } ?: return
        stopScanning()
        val device = item.device
        val deviceAddress = device.address
        val deviceName = device.name
        // Raw advertising payloads are not collected by this scan flow.
        val advData = ""
        Timber.i("Device selected MAC = %s", deviceAddress)
        Timber.i("Device selected Name = %s", deviceName)
        Timber.i("Device selected Data = %s", advData)
        val intent = Intent()
        intent.putExtra("MAC", deviceAddress)
        intent.putExtra("NAME", deviceName)
        appConfig.lastMac = deviceAddress
        appConfig.advDataForWheel = advData
        setResult(RESULT_OK, intent)
        // Set password for inmotion
        appConfig.passwordForWheel = ""
        close()
    }

    /**
     * Long-press on a discovered device opens a protocol picker so the user can force a specific
     * protocol before connecting, bypassing auto-detection entirely.
     */
    @SuppressLint("MissingPermission")
    private fun forceProtocol(address: String) {
        if (closing) return
        val device = uiState.devices.firstOrNull { it.address == address }?.device ?: return
        stopScanning()
        showProtocolPickerDialog(device)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    private fun showProtocolPickerDialog(device: EUCDevice) {
        val candidates = viewModel.getAvailableProtocols()
        val deviceLabel = device.name?.takeIf { it.isNotBlank() } ?: device.address
        val title = getString(R.string.protocol_force_for_device, deviceLabel)

        val labels = candidates.map { it.manufacturer }
        val items = (listOf(getString(R.string.protocol_select_auto)) + labels).toTypedArray()

        protocolDialog?.dismiss()
        protocolDialog = AlertDialog.Builder(this, R.style.OriginalTheme_Dialog_Alert)
            .setTitle(title)
            .setItems(items) { _, which ->
                if (closing) return@setItems
                val intent = Intent()
                intent.putExtra("MAC", device.address)
                intent.putExtra("NAME", device.name)
                if (which > 0) {
                    // User picked a specific protocol (index 0 = auto, 1+ = candidates)
                    intent.putExtra("PROTOCOL_ID", candidates[which - 1].javaClass.simpleName)
                    Timber.i(
                        "Forcing protocol %s for device %s",
                        candidates[which - 1].javaClass.simpleName,
                        device.address
                    )
                }
                appConfig.lastMac = device.address
                appConfig.advDataForWheel = ""
                appConfig.passwordForWheel = ""
                setResult(RESULT_OK, intent)
                close()
            }
            .setNegativeButton(R.string.protocol_cancel, null)
            .show()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun scanLeDevice(enable: Boolean) {
        if (enable) {
            if (closing || scanRequested || protocolDialog?.isShowing == true) return
            scanRequested = true
            scanStarted = false
            updateUi(uiState.copy(scanning = true))
            scanPeriodHandler.removeCallbacks(timeout)
            scanPeriodHandler.postDelayed(timeout, scanPeriod)
            // NE PAS appeler stopScan() ici — startScan() le fait déjà dans BLEManager
            viewModel.startScan()
        } else {
            stopScanning()
        }
    }
}