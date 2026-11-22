/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.preference.PreferenceManager
import com.kickr.trainer.model.GpxTrack
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** A GPX route and the rider's position on an OpenStreetMap map: the GPX screen and the ride. */
internal class RouteMap(private val mapView: MapView) {
    private var positionMarker: Marker? = null
    // When the rider last touched the map, to look around it
    private var lastTouchMs: Long? = null

    init {
        noteTouches()
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            // Central Europe until a route is shown
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(48.8566, 2.3522))
        }
    }

    // Notes the time; the map still handles the touch, and taps still reach its markers. A finger
    // going down also keeps the gesture in the map, since during a ride it sits in a scroll view
    // that would take over any drag or pinch with a vertical part, and stops the re-centring
    // animation, which would otherwise pull the map back under the finger.
    @SuppressLint("ClickableViewAccessibility")
    private fun noteTouches() = mapView.setOnTouchListener { view, event ->
        lastTouchMs = SystemClock.uptimeMillis()
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            view.parent?.requestDisallowInterceptTouchEvent(true)
            mapView.controller.stopAnimation(false)
        }
        false
    }

    /** Draws the route with start and end markers and fits it into view. */
    fun showRoute(track: GpxTrack) {
        val points = track.points.map { GeoPoint(it.latitude, it.longitude) }
        mapView.overlays.clear()
        positionMarker = null
        if (points.isEmpty()) return
        mapView.overlays.add(Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = ContextCompat.getColor(mapView.context, android.R.color.holo_blue_dark)
            outlinePaint.strokeWidth = 8f
            outlinePaint.isAntiAlias = true
        })
        mapView.overlays.add(marker(points.first(), "Start", track.name))
        if (points.size > 1) mapView.overlays.add(marker(points.last(), "End"))
        // Fitting needs the map's size, and a ride that already follows the rider keeps that view
        mapView.doOnLayout {
            if (positionMarker != null) return@doOnLayout
            if (points.size > 1) {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, FIT_PADDING_PX)
            } else {
                mapView.controller.setZoom(FOLLOW_ZOOM)
                mapView.controller.setCenter(points.first())
            }
        }
        mapView.invalidate()
    }

    /**
     * Moves the rider's marker to the distance along the route and centres the map on it, unless the
     * rider moved the map in the last FOLLOW_PAUSE_MS.
     */
    fun showPosition(track: GpxTrack, distanceMeters: Double) {
        val point = track.getPointAtDistance(distanceMeters) ?: return
        val geoPoint = GeoPoint(point.latitude, point.longitude)
        val marker = positionMarker ?: marker(geoPoint, "Current Position").apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(mapView.context, android.R.drawable.ic_menu_mylocation)
            mapView.overlays.add(this)
            positionMarker = this
            // Zooms in on the rider once; after that the rider's own zoom stays
            mapView.controller.setZoom(FOLLOW_ZOOM)
        }
        marker.position = geoPoint
        val touched = lastTouchMs?.let { SystemClock.uptimeMillis() - it < FOLLOW_PAUSE_MS } == true
        if (!touched) mapView.controller.animateTo(geoPoint)
        mapView.invalidate()
    }

    private fun marker(position: GeoPoint, title: String, snippet: String? = null) = Marker(mapView).apply {
        this.position = position
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        this.title = title
        this.snippet = snippet
    }

    companion object {
        private const val FIT_PADDING_PX = 100
        private const val FOLLOW_ZOOM = 16.0  // About 600 m across a phone screen
        private const val FOLLOW_PAUSE_MS = 10_000L

        /** osmdroid settings and tile cache; call before inflating a layout with a map. */
        fun configure(context: Context) {
            Configuration.getInstance().apply {
                load(context, PreferenceManager.getDefaultSharedPreferences(context))
                userAgentValue = context.packageName
                osmdroidBasePath = context.getExternalFilesDir(null)
                osmdroidTileCache = File(context.getExternalFilesDir(null), "osmdroid/tiles")
            }
        }
    }
}
