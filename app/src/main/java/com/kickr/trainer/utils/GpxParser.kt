/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.utils

import android.util.Log
import android.util.Xml
import com.kickr.trainer.model.GpxTrack
import com.kickr.trainer.model.GpxTrackPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

class GpxParser {
    
    companion object {
        private const val TAG = "GpxParser"
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream): GpxTrack? {
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)
            parser.nextTag()
            return readGpx(parser)
        }
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readGpx(parser: XmlPullParser): GpxTrack? {
        var trackName = "Unnamed Track"
        val points = mutableListOf<GpxTrackPoint>()
        var routeName = "Unnamed Track"
        val routePoints = mutableListOf<GpxTrackPoint>()
        
        parser.require(XmlPullParser.START_TAG, null, "gpx")
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            
            when (parser.name) {
                "trk" -> {
                    val track = readTrack(parser)
                    if (track != null) {
                        trackName = track.first
                        points.addAll(track.second)
                    }
                }
                "rte" -> {
                    val route = readRoute(parser)
                    if (route != null) {
                        routeName = route.first
                        routePoints.addAll(route.second)
                    }
                }
                else -> skip(parser)
            }
        }
        
        // Planned routes (<rte>, e.g. exported from route planners) are only used
        // when the file has no recorded track
        return when {
            points.isNotEmpty() -> GpxTrack(trackName, points)
            routePoints.isNotEmpty() -> GpxTrack(routeName, routePoints)
            else -> null
        }
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readRoute(parser: XmlPullParser): Pair<String, List<GpxTrackPoint>>? {
        parser.require(XmlPullParser.START_TAG, null, "rte")
        var name = "Unnamed Track"
        val points = mutableListOf<GpxTrackPoint>()
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            
            when (parser.name) {
                "name" -> name = readText(parser)
                "rtept" -> readTrackPoint(parser, "rtept")?.let { points.add(it) }
                else -> skip(parser)
            }
        }
        
        return if (points.isNotEmpty()) Pair(name, points) else null
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readTrack(parser: XmlPullParser): Pair<String, List<GpxTrackPoint>>? {
        parser.require(XmlPullParser.START_TAG, null, "trk")
        var name = "Unnamed Track"
        val points = mutableListOf<GpxTrackPoint>()
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            
            when (parser.name) {
                "name" -> name = readText(parser)
                "trkseg" -> points.addAll(readTrackSegment(parser))
                else -> skip(parser)
            }
        }
        
        return if (points.isNotEmpty()) Pair(name, points) else null
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readTrackSegment(parser: XmlPullParser): List<GpxTrackPoint> {
        val points = mutableListOf<GpxTrackPoint>()
        parser.require(XmlPullParser.START_TAG, null, "trkseg")
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            
            if (parser.name == "trkpt") {
                readTrackPoint(parser, "trkpt")?.let { points.add(it) }
            } else {
                skip(parser)
            }
        }
        
        return points
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readTrackPoint(parser: XmlPullParser, tag: String): GpxTrackPoint? {
        parser.require(XmlPullParser.START_TAG, null, tag)
        
        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
        
        var elevation: Double? = null
        var time: String? = null
        var heartRate: Int? = null
        var cadence: Int? = null
        var power: Int? = null
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            
            when (parser.name) {
                "ele" -> elevation = readText(parser).toDoubleOrNull()
                "time" -> time = readText(parser)
                "extensions" -> {
                    val extensions = readExtensions(parser)
                    heartRate = extensions["hr"]
                    cadence = extensions["cad"]
                    power = extensions["power"]
                }
                else -> skip(parser)
            }
        }
        
        // A point without valid coordinates would otherwise be placed at 0,0
        // and add thousands of kilometers to the track distance
        if (lat == null || lon == null) {
            Log.w(TAG, "Skipping <$tag> without valid lat/lon")
            return null
        }
        
        return GpxTrackPoint(lat, lon, elevation, time, heartRate, cadence, power)
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    // Descends into container elements such as Garmin's <gpxtpx:TrackPointExtension>,
    // where hr and cad are nested one level below <extensions>
    private fun readExtensions(
        parser: XmlPullParser,
        extensions: MutableMap<String, Int> = mutableMapOf()
    ): Map<String, Int> {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            
            // Namespace processing is off, so names keep their prefix (e.g. "gpxtpx:hr")
            when (parser.name.substringAfter(':').lowercase()) {
                "hr", "heartrate" -> {
                    extensions["hr"] = readText(parser).trim().toIntOrNull() ?: 0
                }
                "cad", "cadence" -> {
                    extensions["cad"] = readText(parser).trim().toIntOrNull() ?: 0
                }
                "power", "powerinwatts", "watts" -> {
                    extensions["power"] = readText(parser).trim().toIntOrNull() ?: 0
                }
                else -> readExtensions(parser, extensions)
            }
        }
        
        return extensions
    }
    
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
