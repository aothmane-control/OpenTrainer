package com.kickr.trainer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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
    private lateinit var dataScrollView: NestedScrollView
    private lateinit var powerTextView: TextView
    private lateinit var cadenceTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var heartRateTextView: TextView
    private lateinit var powerChart: LineChart
    private lateinit var speedChart: LineChart
    
    private val powerDataPoints = mutableListOf<Entry>()
    private val speedDataPoints = mutableListOf<Entry>()
    private var startTime: Long = 0
    private val maxDataPoints = 200 // 20 seconds at ~10Hz

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
        dataScrollView = findViewById(R.id.dataScrollView)
        powerTextView = findViewById(R.id.powerTextView)
        cadenceTextView = findViewById(R.id.cadenceTextView)
        speedTextView = findViewById(R.id.speedTextView)
        heartRateTextView = findViewById(R.id.heartRateTextView)
        powerChart = findViewById(R.id.powerChart)
        speedChart = findViewById(R.id.speedChart)
        
        setupCharts()
    }
    
    private fun setupCharts() {
        // Configure Power Chart
        powerChart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.apply {
                textColor = Color.DKGRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
            }
            xAxis.apply {
                textColor = Color.DKGRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                granularity = 1f
            }
        }
        
        // Configure Speed Chart
        speedChart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.apply {
                textColor = Color.DKGRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
            }
            xAxis.apply {
                textColor = Color.DKGRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                granularity = 1f
            }
        }
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
                        dataScrollView.visibility = View.GONE
                    }
                    is KickrBluetoothService.ConnectionState.Connecting -> {
                        connectionStatusTextView.text = getString(R.string.connecting)
                        connectionStatusTextView.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
                        )
                        devicesRecyclerView.visibility = View.GONE
                        dataScrollView.visibility = View.GONE
                    }
                    is KickrBluetoothService.ConnectionState.Connected -> {
                        connectionStatusTextView.text = getString(R.string.connected)
                        connectionStatusTextView.setTextColor(
                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark)
                        )
                        scanButton.visibility = View.GONE
                        disconnectButton.visibility = View.VISIBLE
                        devicesRecyclerView.visibility = View.GONE
                        dataScrollView.visibility = View.VISIBLE
                        startTime = System.currentTimeMillis()
                        powerDataPoints.clear()
                        speedDataPoints.clear()
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
                
                // Update charts
                updateCharts(data.power, data.speed)
            }
        }
    }
    
    private fun updateCharts(power: Int, speed: Float) {
        val currentTime = (System.currentTimeMillis() - startTime) / 1000f // seconds
        
        // Add new data points
        powerDataPoints.add(Entry(currentTime, power.toFloat()))
        speedDataPoints.add(Entry(currentTime, speed))
        
        // Remove old data points (keep only last 20 seconds)
        while (powerDataPoints.isNotEmpty() && currentTime - powerDataPoints.first().x > 20f) {
            powerDataPoints.removeAt(0)
        }
        while (speedDataPoints.isNotEmpty() && currentTime - speedDataPoints.first().x > 20f) {
            speedDataPoints.removeAt(0)
        }
        
        // Update power chart
        val powerDataSet = LineDataSet(powerDataPoints, "Power").apply {
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark)
            setCircleColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
            lineWidth = 2f
            circleRadius = 1f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        powerChart.data = LineData(powerDataSet)
        powerChart.notifyDataSetChanged()
        powerChart.invalidate()
        
        // Update speed chart
        val speedDataSet = LineDataSet(speedDataPoints, "Speed").apply {
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark)
            setCircleColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
            lineWidth = 2f
            circleRadius = 1f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        speedChart.data = LineData(speedDataSet)
        speedChart.notifyDataSetChanged()
        speedChart.invalidate()
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
