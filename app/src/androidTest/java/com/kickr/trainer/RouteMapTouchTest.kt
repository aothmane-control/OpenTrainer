package com.kickr.trainer

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@RunWith(AndroidJUnit4::class)
class RouteMapTouchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    // Like the ride's map card: a map of fixed height in a scroll view, with more below it to scroll to
    private fun mapInScrollView(check: (NestedScrollView, MapView) -> Unit) = instrumentation.runOnMainSync {
        RouteMap.configure(context)
        val map = MapView(context)
        RouteMap(map)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(map, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 800))
            addView(View(context), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1200))
        }
        val scroll = NestedScrollView(context).apply { addView(content) }
        scroll.measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1000, MeasureSpec.EXACTLY))
        scroll.layout(0, 0, 1000, 1000)
        check(scroll, map)
    }

    private fun NestedScrollView.touch(action: Int, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 500f, y, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }

    @Test
    fun aVerticalDragPansTheMapInsteadOfScrollingThePage() = mapInScrollView { scroll, map ->
        // Upwards, so the page would scroll down if it took the drag
        val before = map.mapCenter.latitude
        val down = SystemClock.uptimeMillis()
        scroll.touch(MotionEvent.ACTION_DOWN, 500f, down)
        for (y in listOf(480f, 440f, 380f, 300f)) scroll.touch(MotionEvent.ACTION_MOVE, y, down)
        scroll.touch(MotionEvent.ACTION_UP, 300f, down)

        assertEquals("The page must not scroll", 0, scroll.scrollY)
        assertTrue("The map must pan south", map.mapCenter.latitude < before)
    }

    @Test
    fun aTouchStopsTheRecentring() = mapInScrollView { scroll, map ->
        map.controller.animateTo(GeoPoint(50.0, 8.0))
        assertTrue(map.isAnimating)
        scroll.touch(MotionEvent.ACTION_DOWN, 300f, SystemClock.uptimeMillis())
        assertFalse(map.isAnimating)
    }
}
