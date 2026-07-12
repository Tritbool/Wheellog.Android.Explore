package com.cooper.wheellog

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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.cooper.wheellog.ble.BleSessionViewModel
import com.cooper.wheellog.databinding.ActivityScanBinding
import com.cooper.wheellog.utils.PermissionsUtil
import com.cooper.wheellog.utils.StringUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber


class ScanActivity: AppCompatActivity() {
    private val appConfig: AppConfig by inject()
    private val viewModel: BleSessionViewModel by viewModel()
    private var mDeviceListAdapter: DeviceListAdapter? = null
    private var pb: ProgressBar? = null
    private var scanTitle: TextView? = null
    // Stops scanning after 10 seconds.
    private val scanPeriodHandler = Handler(Looper.getMainLooper())
    private val scanPeriod: Long = 10_000
    private lateinit var alertDialog: AlertDialog
    private lateinit var macLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityScanBinding.inflate(layoutInflater, null, false)
        pb = binding.scanProgress
        scanTitle = binding.scanTitle
        mDeviceListAdapter = DeviceListAdapter(this)
        binding.list.onItemClickListener = onItemClickListener
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
                            !keyEvent.isCanceled) {
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
            viewModel.sessionState.collectLatest { state ->
                mDeviceListAdapter?.setDevices(state.scanResults)
                mDeviceListAdapter?.notifyDataSetChanged()
            }
        }

        // Safety-net: handle back via the Activity dispatcher in case the dialog key listener
        // is not reached (e.g. hardware back on some launchers).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.sessionState.value.isScanning) {
                    scanLeDevice(false)
                }
                close()
            }
        })
    }

    private fun close () {
        if (viewModel.sessionState.value.isScanning) {
            @SuppressLint("MissingPermission")
            val ignored = runCatching { viewModel.stopScan() }
        }
        alertDialog.dismiss()
        finish()
    }

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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all {r -> r == PackageManager.PERMISSION_GRANTED}) {
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

    @SuppressLint("MissingPermission")
    private fun scanLeDevice(enable: Boolean) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            scanPeriodHandler.postDelayed({ scanLeDevice(false) }, scanPeriod)
            viewModel.stopScan()
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