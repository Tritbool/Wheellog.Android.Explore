package com.cooper.wheellog

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.databinding.ActivityScanBinding
import com.cooper.wheellog.utils.PermissionsUtil
import com.cooper.wheellog.utils.StringUtil
import io.github.tritbool.euc.ble.models.EUCDevice
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber


class ScanActivity : AppCompatActivity() {
    private val appConfig: AppConfig by inject()
    // Shared app-wide singleton (see bleModule); must use `inject()`, not `viewModel()`.
    private val viewModel: BleSessionViewModel by inject()
    private var mDeviceListAdapter: DeviceListAdapter? = null
    private var pb: ProgressBar? = null
    private var scanTitle: TextView? = null

    // Stops scanning after 10 seconds.
    private val scanPeriodHandler = Handler(Looper.getMainLooper())
    private val scanPeriod: Long = 10_000
    private lateinit var alertDialog: AlertDialog
    private lateinit var macLayout: LinearLayout

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityScanBinding.inflate(layoutInflater, null, false)
        pb = binding.scanProgress
        scanTitle = binding.scanTitle
        mDeviceListAdapter = DeviceListAdapter(this)
        binding.list.onItemClickListener = onItemClickListener
        binding.list.onItemLongClickListener = onItemLongClickListener
        binding.list.adapter = mDeviceListAdapter
        macLayout = binding.lastMacText
        binding.lastMacText.editText!!.setText(appConfig.lastMac)
        binding.lastMacText.setEndIconOnClickListener {
            val deviceAddress = binding.lastMacText.editText?.text.toString()
            if (!StringUtil.isCorrectMac(deviceAddress)) {
                binding.lastMacText.error = "incorrect MAC"
                binding.lastMacText.errorIconDrawable = null
                return@setEndIconOnClickListener
            }
            if (viewModel.sessionState.value.isScanning) {
                scanLeDevice(false)
            }
            val intent = Intent()
            intent.putExtra("MAC", deviceAddress)
            appConfig.lastMac = deviceAddress
            setResult(RESULT_OK, intent)
            appConfig.passwordForWheel = ""
            close()
        }
        alertDialog = AlertDialog.Builder(this, R.style.OriginalTheme_Dialog_Alert)
            .setView(binding.root)
            .setCancelable(false)
            .setOnKeyListener { dialogInterface: DialogInterface, keycode: Int, keyEvent: KeyEvent ->
                if (keycode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP &&
                    !keyEvent.isCanceled
                ) {
                    if (viewModel.sessionState.value.isScanning) {
                        scanLeDevice(false)
                    }
                    dialogInterface.cancel()
                    close()
                }
                false
            }
            .create()
        alertDialog.show()
        // Position the dialog at the top of the screen without dimming, so MainActivity remains
        // visible behind the transparent ScanActivity window.
        alertDialog.window?.apply {
            setGravity(Gravity.TOP)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        // Observe scan results from ViewModel
        lifecycleScope.launch {
            viewModel.sessionState.collect { state ->
                mDeviceListAdapter?.setDevices(state.scanResults)
                mDeviceListAdapter?.notifyDataSetChanged()
            }
        }

        // Safety-net: handle back via the Activity dispatcher in case the dialog key listener
        // is not reached (e.g. hardware back on some launchers).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            override fun handleOnBackPressed() {
                if (viewModel.sessionState.value.isScanning) {
                    scanLeDevice(false)
                }
                close()
            }
        })
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun close() {
        if (viewModel.sessionState.value.isScanning) {
            val ignored = runCatching { viewModel.stopScan() }
        }
        alertDialog.dismiss()
        finish()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onResume() {
        super.onResume()
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

    private fun killMe() {
        alertDialog.dismiss()
        finish()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { r -> r == PackageManager.PERMISSION_GRANTED }) {
            scanLeDevice(true)
        }
    }

    @SuppressLint("MissingPermission")
    private val onItemClickListener = OnItemClickListener { _, _, i, _ ->
        if (viewModel.sessionState.value.isScanning) {
            viewModel.stopScan()
        }
        val device = mDeviceListAdapter!!.getDevice(i)
        val deviceAddress = device.address
        val deviceName = device.name
        val advData = mDeviceListAdapter!!.getAdvData(i)
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
    private val onItemLongClickListener = OnItemLongClickListener { _, _, i, _ ->
        if (viewModel.sessionState.value.isScanning) {
            viewModel.stopScan()
        }
        val device = mDeviceListAdapter!!.getDevice(i)
        showProtocolPickerDialog(device)
        true
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    private fun showProtocolPickerDialog(device: EUCDevice) {
        val candidates = viewModel.getAvailableProtocols()
        val deviceLabel = device.name?.takeIf { it.isNotBlank() } ?: device.address
        val title = getString(R.string.protocol_force_for_device, deviceLabel)

        val labels = candidates.map { it.manufacturer }
        val items = (listOf(getString(R.string.protocol_select_auto)) + labels).toTypedArray()

        AlertDialog.Builder(this, R.style.OriginalTheme_Dialog_Alert)
            .setTitle(title)
            .setItems(items) { _, which ->
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
            scanPeriodHandler.postDelayed({ scanLeDevice(false) }, scanPeriod)
            // NE PAS appeler stopScan() ici — startScan() le fait déjà dans BLEManager
            viewModel.startScan()
            pb!!.visibility = View.VISIBLE
            scanTitle!!.setText(R.string.scanning)
            macLayout.visibility = View.GONE
        } else {
            scanPeriodHandler.removeCallbacksAndMessages(null)
            viewModel.stopScan()
            pb!!.visibility = View.GONE
            scanTitle!!.setText(R.string.devices)
            macLayout.visibility = View.VISIBLE
        }
    }
}