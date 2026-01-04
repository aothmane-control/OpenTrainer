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
                else -> skip(parser)
            }
        }
        
        return if (points.isNotEmpty()) {
            GpxTrack(trackName, points)
        } else {
            null
        }
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
                points.add(readTrackPoint(parser))
            } else {
                skip(parser)
            }
        }
        
        return points
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readTrackPoint(parser: XmlPullParser): GpxTrackPoint {
        parser.require(XmlPullParser.START_TAG, null, "trkpt")
        
        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
        
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
        
        return GpxTrackPoint(lat, lon, elevation, time, heartRate, cadence, power)
    }
    
    @Throws(XmlPullParserException::class, IOException::class)
    private fun readExtensions(parser: XmlPullParser): Map<String, Int> {
        val extensions = mutableMapOf<String, Int>()
        
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            
            when {
                parser.name.contains("hr", ignoreCase = true) -> {
                    extensions["hr"] = readText(parser).toIntOrNull() ?: 0
                }
                parser.name.contains("cad", ignoreCase = true) -> {
                    extensions["cad"] = readText(parser).toIntOrNull() ?: 0
                }
                parser.name.contains("power", ignoreCase = true) -> {
                    extensions["power"] = readText(parser).toIntOrNull() ?: 0
                }
                else -> skip(parser)
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
