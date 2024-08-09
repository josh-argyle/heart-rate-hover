package com.example.bubble_hrm

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.example.bubble_hrm.ui.theme.Bubble_hrmTheme
import java.util.UUID
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.provider.Settings



class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false
    private lateinit var heartRateTextView: TextView
    private var overlayPermissionRequested = false;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_view)
        heartRateTextView = findViewById(R.id.textView_heartRate)
        initializeBluetooth()
        checkAndRequestPermissions()
        checkOverlayPermission()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        sendBroadcast(Intent(ACTION_CLOSE_APP))
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 101
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1
        const val ACTION_CLOSE_APP = "com.example.bubble_hrm.CLOSE_APP"
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN))
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (requiredPermissions.isNotEmpty()) {
            requestPermissions(requiredPermissions.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            startScan()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!scanning) {
            scanning = true
            val scanner = bluetoothAdapter.bluetoothLeScanner
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("0000180D-0000-1000-8000-00805f9b34fb"))
                .build()

            scanner.startScan(listOf(filter), settings, scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (scanning) {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            scanner.stopScan(scanCallback)
            scanning = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                runOnUiThread {
                    heartRateTextView.text = "Device found: ${device.address}"
                }
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    bluetoothGatt = device.connectGatt(this@MainActivity, false, gattCallback)
                    stopScan()
                }
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            // Handle the case where permission is denied
            // Maybe inform the user or disable functionality
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val heartRateService = gatt.getService(UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb"))
            val heartRateCharacteristic = heartRateService?.getCharacteristic(UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb"))
            gatt.setCharacteristicNotification(heartRateCharacteristic, true)
            val descriptor = heartRateCharacteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")) {
                val heartRate = extractHeartRate(characteristic.value)
                runOnUiThread {
                    val str = "${heartRate}bpm"
                    heartRateTextView.text = str
                    updateNotificationService(str)
                }
            }
        }

    }

//    private fun updateNotificationService(heartRate: String) {
//        val intent = Intent(this, HeartRateService::class.java)
//        intent.putExtra("heartRate", heartRate)
//
//        Log.d("MainActivity", "Updating notification service with heart rate: $heartRate")
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(intent) // Use startForegroundService for Android O and above
//            Log.d("MainActivity", "Starting foreground service")
//        } else {
//            startService(intent)
//            Log.d("MainActivity", "Starting service for older Android versions")
//        }
//    }

    private fun updateNotificationService(heartRate: String) {
        val bubbleIntent = Intent(this, FloatingBubbleService::class.java)
        val notificationIntent = Intent(this, HeartRateService::class.java)
        bubbleIntent.putExtra("heartRate", heartRate)
        notificationIntent.putExtra("heartRate", heartRate)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(bubbleIntent)
                startForegroundService(notificationIntent)
            } else {
                startService(bubbleIntent)
                startService(notificationIntent)
            }
        } else {
            checkOverlayPermission()
        }
    }


    private fun extractHeartRate(data: ByteArray): Int {
        var heartRate = data[1].toInt()
        if ((data[0].toInt() and 0x01) != 0) {
            heartRate = (heartRate shl 8) + data[2].toInt()
        }
        return heartRate
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (!overlayPermissionRequested) {
                overlayPermissionRequested = true
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
        } else {
            startFloatingBubbleService()
        }
    }

    private fun startFloatingBubbleService() {
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            overlayPermissionRequested = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startFloatingBubbleService()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
