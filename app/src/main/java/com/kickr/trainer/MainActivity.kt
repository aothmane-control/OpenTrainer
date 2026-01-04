package com.kickr.trainer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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
import com.kickr.trainer.model.Workout
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var bluetoothService: KickrBluetoothService
    private lateinit var deviceAdapter: DeviceAdapter
    
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var setupWorkoutButton: Button
    private lateinit var stopWorkoutButton: Button
    private lateinit var connectionStatusTextView: TextView
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var dataScrollView: NestedScrollView
    private lateinit var powerTextView: TextView
    private lateinit var cadenceTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var heartRateTextView: TextView
    private lateinit var powerChart: LineChart
    private lateinit var speedChart: LineChart
    private lateinit var workoutStatusCard: MaterialCardView
    private lateinit var workoutChartCard: MaterialCardView
    private lateinit var workoutIntervalTextView: TextView
    private lateinit var workoutResistanceTextView: TextView
    private lateinit var workoutTimeTextView: TextView
    private lateinit var workoutProfileChart: LineChart
    
    private val powerDataPoints = mutableListOf<Entry>()
    private val speedDataPoints = mutableListOf<Entry>()
    private var startTime: Long = 0
    private val maxDataPoints = 200 // 20 seconds at ~10Hz
    
    private var activeWorkout: Workout? = null
    private var workoutTimer: CountDownTimer? = null
    private var workoutElapsedSeconds = 0

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
    
    private val workoutSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val durationMinutes = data.getIntExtra("durationMinutes", 0)
                val durations = data.getIntArrayExtra("durations") ?: IntArray(0)
                val resistances = data.getIntArrayExtra("resistances") ?: IntArray(0)
                
                if (durations.isNotEmpty() && resistances.isNotEmpty()) {
                    val intervals = durations.zip(resistances).map { (duration, resistance) ->
                        com.kickr.trainer.model.WorkoutInterval(duration, resistance)
                    }
                    activeWorkout = Workout(durationMinutes * 60, intervals)
                    startWorkout()
                }
            }
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
        setupWorkoutButton = findViewById(R.id.setupWorkoutButton)
        stopWorkoutButton = findViewById(R.id.stopWorkoutButton)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        dataScrollView = findViewById(R.id.dataScrollView)
        powerTextView = findViewById(R.id.powerTextView)
        cadenceTextView = findViewById(R.id.cadenceTextView)
        speedTextView = findViewById(R.id.speedTextView)
        heartRateTextView = findViewById(R.id.heartRateTextView)
        powerChart = findViewById(R.id.powerChart)
        speedChart = findViewById(R.id.speedChart)
        workoutStatusCard = findViewById(R.id.workoutStatusCard)
        workoutChartCard = findViewById(R.id.workoutChartCard)
        workoutIntervalTextView = findViewById(R.id.workoutIntervalTextView)
        workoutResistanceTextView = findViewById(R.id.workoutResistanceTextView)
        workoutTimeTextView = findViewById(R.id.workoutTimeTextView)
        workoutProfileChart = findViewById(R.id.workoutProfileChart)
        
        setupCharts()
        setupWorkoutProfileChart()
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
    
    private fun setupWorkoutProfileChart() {
        workoutProfileChart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = true
            legend.textColor = Color.DKGRAY
            axisRight.isEnabled = false
            axisLeft.apply {
                textColor = Color.DKGRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 10f
            }
            xAxis.apply {
                textColor = Color.DKGRAY
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                granularity = 30f
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val mins = (value / 60).toInt()
                        val secs = (value % 60).toInt()
                        return "%d:%02d".format(mins, secs)
                    }
                }
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
                        setupWorkoutButton.visibility = View.VISIBLE
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
        
        setupWorkoutButton.setOnClickListener {
            val intent = Intent(this, WorkoutSetupActivity::class.java)
            workoutSetupLauncher.launch(intent)
        }
        
        stopWorkoutButton.setOnClickListener {
            stopWorkout()
        }
    }
    
    private fun startWorkout() {
        val workout = activeWorkout ?: return
        
        Log.d(TAG, "Starting workout: ${workout.intervals.size} intervals, ${workout.totalDuration}s total")
        
        workoutElapsedSeconds = 0
        workoutStatusCard.visibility = View.VISIBLE
        workoutChartCard.visibility = View.VISIBLE
        setupWorkoutButton.visibility = View.GONE
        stopWorkoutButton.visibility = View.VISIBLE
        
        // Build workout profile chart
        buildWorkoutProfileChart(workout)
        
        // Initialize UI with first interval
        val firstInterval = workout.intervals.firstOrNull()
        if (firstInterval != null) {
            workoutIntervalTextView.text = getString(R.string.workout_interval_format, 1, workout.intervals.size)
            workoutResistanceTextView.text = getString(R.string.workout_resistance_format, firstInterval.resistance)
            val mins = workout.totalDuration / 60
            val secs = workout.totalDuration % 60
            workoutTimeTextView.text = getString(R.string.workout_time_format, mins, secs)
            
            // Set initial resistance
            bluetoothService.setResistance(firstInterval.resistance)
            Log.d(TAG, "Set initial resistance: ${firstInterval.resistance}%")
        }
        
        // Start timer that ticks every second
        workoutTimer = object : CountDownTimer(workout.totalDuration * 1000L, 1000) {
            private var lastResistance = -1
            
            override fun onTick(millisUntilFinished: Long) {
                workoutElapsedSeconds++
                Log.d(TAG, "Workout tick: ${workoutElapsedSeconds}s elapsed")
                
                val currentInterval = workout.getCurrentInterval(workoutElapsedSeconds)
                if (currentInterval != null) {
                    val intervalIndex = workout.intervals.indexOf(currentInterval) + 1
                    
                    // Update UI
                    workoutIntervalTextView.text = getString(
                        R.string.workout_interval_format,
                        intervalIndex,
                        workout.intervals.size
                    )
                    workoutResistanceTextView.text = getString(
                        R.string.workout_resistance_format,
                        currentInterval.resistance
                    )
                    
                    val remaining = workout.getRemainingTime(workoutElapsedSeconds)
                    val mins = remaining / 60
                    val secs = remaining % 60
                    workoutTimeTextView.text = getString(R.string.workout_time_format, mins, secs)
                    
                    // Update workout profile chart with progress line
                    updateWorkoutProfileProgress(workout, workoutElapsedSeconds)
                    
                    // Send resistance command when interval changes
                    if (currentInterval.resistance != lastResistance) {
                        bluetoothService.setResistance(currentInterval.resistance)
                        lastResistance = currentInterval.resistance
                        Log.d(TAG, "Resistance changed to: ${currentInterval.resistance}% at ${workoutElapsedSeconds}s")
                    }
                }
            }
            
            override fun onFinish() {
                Log.d(TAG, "Workout finished")
                Toast.makeText(this@MainActivity, R.string.workout_complete, Toast.LENGTH_SHORT).show()
                stopWorkout()
            }
        }.start()
        
        Log.d(TAG, "Workout timer started")
    }
    
    private fun buildWorkoutProfileChart(workout: Workout) {
        val entries = mutableListOf<Entry>()
        var accumulatedTime = 0f
        
        // Create the resistance profile with step function
        for (interval in workout.intervals) {
            entries.add(Entry(accumulatedTime, interval.resistance.toFloat()))
            accumulatedTime += interval.duration
            entries.add(Entry(accumulatedTime, interval.resistance.toFloat()))
        }
        
        val dataSet = LineDataSet(entries, "Target Resistance (%)").apply {
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
            lineWidth = 3f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_light)
            fillAlpha = 50
        }
        
        workoutProfileChart.data = LineData(dataSet)
        workoutProfileChart.xAxis.axisMaximum = workout.totalDuration.toFloat()
        workoutProfileChart.notifyDataSetChanged()
        workoutProfileChart.invalidate()
    }
    
    private fun updateWorkoutProfileProgress(workout: Workout, elapsedSeconds: Int) {
        val entries = mutableListOf<Entry>()
        var accumulatedTime = 0f
        
        // Rebuild the resistance profile
        for (interval in workout.intervals) {
            entries.add(Entry(accumulatedTime, interval.resistance.toFloat()))
            accumulatedTime += interval.duration
            entries.add(Entry(accumulatedTime, interval.resistance.toFloat()))
        }
        
        val profileDataSet = LineDataSet(entries, "Target Resistance (%)").apply {
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
            lineWidth = 3f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_light)
            fillAlpha = 50
        }
        
        // Add progress indicator line
        val progressEntries = listOf(
            Entry(elapsedSeconds.toFloat(), 0f),
            Entry(elapsedSeconds.toFloat(), 100f)
        )
        
        val progressDataSet = LineDataSet(progressEntries, "Current Position").apply {
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
            lineWidth = 3f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
            enableDashedLine(10f, 5f, 0f)
        }
        
        workoutProfileChart.data = LineData(profileDataSet, progressDataSet)
        workoutProfileChart.notifyDataSetChanged()
        workoutProfileChart.invalidate()
    }
    
    private fun stopWorkout() {
        Log.d(TAG, "Stopping workout")
        workoutTimer?.cancel()
        workoutTimer = null
        activeWorkout = null
        workoutElapsedSeconds = 0
        
        workoutStatusCard.visibility = View.GONE
        workoutChartCard.visibility = View.GONE
        setupWorkoutButton.visibility = View.VISIBLE
        stopWorkoutButton.visibility = View.GONE
        
        // Reset resistance to 0
        bluetoothService.setResistance(0)
        Log.d(TAG, "Resistance reset to 0%")
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
        stopWorkout()
        bluetoothService.stopScan()
        bluetoothService.disconnect()
    }
}
