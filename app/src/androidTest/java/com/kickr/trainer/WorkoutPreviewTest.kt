package com.kickr.trainer

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.mikephil.charting.charts.LineChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutPreviewTest {
    @Test
    fun previewShowsFrozenWorkoutAndNormalLaunchStillStartsDisconnected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preview = Intent(context, MainActivity::class.java).putExtra("preview", true)
        ActivityScenario.launch<MainActivity>(preview).use { scenario ->
            fun checkPreview() {
                scenario.onActivity { activity ->
                    assertNull(activity.supportActionBar?.subtitle)
                    assertEquals("Connected (faked with sample data)",
                        activity.findViewById<TextView>(R.id.connectionStatusTextView).text.toString())
                    assertEquals(activity.getString(R.string.workout_time_format, 12, 34),
                        activity.findViewById<TextView>(R.id.workoutTimeTextView).text.toString())
                    assertEquals(activity.getString(R.string.power_format, 180),
                        activity.findViewById<TextView>(R.id.powerTextView).text.toString())
                    for (id in listOf(R.id.dataScrollView, R.id.workoutStatusCard, R.id.workoutChartCard)) {
                        assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
                    }
                    for (id in listOf(R.id.stopWorkoutButton, R.id.pauseResumeWorkoutButton)) {
                        val button = activity.findViewById<Button>(id)
                        assertEquals(View.VISIBLE, button.visibility)
                        assertFalse(button.isEnabled)
                        assertFalse(button.hasOnClickListeners())
                    }
                    for (id in listOf(R.id.scanButton, R.id.setupWorkoutButton, R.id.disconnectButton,
                        R.id.viewGpxButton, R.id.viewHistoryButton)) {
                        assertEquals(View.GONE, activity.findViewById<View>(id).visibility)
                    }
                    for (id in listOf(R.id.powerChart, R.id.speedChart, R.id.workoutProfileChart)) {
                        assertTrue(activity.findViewById<LineChart>(id).data.entryCount > 1)
                    }
                }
            }
            checkPreview()
            SystemClock.sleep(1500) // A running timer would have changed; observers must not overwrite the preview.
            checkPreview()
            scenario.recreate()
            checkPreview()
        }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.disconnected),
                    activity.findViewById<TextView>(R.id.connectionStatusTextView).text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.workoutStatusCard).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanButton).visibility)
            }
        }
    }
}
