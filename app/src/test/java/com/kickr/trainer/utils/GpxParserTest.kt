package com.kickr.trainer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File

class GpxParserTest {

    // KXmlParser is what android.util.Xml returns on the device
    private val parser = GpxParser { KXmlParser() }

    private fun parse(gpx: String) = parser.parse(gpx.byteInputStream())

    @Test
    fun readsTheTestRoute() {
        // Unit tests run in the app module directory
        val track = File("../testdata/grade-test.gpx").inputStream().use { parser.parse(it) }!!
        assertEquals("OpenTrainer grade test", track.name)
        // The point without lat is skipped, so its 999 m elevation never appears
        assertEquals(21, track.points.size)
        assertEquals(222.5, track.points.maxOf { it.elevation!! }, 0.0)
        // 0.009° of latitude at 49° N on the WGS-84 ellipsoid; the old spherical distance gave 1001.9 m
        assertEquals(1000.89, track.withDistances().totalDistance, 0.01)

        // <power> sits directly in <extensions>, hr and cad in Garmin's TrackPointExtension
        val first = track.points.first()
        assertEquals(200.0, first.elevation!!, 0.0)
        assertEquals("2026-11-19T08:00:00Z", first.time)
        assertEquals(130, first.heartRate)
        assertEquals(85, first.cadence)
        assertEquals(210, first.power)
    }

    @Test
    fun usesTheRouteWhenThereIsNoTrack() {
        val track = parse(
            """<gpx><rte><name>Planned</name>
                <rtept lat="49.0" lon="7.0"><ele>100</ele></rtept>
                <rtept lat="49.001" lon="7.0"/>
            </rte></gpx>"""
        )!!
        assertEquals("Planned", track.name)
        assertEquals(2, track.points.size)
        assertNull(track.points[1].elevation)
    }

    @Test
    fun prefersTheTrackOverTheRoute() {
        val track = parse(
            """<gpx>
                <rte><name>Planned</name><rtept lat="49.0" lon="7.0"/></rte>
                <trk><name>Ridden</name><trkseg><trkpt lat="49.0" lon="7.0"/><trkpt lat="49.001" lon="7.0"/></trkseg></trk>
            </gpx>"""
        )!!
        assertEquals("Ridden", track.name)
        assertEquals(2, track.points.size)
    }

    @Test
    fun unreadableExtensionValuesAreMissingNotZero() {
        val track = parse(
            """<gpx xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"><trk><trkseg>
                <trkpt lat="49.0" lon="7.0"><extensions><power>n/a</power><gpxtpx:TrackPointExtension>
                    <gpxtpx:hr></gpxtpx:hr><gpxtpx:cad> 0 </gpxtpx:cad>
                </gpxtpx:TrackPointExtension></extensions></trkpt>
            </trkseg></trk></gpx>"""
        )!!
        val point = track.points.single()
        assertNull(point.power)
        assertNull(point.heartRate)
        // Zero is a real reading, for example when coasting
        assertEquals(0, point.cadence)
    }

    @Test
    fun fileWithoutPointsGivesNoTrack() {
        assertNull(parse("<gpx><trk><name>Empty</name><trkseg/></trk></gpx>"))
    }
}
