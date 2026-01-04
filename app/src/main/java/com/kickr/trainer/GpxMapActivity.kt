package com.kickr.trainer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kickr.trainer.model.GpxTrack
import com.kickr.trainer.utils.GpxParser
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import kotlin.math.max
import kotlin.math.min

class GpxMapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GPX_TRACK = "gpx_track"
        const val EXTRA_CURRENT_DISTANCE = "current_distance"
    }

    private lateinit var mapView: MapView
    private lateinit var loadGpxButton: Button
    private lateinit var startWorkoutButton: Button
    private lateinit var trackInfoTextView: TextView
    private var currentTrack: GpxTrack? = null
    private var currentPositionMarker: Marker? = null

    private val gpxFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadGpxFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure osmdroid
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        
        setContentView(R.layout.activity_gpx_map)

        initViews()
        setupMap()
        setupClickListeners()
        
        // Check if we're being launched with track data (for live tracking)
        handleIncomingIntent()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        loadGpxButton = findViewById(R.id.loadGpxButton)
        startWorkoutButton = findViewById(R.id.startWorkoutButton)
        trackInfoTextView = findViewById(R.id.trackInfoTextView)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(13.0)
        
        // Default to a central location
        mapView.controller.setCenter(GeoPoint(51.5074, -0.1278)) // London
    }

    private fun setupClickListeners() {
        loadGpxButton.setOnClickListener {
            gpxFileLauncher.launch(arrayOf("application/gpx+xml", "text/xml", "*/*"))
        }
        
        startWorkoutButton.setOnClickListener {
            currentTrack?.let { track ->
                // Return to MainActivity with GPX track data
                val resultIntent = Intent().apply {
                    putExtra("has_gpx_workout", true)
                    putExtra("gpx_track_name", track.name)
                    // We'll pass the track via a singleton or save it temporarily
                    GpxWorkoutHolder.currentTrack = track.withDistances()
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
    
    private fun handleIncomingIntent() {
        // Check if launched for live tracking
        val currentDistance = intent.getDoubleExtra(EXTRA_CURRENT_DISTANCE, -1.0)
        if (currentDistance >= 0 && GpxWorkoutHolder.currentTrack != null) {
            currentTrack = GpxWorkoutHolder.currentTrack
            currentTrack?.let { track ->
                displayTrackOnMap(track)
                updateCurrentPosition(currentDistance)
                startWorkoutButton.visibility = android.view.View.GONE
                loadGpxButton.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun updateCurrentPosition(distanceMeters: Double) {
        val track = currentTrack ?: return
        val position = track.getPointAtDistance(distanceMeters)
        
        position?.let { point ->
            // Remove old marker
            currentPositionMarker?.let { mapView.overlays.remove(it) }
            
            // Add new position marker
            currentPositionMarker = Marker(mapView).apply {
                this.position = GeoPoint(point.latitude, point.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Current Position"
                snippet = String.format("%.2f km", distanceMeters / 1000)
                icon = resources.getDrawable(android.R.drawable.ic_menu_mylocation, null)
            }
            mapView.overlays.add(currentPositionMarker)
            
            // Center map on current position
            mapView.controller.animateTo(GeoPoint(point.latitude, point.longitude))
            mapView.invalidate()
        }
    }

    private fun loadGpxFile(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show()
                return
            }

            val parser = GpxParser()
            val track = parser.parse(inputStream)

            if (track != null && track.points.isNotEmpty()) {
                currentTrack = track.withDistances()
                displayTrackOnMap(currentTrack!!)
                updateTrackInfo(currentTrack!!)
                startWorkoutButton.visibility = android.view.View.VISIBLE
                Toast.makeText(this, "GPX loaded: ${track.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No track data found in GPX", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading GPX: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun displayTrackOnMap(track: GpxTrack) {
        // Clear existing overlays
        mapView.overlays.clear()

        // Create polyline for the track
        val polyline = Polyline().apply {
            outlinePaint.color = getColor(android.R.color.holo_blue_dark)
            outlinePaint.strokeWidth = 8f
        }

        val geoPoints = track.points.map { point ->
            GeoPoint(point.latitude, point.longitude)
        }
        polyline.setPoints(geoPoints)
        mapView.overlays.add(polyline)

        // Add start marker
        if (geoPoints.isNotEmpty()) {
            val startMarker = Marker(mapView).apply {
                position = geoPoints.first()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Start"
                snippet = track.name
            }
            mapView.overlays.add(startMarker)

            // Add end marker
            if (geoPoints.size > 1) {
                val endMarker = Marker(mapView).apply {
                    position = geoPoints.last()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "End"
                }
                mapView.overlays.add(endMarker)
            }
        }

        // Calculate bounding box and zoom to fit
        if (geoPoints.isNotEmpty()) {
            val bounds = calculateBounds(geoPoints)
            mapView.zoomToBoundingBox(bounds, true, 100)
        }

        mapView.invalidate()
    }

    private fun calculateBounds(points: List<GeoPoint>): org.osmdroid.util.BoundingBox {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        points.forEach { point ->
            minLat = min(minLat, point.latitude)
            maxLat = max(maxLat, point.latitude)
            minLon = min(minLon, point.longitude)
            maxLon = max(maxLon, point.longitude)
        }

        return org.osmdroid.util.BoundingBox(maxLat, maxLon, minLat, minLon)
    }

    private fun updateTrackInfo(track: GpxTrack) {
        val pointCount = track.points.size
        val distance = calculateDistance(track.points)
        
        val info = buildString {
            append("Track: ${track.name}\n")
            append("Points: $pointCount\n")
            append("Distance: ${"%.2f".format(distance / 1000)} km\n")
            
            // Show elevation if available
            val elevations = track.points.mapNotNull { it.elevation }
            if (elevations.isNotEmpty()) {
                val minEle = elevations.minOrNull() ?: 0.0
                val maxEle = elevations.maxOrNull() ?: 0.0
                append("Elevation: ${"%.0f".format(minEle)}m - ${"%.0f".format(maxEle)}m\n")
            }
            
            // Show data availability
            val hasHR = track.points.any { it.heartRate != null }
            val hasCadence = track.points.any { it.cadence != null }
            val hasPower = track.points.any { it.power != null }
            
            if (hasHR || hasCadence || hasPower) {
                append("Data: ")
                val dataTypes = mutableListOf<String>()
                if (hasHR) dataTypes.add("HR")
                if (hasCadence) dataTypes.add("Cadence")
                if (hasPower) dataTypes.add("Power")
                append(dataTypes.joinToString(", "))
            }
        }
        
        trackInfoTextView.text = info
    }

    private fun calculateDistance(points: List<com.kickr.trainer.model.GpxTrackPoint>): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = GeoPoint(points[i].latitude, points[i].longitude)
            val p2 = GeoPoint(points[i + 1].latitude, points[i + 1].longitude)
            distance += p1.distanceToAsDouble(p2)
        }
        return distance
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
