/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

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
import androidx.lifecycle.lifecycleScope
import com.kickr.trainer.model.GpxTrack
import com.kickr.trainer.utils.GpxParser
import com.kickr.trainer.utils.applyEdgeToEdgeInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import java.io.FileNotFoundException
import kotlin.math.max
import kotlin.math.min

class GpxMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var loadGpxButton: Button
    private lateinit var riderBikeButton: Button
    private lateinit var startWorkoutButton: Button
    private lateinit var trackInfoTextView: TextView
    private var currentTrack: GpxTrack? = null

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
        applyEdgeToEdgeInsets()

        initViews()
        setupMap()
        setupClickListeners()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        loadGpxButton = findViewById(R.id.loadGpxButton)
        riderBikeButton = findViewById(R.id.riderBikeButton)
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

        riderBikeButton.setOnClickListener {
            startActivity(Intent(this, RiderBikeSettingsActivity::class.java))
        }
        
        startWorkoutButton.setOnClickListener {
            currentTrack?.let { track ->
                // Return to MainActivity with GPX track data
                val resultIntent = Intent().apply {
                    putExtra("has_gpx_workout", true)
                    putExtra("gpx_track_name", track.name)
                    // We'll pass the track via a singleton or save it temporarily;
                    // loadGpxFile() already computed its distances
                    GpxWorkoutHolder.currentTrack = track
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
    
    private fun loadGpxFile(uri: Uri) {
        loadGpxButton.isEnabled = false
        lifecycleScope.launch {
            try {
                // Reading and parsing a long recording can take seconds, too long for the main thread
                val track = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw FileNotFoundException("Failed to open file")
                    GpxParser().parse(inputStream)?.withDistances()
                }

                if (track != null && track.points.isNotEmpty()) {
                    currentTrack = track
                    displayTrackOnMap(track)
                    updateTrackInfo(track)
                    startWorkoutButton.visibility = android.view.View.VISIBLE
                    Toast.makeText(this@GpxMapActivity, "GPX loaded: ${track.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@GpxMapActivity, "No track data found in GPX", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@GpxMapActivity, "Error loading GPX: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } finally {
                loadGpxButton.isEnabled = true
            }
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
        val distance = track.totalDistance
        
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
            // The grade is interpolated across points without elevation; without any the ride is flat
            if (elevations.size < pointCount * 0.95) {
                append("Warning: no elevation for ${pointCount - elevations.size} of $pointCount points")
                append(if (elevations.isEmpty()) ", the ride will be flat\n" else "\n")
            }
            // The map draws straight lines across the gaps, but they add no distance or climb
            val segments = track.points.distinctBy { it.segment }.size
            if (segments > 1) append("Segments: $segments, joined without the gaps between them\n")
            
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
