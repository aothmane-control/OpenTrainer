/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

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
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.kickr.trainer.model.WorkoutInterval
import com.kickr.trainer.model.GpxTrack
import com.kickr.trainer.model.WorkoutDataPoint
import com.kickr.trainer.model.WorkoutHistory
import com.kickr.trainer.utils.WorkoutStorageManager
import kotlinx.coroutines.launch
import android.util.Log
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var bluetoothService: KickrBluetoothService
    private lateinit var deviceAdapter: DeviceAdapter
    
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var setupWorkoutButton: Button
    private lateinit var viewGpxButton: Button
    private lateinit var stopWorkoutButton: Button
    private lateinit var pauseResumeWorkoutButton: Button
    private lateinit var viewHistoryButton: Button
    private lateinit var connectionStatusTextView: TextView
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var dataScrollView: NestedScrollView
    private lateinit var powerTextView: TextView
    private lateinit var cadenceTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var powerChart: LineChart
    private lateinit var speedChart: LineChart
    private lateinit var workoutStatusCard: MaterialCardView
    private lateinit var workoutChartCard: MaterialCardView
    private lateinit var workoutIntervalTextView: TextView
    private lateinit var workoutResistanceTextView: TextView
    private lateinit var workoutTimeTextView: TextView
    private lateinit var viewMapButton: Button
    private lateinit var workoutProfileChart: LineChart
    private lateinit var mapView: MapView
    private lateinit var mapCard: MaterialCardView
    private var currentPositionMarker: Marker? = null
    private var routePolyline: Polyline? = null
    
    private val powerDataPoints = mutableListOf<Entry>()
    private val speedDataPoints = mutableListOf<Entry>()
    private var startTime: Long = 0
    private val maxDataPoints = 200 // 20 seconds at ~10Hz
    
    private var activeWorkout: Workout? = null
    private var workoutTimer: CountDownTimer? = null
    private var workoutElapsedSeconds = 0
    private var cumulativeDistance = 0.0 // For GPX workouts
    private var workoutPaused = false
    private var workoutPausedAtSecond = 0
    
    // Workout tracking
    private var workoutStartTime: Long = 0
    private val workoutDataPoints = mutableListOf<WorkoutDataPoint>()
    private lateinit var workoutStorageManager: WorkoutStorageManager

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
                val durationSeconds = data.getIntExtra("workout_duration", 0)
                val durations = data.getIntArrayExtra("workout_interval_durations") ?: IntArray(0)
                val resistances = data.getIntArrayExtra("workout_interval_resistances") ?: IntArray(0)
                
                Log.d(TAG, "Received workout: duration=${durationSeconds}s, intervals=${durations.size}")
                
                if (durations.isNotEmpty() && resistances.isNotEmpty()) {
                    val intervals = durations.zip(resistances).map { (duration, resistance) ->
                        com.kickr.trainer.model.WorkoutInterval(duration, resistance)
                    }
                    activeWorkout = Workout(durationSeconds, intervals)
                    startWorkout()
                } else {
                    Log.e(TAG, "No intervals received!")
                    Toast.makeText(this@MainActivity, "Failed to create workout", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d(TAG, "Workout setup cancelled")
        }
    }
    
    private val gpxMapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val hasGpxWorkout = data.getBooleanExtra("has_gpx_workout", false)
                if (hasGpxWorkout && GpxWorkoutHolder.currentTrack != null) {
                    startGpxWorkout(GpxWorkoutHolder.currentTrack!!)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize osmdroid configuration BEFORE setContentView
        Configuration.getInstance().apply {
            load(this@MainActivity, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@MainActivity))
            userAgentValue = "OpenTrainer/1.0"
            osmdroidBasePath = getExternalFilesDir(null)
            osmdroidTileCache = File(getExternalFilesDir(null), "osmdroid/tiles")
        }
        
        setContentView(R.layout.activity_main)

        // Setup toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        bluetoothService = KickrBluetoothService(this)
        workoutStorageManager = WorkoutStorageManager(this)
        
        initViews()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun initViews() {
        scanButton = findViewById(R.id.scanButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        setupWorkoutButton = findViewById(R.id.setupWorkoutButton)
        viewGpxButton = findViewById(R.id.viewGpxButton)
        stopWorkoutButton = findViewById(R.id.stopWorkoutButton)
        pauseResumeWorkoutButton = findViewById(R.id.pauseResumeWorkoutButton)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        dataScrollView = findViewById(R.id.dataScrollView)
        powerTextView = findViewById(R.id.powerTextView)
        cadenceTextView = findViewById(R.id.cadenceTextView)
        speedTextView = findViewById(R.id.speedTextView)
        distanceTextView = findViewById(R.id.distanceTextView)
        powerChart = findViewById(R.id.powerChart)
        speedChart = findViewById(R.id.speedChart)
        workoutStatusCard = findViewById(R.id.workoutStatusCard)
        workoutChartCard = findViewById(R.id.workoutChartCard)
        workoutIntervalTextView = findViewById(R.id.workoutIntervalTextView)
        workoutResistanceTextView = findViewById(R.id.workoutResistanceTextView)
        workoutTimeTextView = findViewById(R.id.workoutTimeTextView)
        viewMapButton = findViewById(R.id.viewMapButton)
        workoutProfileChart = findViewById(R.id.workoutProfileChart)
        mapView = findViewById(R.id.mapView)
        mapCard = findViewById(R.id.mapCard)
        
        setupMap()
        
        if (workoutStatusCard == null || workoutChartCard == null) {
            Log.e(TAG, "ERROR: Workout cards not found in layout!")
        } else {
            Log.d(TAG, "Workout views initialized successfully")
        }
        
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
            legend.textSize = 12f
            setExtraOffsets(10f, 10f, 10f, 10f)
            axisRight.isEnabled = false
            axisLeft.apply {
                isEnabled = true
                textColor = Color.BLACK
                textSize = 12f
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 10f
                setDrawLabels(true)
                setLabelCount(6, false)
            }
            xAxis.apply {
                textColor = Color.BLACK
                textSize = 10f
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                granularity = 30f
                setDrawLabels(true)
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
    
    private fun setupMap() {
        Log.d(TAG, "Setting up MapView")
        try {
            mapView.apply {
                Log.d(TAG, "MapView instance: $this")
                
                // Important: Set tile source FIRST
                setTileSource(TileSourceFactory.MAPNIK)
                Log.d(TAG, "Tile source set to MAPNIK")
                
                // Enable controls
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                
                // CRITICAL: These settings are essential for tile loading
                isTilesScaledToDpi = true
                setUseDataConnection(true)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                
                // Set zoom limits
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                
                // Set initial position (Paris) and zoom
                controller.setZoom(5.0)
                controller.setCenter(GeoPoint(48.8566, 2.3522))
                
                Log.d(TAG, "MapView setup complete, visibility will be: ${View.VISIBLE}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up map", e)
            e.printStackTrace()
        }
    }
    
    private fun displayGpxTrackOnMap(track: GpxTrack) {
        Log.d(TAG, "=== Displaying GPX track on map ===")
        Log.d(TAG, "Track: ${track.name}, ${track.points.size} points")
        
        try {
            // Ensure map is visible and laid out
            if (mapView.width == 0 || mapView.height == 0) {
                Log.w(TAG, "MapView not laid out yet, posting display")
                mapView.post {
                    displayGpxTrackOnMap(track)
                }
                return
            }
            
            Log.d(TAG, "MapView dimensions: ${mapView.width}x${mapView.height}")
            
            // Clear existing overlays
            mapView.overlays.clear()
            currentPositionMarker = null
            routePolyline = null
            
            if (track.points.isEmpty()) {
                Log.w(TAG, "No points in GPX track")
                return
            }
            
            // Convert to GeoPoints
            val geoPoints = track.points.map { point ->
                GeoPoint(point.latitude, point.longitude)
            }
            
            if (geoPoints.isEmpty()) {
                Log.w(TAG, "No valid geo points")
                return
            }
            
            Log.d(TAG, "Points range: ${geoPoints.first()} to ${geoPoints.last()}")
            
            // Create and add polyline
            val polyline = Polyline(mapView).apply {
                setPoints(geoPoints)
                outlinePaint.color = Color.BLUE
                outlinePaint.strokeWidth = 10f
                outlinePaint.isAntiAlias = true
            }
            
            mapView.overlays.add(polyline)
            routePolyline = polyline
            
            Log.d(TAG, "Polyline added, ${mapView.overlays.size} overlays total")
            
            // Zoom to show entire route with proper padding
            if (geoPoints.size > 1) {
                val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
                Log.d(TAG, "Zooming to bounds: $bounds")
                
                // Calculate center point
                val centerLat = (bounds.latNorth + bounds.latSouth) / 2
                val centerLon = (bounds.lonEast + bounds.lonWest) / 2
                val center = GeoPoint(centerLat, centerLon)
                
                // Calculate appropriate zoom level to fit the bounds
                val latSpan = Math.abs(bounds.latNorth - bounds.latSouth)
                val lonSpan = Math.abs(bounds.lonEast - bounds.lonWest)
                
                // Estimate zoom level based on span with better scaling
                val maxSpan = maxOf(latSpan, lonSpan) * 1.3 // Add 30% padding
                val zoom = when {
                    maxSpan > 10.0 -> 6.0
                    maxSpan > 5.0 -> 7.5
                    maxSpan > 2.0 -> 9.0
                    maxSpan > 1.0 -> 10.5
                    maxSpan > 0.5 -> 11.5
                    maxSpan > 0.25 -> 12.5
                    maxSpan > 0.125 -> 13.5
                    maxSpan > 0.05 -> 14.5
                    maxSpan > 0.025 -> 15.5
                    else -> 16.0
                }
                
                Log.d(TAG, "Setting center: $center, zoom: $zoom (span: lat=$latSpan, lon=$lonSpan, max=$maxSpan)")
                
                // Set center and zoom
                mapView.controller.setCenter(center)
                mapView.controller.setZoom(zoom)
            } else {
                // Single point - just center on it
                mapView.controller.setCenter(geoPoints.first())
                mapView.controller.setZoom(16.0)
            }
            
            // Force redraw
            mapView.invalidate()
            
            Log.d(TAG, "=== Map display complete ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying GPX track on map", e)
            e.printStackTrace()
            throw e
        }
    }
    
    private fun updateMapPosition(distanceMeters: Double) {
        try {
            val track = activeWorkout?.gpxTrack ?: return
            val position = track.getPointAtDistance(distanceMeters) ?: return
            
            Log.d(TAG, "Updating map position: distance=${distanceMeters}m, lat=${position.latitude}, lon=${position.longitude}")
            
            // Remove old marker
            currentPositionMarker?.let { mapView.overlays.remove(it) }
            
            // Create new marker
            val geoPoint = GeoPoint(position.latitude, position.longitude)
            val marker = Marker(mapView).apply {
                this.position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Current Position"
                icon = resources.getDrawable(android.R.drawable.ic_menu_mylocation, null)
            }
            
            mapView.overlays.add(marker)
            currentPositionMarker = marker
            
            // Center map on current position with zoom for ~500m x 300m view
            // At latitude ~45°, zoom level 16 shows roughly 500m width
            // Adjust based on latitude: higher latitudes need higher zoom
            val latitude = position.latitude
            val zoomLevel = when {
                Math.abs(latitude) > 60 -> 16.5  // Polar regions
                Math.abs(latitude) > 45 -> 16.0  // Mid latitudes
                else -> 15.5  // Equatorial regions
            }
            
            mapView.controller.setZoom(zoomLevel)
            mapView.controller.animateTo(geoPoint)
            
            mapView.invalidate()
            
            Log.d(TAG, "Map position updated successfully with zoom $zoomLevel")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating map position", e)
            // Don't crash, just skip map update
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
                        setupWorkoutButton.visibility = View.GONE
                        viewGpxButton.visibility = View.GONE
                        viewHistoryButton.visibility = View.VISIBLE
                        stopWorkoutButton.visibility = View.GONE
                        pauseResumeWorkoutButton.visibility = View.GONE
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
                        viewGpxButton.visibility = View.VISIBLE
                        viewHistoryButton.visibility = View.VISIBLE
                        devicesRecyclerView.visibility = View.GONE
                        dataScrollView.visibility = View.VISIBLE
                        
                        Log.d(TAG, "Connected - Setup Workout button visible: ${setupWorkoutButton.visibility == View.VISIBLE}")
                        
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
                
                // Update distance display if workout is active
                if (activeWorkout != null) {
                    val distanceKm = cumulativeDistance / 1000.0
                    distanceTextView.text = getString(R.string.distance_format, distanceKm)
                }
                
                // Update charts
                updateCharts(data.power, data.speed)
                
                // Track workout data if a workout is active
                if (activeWorkout != null && workoutStartTime > 0) {
                    recordWorkoutData(data)
                }
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
    
    private fun recordWorkoutData(data: com.kickr.trainer.model.TrainerData) {
        val currentResistance = activeWorkout?.let { workout ->
            if (workout.gpxTrack != null) {
                workout.getResistanceAtDistance(cumulativeDistance)
            } else {
                workout.getCurrentInterval(workoutElapsedSeconds)?.resistance ?: 0
            }
        } ?: 0
        
        val dataPoint = WorkoutDataPoint(
            timestamp = System.currentTimeMillis(),
            elapsedSeconds = workoutElapsedSeconds,
            power = data.power,
            speed = data.speed,
            cadence = data.cadence,
            heartRate = data.heartRate,
            resistance = currentResistance,
            distance = cumulativeDistance
        )
        
        workoutDataPoints.add(dataPoint)
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
            Log.d(TAG, "Setup Workout button clicked")
            val intent = Intent(this, WorkoutSetupActivity::class.java)
            workoutSetupLauncher.launch(intent)
        }
        
        viewGpxButton.setOnClickListener {
            val intent = Intent(this, GpxMapActivity::class.java)
            gpxMapLauncher.launch(intent)
        }
        
        viewHistoryButton.setOnClickListener {
            val intent = Intent(this, WorkoutHistoryActivity::class.java)
            startActivity(intent)
        }
        
        viewMapButton.setOnClickListener {
            // Open map with current position during GPX workout
            val intent = Intent(this, GpxMapActivity::class.java)
            intent.putExtra(GpxMapActivity.EXTRA_CURRENT_DISTANCE, cumulativeDistance)
            startActivity(intent)
        }
        
        stopWorkoutButton.setOnClickListener {
            stopWorkout()
        }
        
        pauseResumeWorkoutButton.setOnClickListener {
            togglePauseResumeWorkout()
        }
    }
    
    private fun togglePauseResumeWorkout() {
        if (activeWorkout == null) return
        
        workoutPaused = !workoutPaused
        
        if (workoutPaused) {
            // Paused
            workoutPausedAtSecond = workoutElapsedSeconds
            pauseResumeWorkoutButton.text = getString(R.string.resume_workout)
            Toast.makeText(this, "Workout Paused", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Workout paused at ${workoutElapsedSeconds}s")
        } else {
            // Resumed
            pauseResumeWorkoutButton.text = getString(R.string.pause_workout)
            Toast.makeText(this, "Workout Resumed", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Workout resumed at ${workoutElapsedSeconds}s")
        }
    }
    
    private fun startWorkout() {
        val workout = activeWorkout ?: return
        
        Log.d(TAG, "Starting workout: ${workout.intervals.size} intervals, ${workout.totalDuration}s total")
        Toast.makeText(this, "Workout Started! Scroll to see resistance chart.", Toast.LENGTH_LONG).show()
        
        workoutElapsedSeconds = 0
        workoutPaused = false
        workoutPausedAtSecond = 0
        cumulativeDistance = 0.0
        workoutStartTime = System.currentTimeMillis()
        workoutDataPoints.clear()
        
        // Make cards visible
        Log.d(TAG, "Making workout cards visible")
        workoutStatusCard.visibility = View.VISIBLE
        workoutChartCard.visibility = View.VISIBLE
        setupWorkoutButton.visibility = View.GONE
        viewGpxButton.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        disconnectButton.visibility = View.GONE
        stopWorkoutButton.visibility = View.VISIBLE
        pauseResumeWorkoutButton.visibility = View.VISIBLE
        pauseResumeWorkoutButton.text = getString(R.string.pause_workout)
        
        Log.d(TAG, "Workout status card visible: ${workoutStatusCard.visibility == View.VISIBLE}")
        Log.d(TAG, "Workout chart card visible: ${workoutChartCard.visibility == View.VISIBLE}")
        
        // Scroll to top to show workout cards
        dataScrollView.post {
            dataScrollView.smoothScrollTo(0, 0)
        }
        
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
        workoutTimer = object : CountDownTimer((workout.totalDuration + 1) * 1000L, 1000) {
            private var lastResistance = -1
            
            override fun onTick(millisUntilFinished: Long) {
                // Skip processing if workout is paused
                if (workoutPaused) {
                    return
                }
                
                workoutElapsedSeconds++
                Log.d(TAG, "Workout tick: ${workoutElapsedSeconds}s elapsed, remaining: ${workout.getRemainingTime(workoutElapsedSeconds)}s")
                
                // Calculate distance for resistance workouts (not GPX)
                if (workout.gpxTrack == null) {
                    val currentSpeed = bluetoothService.trainerData.value.speed
                    val validSpeed = if (currentSpeed > 0 && currentSpeed < 100) currentSpeed.toDouble() else 0.0
                    val distanceThisSecond = validSpeed / 3.6 // convert km/h to m/s
                    cumulativeDistance += distanceThisSecond
                }
                
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
        
        // Calculate max resistance and set Y-axis to 110% of max
        val maxResistance = workout.intervals.maxOfOrNull { it.resistance } ?: 100
        val yMax = maxResistance * 1.1f
        
        workoutProfileChart.data = LineData(dataSet)
        workoutProfileChart.xAxis.axisMaximum = workout.totalDuration.toFloat()
        workoutProfileChart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = yMax
            setLabelCount(6, false)
            granularity = yMax / 10f
        }
        workoutProfileChart.axisRight.axisMaximum = yMax
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
        
        // Calculate max resistance and set Y-axis to 110% of max
        val maxResistance = workout.intervals.maxOfOrNull { it.resistance } ?: 100
        val yMax = maxResistance * 1.1f
        
        // Add progress indicator line
        val progressEntries = listOf(
            Entry(elapsedSeconds.toFloat(), 0f),
            Entry(elapsedSeconds.toFloat(), yMax)
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
        workoutProfileChart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = yMax
            setLabelCount(6, false)
            granularity = yMax / 10f
        }
        workoutProfileChart.axisRight.axisMaximum = yMax
        workoutProfileChart.notifyDataSetChanged()
        workoutProfileChart.invalidate()
    }
    
    private fun stopWorkout() {
        Log.d(TAG, "Stopping workout")
        
        // Save workout data if we have data points
        if (workoutDataPoints.isNotEmpty() && workoutStartTime > 0) {
            saveWorkoutHistory()
        }
        
        workoutTimer?.cancel()
        workoutTimer = null
        val wasActive = activeWorkout != null
        activeWorkout = null
        workoutElapsedSeconds = 0
        workoutPaused = false
        workoutPausedAtSecond = 0
        cumulativeDistance = 0.0
        workoutStartTime = 0
        
        workoutStatusCard.visibility = View.GONE
        workoutChartCard.visibility = View.GONE
        mapCard.visibility = View.GONE
        setupWorkoutButton.visibility = View.VISIBLE
        viewGpxButton.visibility = View.VISIBLE
        viewHistoryButton.visibility = View.VISIBLE
        disconnectButton.visibility = View.VISIBLE
        stopWorkoutButton.visibility = View.GONE
        pauseResumeWorkoutButton.visibility = View.GONE
        
        // Reset resistance to 0
        bluetoothService.setResistance(0)
        Log.d(TAG, "Resistance reset to 0%")
    }
    
    private fun saveWorkoutHistory() {
        try {
            val endTime = System.currentTimeMillis()
            
            // Calculate statistics
            val totalDistance = workoutDataPoints.lastOrNull()?.distance ?: 0.0
            val avgPower = if (workoutDataPoints.isNotEmpty()) {
                workoutDataPoints.map { it.power }.average()
            } else 0.0
            val maxPower = workoutDataPoints.maxOfOrNull { it.power } ?: 0
            val avgSpeed = if (workoutDataPoints.isNotEmpty()) {
                workoutDataPoints.map { it.speed.toDouble() }.average()
            } else 0.0
            val maxSpeed = workoutDataPoints.maxOfOrNull { it.speed } ?: 0f
            val avgCadence = if (workoutDataPoints.isNotEmpty()) {
                workoutDataPoints.map { it.cadence }.average()
            } else 0.0
            val avgHeartRate = if (workoutDataPoints.isNotEmpty()) {
                val hrValues = workoutDataPoints.filter { it.heartRate > 0 }.map { it.heartRate }
                if (hrValues.isNotEmpty()) hrValues.average() else 0.0
            } else 0.0
            
            // Determine workout type and name
            val workout = activeWorkout
            val workoutType = if (workout?.gpxTrack != null) "GPX" else "RESISTANCE"
            val workoutName = if (workout?.gpxTrack != null) {
                workout.gpxTrack!!.name
            } else {
                "Custom Workout"
            }
            
            val history = WorkoutHistory(
                startTime = workoutStartTime,
                endTime = endTime,
                workoutType = workoutType,
                workoutName = workoutName,
                dataPoints = workoutDataPoints.toList(),
                totalDistance = totalDistance,
                averagePower = avgPower,
                maxPower = maxPower,
                averageSpeed = avgSpeed,
                maxSpeed = maxSpeed,
                averageCadence = avgCadence,
                averageHeartRate = avgHeartRate
            )
            
            val savedFile = workoutStorageManager.saveWorkoutHistory(history)
            if (savedFile != null) {
                Log.d(TAG, "Workout history saved: ${savedFile.absolutePath}")
                
                // Show workout summary
                val intent = Intent(this, WorkoutSummaryActivity::class.java)
                intent.putExtra(WorkoutSummaryActivity.EXTRA_WORKOUT_FILE, savedFile.absolutePath)
                startActivity(intent)
            } else {
                Log.e(TAG, "Failed to save workout history")
                Toast.makeText(this, "Failed to save workout data", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workout history", e)
            Toast.makeText(this, "Error saving workout: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            workoutDataPoints.clear()
        }
    }
    
    private fun startGpxWorkout(gpxTrack: GpxTrack) {
        Log.d(TAG, "Starting GPX workout: ${gpxTrack.name}")
        
        // Store track in holder for map activity
        GpxWorkoutHolder.currentTrack = gpxTrack
        
        // Estimate workout duration based on track distance and average speed (20 km/h)
        val estimatedDurationSeconds = ((gpxTrack.totalDistance / 1000) / 20.0 * 3600).toInt()
        
        // Create a workout with the GPX track
        activeWorkout = Workout(
            totalDuration = estimatedDurationSeconds,
            intervals = listOf(WorkoutInterval(estimatedDurationSeconds, 50)), // Dummy interval
            gpxTrack = gpxTrack
        )
        
        cumulativeDistance = 0.0
        workoutElapsedSeconds = 0
        workoutStartTime = System.currentTimeMillis()
        workoutDataPoints.clear()
        workoutPaused = false
        workoutPausedAtSecond = 0
        
        // Show workout UI
        workoutStatusCard.visibility = View.VISIBLE
        setupWorkoutButton.visibility = View.GONE
        viewGpxButton.visibility = View.GONE
        viewHistoryButton.visibility = View.GONE
        disconnectButton.visibility = View.GONE
        stopWorkoutButton.visibility = View.VISIBLE
        pauseResumeWorkoutButton.visibility = View.VISIBLE
        pauseResumeWorkoutButton.text = getString(R.string.pause_workout)
        viewMapButton.visibility = View.GONE // Hide the external map button
        
        // Try to show and setup map
        try {
            mapCard.visibility = View.VISIBLE
            displayGpxTrackOnMap(gpxTrack)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading map", e)
            Toast.makeText(this, "Map unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            // Continue without map
            mapCard.visibility = View.GONE
        }
        
        // Update workout info
        workoutIntervalTextView.text = "GPX: ${gpxTrack.name}"
        workoutTimeTextView.text = String.format("%.2f / %.2f km", 0.0, gpxTrack.totalDistance / 1000)
        workoutResistanceTextView.text = "Resistance: 50%"
        
        Toast.makeText(this, "GPX workout started! Resistance based on elevation", Toast.LENGTH_LONG).show()
        
        // Start workout timer
        workoutTimer = object : CountDownTimer(estimatedDurationSeconds.toLong() * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (workoutPaused) {
                    return
                }
                workoutElapsedSeconds++
                updateGpxWorkout()
            }
            
            override fun onFinish() {
                workoutIntervalTextView.text = getString(R.string.workout_complete)
                Toast.makeText(this@MainActivity, getString(R.string.workout_complete), Toast.LENGTH_LONG).show()
                stopWorkout()
            }
        }.start()
    }
    
    private fun updateGpxWorkout() {
        val workout = activeWorkout ?: return
        val gpxTrack = workout.gpxTrack ?: return
        
        // Get current speed from trainer data (already in km/h)
        val currentSpeed = bluetoothService.trainerData.value.speed
        
        // Validate speed (sanity check - if > 100 km/h, likely bad data)
        val validSpeed = if (currentSpeed > 0 && currentSpeed < 100) currentSpeed.toDouble() else 0.0
        
        // Calculate distance traveled in last second
        val distanceThisSecond = validSpeed / 3.6 // convert km/h to m/s
        cumulativeDistance += distanceThisSecond
        
        // Clamp distance to track length
        cumulativeDistance = cumulativeDistance.coerceAtMost(gpxTrack.totalDistance)
        
        // Get resistance based on current position on track
        val targetResistance = workout.getResistanceAtDistance(cumulativeDistance)
        val gradient = gpxTrack.getGradientAtDistance(cumulativeDistance)
        
        // Update map position
        updateMapPosition(cumulativeDistance)
        
        // Update UI
        workoutTimeTextView.text = String.format(
            "%.2f / %.2f km", 
            cumulativeDistance / 1000, 
            gpxTrack.totalDistance / 1000
        )
        workoutResistanceTextView.text = String.format(
            "Resistance: %d%% (%.1f%% grade)", 
            targetResistance,
            gradient
        )
        
        // Send resistance command to trainer
        bluetoothService.setResistance(targetResistance)
        Log.d(TAG, "GPX workout: distance=%.1fm, speed=%.1f km/h, resistance=%d%%, gradient=%.1f%%".format(
            cumulativeDistance, validSpeed, targetResistance, gradient
        ))
        
        // Check if workout complete
        if (cumulativeDistance >= gpxTrack.totalDistance) {
            workoutTimer?.cancel()
            workoutIntervalTextView.text = getString(R.string.workout_complete)
            Toast.makeText(this, "Route complete!", Toast.LENGTH_LONG).show()
            stopWorkout()
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWorkout()
        bluetoothService.stopScan()
        bluetoothService.disconnect()
        mapView.onDetach()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About OpenTrainer")
            .setMessage("""
                OpenTrainer v2.0
                
                A free and open-source cycling training app for Wahoo Kickr and compatible smart trainers.
                
                Copyright © 2026 Amine Othmane
                All rights reserved.
                
                Features:
                • Real-time power, cadence, and speed monitoring
                • Custom resistance workouts
                • GPX-based elevation workouts
                • Workout history and analytics
                • 100% offline - no data collection
                
                This application is open source and respects your privacy.
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
}
