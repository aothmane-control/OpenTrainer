package com.kickr.trainer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.kickr.trainer.adapter.DeviceAdapter
import com.kickr.trainer.bluetooth.KickrBluetoothService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothService: KickrBluetoothService
    private lateinit var deviceAdapter: DeviceAdapter
    
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var connectionStatusTextView: TextView
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var dataCardView: MaterialCardView
    private lateinit var powerTextView: TextView
    private lateinit var cadenceTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var heartRateTextView: TextView

    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startBluetoothScan()
        } else {
            Toast.makeText(
                this,
                R.string.permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (bluetoothService.isBluetoothEnabled()) {
            checkPermissionsAndScan()
        } else {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothService = KickrBluetoothService(this)
        
        initViews()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun initViews() {
        scanButton = findViewById(R.id.scanButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        dataCardView = findViewById(R.id.dataCardView)
        powerTextView = findViewById(R.id.powerTextView)
        cadenceTextView = findViewById(R.id.cadenceTextView)
        speedTextView = findViewById(R.id.speedTextView)
        heartRateTextView = findViewById(R.id.heartRateTextView)
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            bluetoothService.connect(device.device)
        }
        
        devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }

    private fun setupObservers() {
        // Observe scanning state
        lifecycleScope.launch {
            bluetoothService.isScanning.collect { isScanning ->
                if (isScanning) {
                    scanButton.text = getString(R.string.stop_scan)
                    connectionStatusTextView.text = getString(R.string.scanning)
                    connectionStatusTextView.setTextColor(
                        ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
                    )
                } else {
                    scanButton.text = getString(R.string.scan_for_devices)
                }
            }
        }

        // Observe discovered devices
        lifecycleScope.launch {
            bluetoothService.discoveredDevices.collect { devices ->
                deviceAdapter.updateDevices(devices)
                devicesRecyclerView.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // Observe connection state
        lifecycleScope.launch {
            bluetoothService.connectionState.collect { state ->
                when (state) {
                    is KickrBluetoothService.ConnectionState.Disconnected -> {
                        connectionStatusTextView.text = getString(R.string.disconnected)
                        connectionStatusTextView.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                        )
                        scanButton.visibility = View.VISIBLE
                        disconnectButton.visibility = View.GONE
                        devicesRecyclerView.visibility = View.GONE
                    }
                    is KickrBluetoothService.ConnectionState.Connecting -> {
                        connectionStatusTextView.text = getString(R.string.connecting)
                        connectionStatusTextView.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
                        )
                        devicesRecyclerView.visibility = View.GONE
                    }
                    is KickrBluetoothService.ConnectionState.Connected -> {
                        connectionStatusTextView.text = getString(R.string.connected)
                        connectionStatusTextView.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark)
                        )
                        scanButton.visibility = View.GONE
                        disconnectButton.visibility = View.VISIBLE
                        devicesRecyclerView.visibility = View.GONE
                    }
                    is KickrBluetoothService.ConnectionState.Error -> {
                        connectionStatusTextView.text = state.message
                        connectionStatusTextView.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                        )
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Observe trainer data
        lifecycleScope.launch {
            bluetoothService.trainerData.collect { data ->
                powerTextView.text = getString(R.string.power_format, data.power)
                cadenceTextView.text = getString(R.string.cadence_format, data.cadence)
                speedTextView.text = getString(R.string.speed_format, data.speed)
                heartRateTextView.text = getString(R.string.heart_rate_format, data.heartRate)
            }
        }
    }

    private fun setupClickListeners() {
        scanButton.setOnClickListener {
            if (bluetoothService.isScanning.value) {
                bluetoothService.stopScan()
            } else {
                checkPermissionsAndScan()
            }
        }

        disconnectButton.setOnClickListener {
            bluetoothService.disconnect()
        }
    }

    private fun checkPermissionsAndScan() {
        // Check if Bluetooth is supported
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        // Check if Bluetooth is enabled
        if (!bluetoothService.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        // Check permissions based on Android version
        val permissionsNeeded = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            // Android 10-11
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestBluetoothPermissions.launch(permissionsNeeded.toTypedArray())
        } else {
            startBluetoothScan()
        }
    }

    private fun startBluetoothScan() {
        bluetoothService.startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.stopScan()
        bluetoothService.disconnect()
    }
}
