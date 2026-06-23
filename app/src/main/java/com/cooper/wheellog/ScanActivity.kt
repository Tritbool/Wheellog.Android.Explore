package com.cooper.wheellog

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.cooper.wheellog.ble.EucBleManager
import com.cooper.wheellog.databinding.ActivityScanBinding
import com.cooper.wheellog.utils.PermissionsUtil
import com.cooper.wheellog.utils.StringUtil
import io.github.tritbool.euc.ble.models.EUCDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import timber.log.Timber


class ScanActivity : AppCompatActivity() {
    private val appConfig: AppConfig by inject()
    private val eucBleManager: EucBleManager by inject()

    private var mDeviceListAdapter: DeviceListAdapter? = null
    private var pb: ProgressBar? = null
    private var scanTitle: TextView? = null
    private val scanPeriodHandler = Handler(Looper.getMainLooper())
    private val scanPeriod: Long = 10_000
    private lateinit var alertDialog: AlertDialog
    private lateinit var macLayout: LinearLayout
    // Job de collecte de discoveredDevices — annulé à la fermeture
    private var scanJob: Job? = null

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
            stopScan()
            val intent = Intent()
            intent.putExtra("MAC", deviceAddress)
            intent.putExtra("NAME", "")
            intent.putExtra("MANUFACTURER_ID", 0)
            intent.putExtra("RSSI", 0)
            appConfig.lastMac = deviceAddress
            setResult(RESULT_OK, intent)
            appConfig.passwordForWheel = ""
            close()
        }
        alertDialog = AlertDialog.Builder(this, R.style.OriginalTheme_Dialog_Alert)
            .setView(binding.root)
            .setCancelable(false)
            .setOnKeyListener { dialogInterface: DialogInterface, keycode: Int, keyEvent: KeyEvent ->
                if (keycode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP && !keyEvent.isCanceled) {
                    stopScan()
                    dialogInterface.cancel()
                    close()
                }
                false
            }
            .create()
        window.attributes = alertDialog.window?.attributes?.apply {
            gravity = Gravity.TOP
            flags = flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
        }
        alertDialog.show()
        if (!isLocationEnabled(this)) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (mDeviceListAdapter == null) return
        if (!PermissionsUtil.checkBlePermissions(this)) {
            if (PermissionsUtil.isMaxBleReq) killMe()
            return
        }
        val btAdapter = (getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            if (PermissionsUtil.checkBlePermissions(this)) {
                ActivityCompat.startActivityForResult(
                    this,
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    2,
                    null
                )
            } else {
                killMe()
            }
            return
        }
        // Collecter les devices découverts par EucBleManager et alimenter l'adapter
        scanJob = eucBleManager.discoveredDevices
            .onEach { devices ->
                runOnUiThread {
                    mDeviceListAdapter!!.clear()
                    devices.forEach { device -> mDeviceListAdapter!!.addEucDevice(device) }
                    mDeviceListAdapter!!.notifyDataSetChanged()
                }
            }
            .launchIn(lifecycleScope)
        startScan()
    }

    override fun onPause() {
        super.onPause()
        scanJob?.cancel()
        scanJob = null
        stopScan()
    }

    private fun killMe() {
        stopScan()
        alertDialog.dismiss()
        finish()
    }

    private fun close() {
        stopScan()
        alertDialog.dismiss()
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { r -> r == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        }
    }

    @Suppress("MissingPermission")
    private fun startScan() {
        scanPeriodHandler.postDelayed({ stopScan() }, scanPeriod)
        eucBleManager.startScan()
        pb!!.visibility = View.VISIBLE
        scanTitle!!.setText(R.string.scanning)
        macLayout.visibility = View.GONE
    }

    @Suppress("MissingPermission")
    private fun stopScan() {
        scanPeriodHandler.removeCallbacksAndMessages(null)
        eucBleManager.stopScan()
        pb?.visibility = View.GONE
        scanTitle?.setText(R.string.devices)
        macLayout.visibility = View.VISIBLE
    }

    private val onItemClickListener = OnItemClickListener { _, _, i, _ ->
        stopScan()
        val device: EUCDevice = mDeviceListAdapter!!.getEucDevice(i)
        Timber.i("Device selected MAC = %s", device.address)
        Timber.i("Device selected Name = %s", device.name)
        val intent = Intent().apply {
            putExtra("MAC", device.address)
            putExtra("NAME", device.name)
            putExtra("MANUFACTURER_ID", device.manufacturerId)
            putExtra("RSSI", device.rssi)
        }
        appConfig.lastMac = device.address
        appConfig.passwordForWheel = ""
        setResult(RESULT_OK, intent)
        close()
    }

    private fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lm = context.getSystemService(LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            val locationMode = try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE)
            } catch (e: SettingNotFoundException) {
                e.printStackTrace()
                Settings.Secure.LOCATION_MODE_OFF
            }
            locationMode != Settings.Secure.LOCATION_MODE_OFF
        }
    }
}
