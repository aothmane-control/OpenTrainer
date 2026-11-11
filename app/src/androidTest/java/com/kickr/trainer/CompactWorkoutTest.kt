package com.kickr.trainer

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.tabs.TabLayout
import com.kickr.trainer.model.GpxTrack
import com.kickr.trainer.model.GpxTrackPoint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactWorkoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun compactPreferenceMetricSwapTimerAndReturnToClassic() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val checkbox = activity.findViewById<CheckBox>(R.id.compactLayoutCheckBox)
                assertEquals(View.VISIBLE, checkbox.visibility)
                checkbox.isChecked = true
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<CheckBox>(R.id.compactLayoutCheckBox).isChecked)
            }
            startIntervals(scenario)
            scenario.onActivity { activity ->
                assertCompact(activity)
                assertEquals(activity.getString(R.string.power), text(activity, R.id.compactFocusedLabel))
                val tabs = activity.findViewById<TabLayout>(R.id.compactChartTabs)
                tabs.getTabAt(2)!!.select()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.speedChart).visibility)
                activity.findViewById<Button>(R.id.compactMetricTwo).performClick()
                assertEquals(activity.getString(R.string.speed), text(activity, R.id.compactFocusedLabel))
                assertTrue(text(activity, R.id.compactMetricTwo).contains(activity.getString(R.string.power)))
                assertEquals(2, tabs.selectedTabPosition)
                for ((button, label) in listOf(
                    R.id.compactMetricThree to R.string.distance,
                    R.id.compactMetricOne to R.string.cadence,
                    R.id.compactMetricTwo to R.string.power,
                    R.id.compactMetricThree to R.string.speed
                )) {
                    val previous = text(activity, R.id.compactFocusedLabel)
                    activity.findViewById<Button>(button).performClick()
                    assertEquals(activity.getString(label), text(activity, R.id.compactFocusedLabel))
                    assertTrue(text(activity, button).contains(previous))
                    assertEquals(2, tabs.selectedTabPosition)
                }
                assertTrue(activity.findViewById<View>(R.id.compactWorkoutPanel).keepScreenOn)
            }
            var before = ""
            scenario.onActivity { before = text(it, R.id.compactWorkoutTime) }
            SystemClock.sleep(1500)
            scenario.onActivity { activity ->
                assertNotEquals(before, text(activity, R.id.compactWorkoutTime))
                assertEquals(activity.getString(R.string.speed), text(activity, R.id.compactFocusedLabel))
                activity.findViewById<Button>(R.id.compactPauseButton).performClick()
                before = text(activity, R.id.compactWorkoutTime)
                assertEquals(activity.getString(R.string.compact_resume), text(activity, R.id.compactPauseButton))
            }
            SystemClock.sleep(1500)
            scenario.onActivity { activity ->
                assertEquals(before, text(activity, R.id.compactWorkoutTime))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.compactLayoutCheckBox).visibility)
                activity.findViewById<Button>(R.id.compactPauseButton).performClick()
                assertEquals(activity.getString(R.string.compact_pause), text(activity, R.id.compactPauseButton))
            }
            stopWorkout(scenario, R.id.compactStopButton)
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.compactWorkoutPanel).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.toolbar).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.compactLayoutCheckBox).visibility)
                for (id in listOf(R.id.workoutProfileChart, R.id.powerChart, R.id.speedChart)) {
                    val chart = activity.findViewById<LineChart>(id)
                    assertEquals(View.VISIBLE, chart.visibility)
                    assertNotEquals(R.id.compactChartContainer, (chart.parent as View).id)
                }
            }
            startIntervals(scenario)
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.power), text(activity, R.id.compactFocusedLabel))
                assertEquals(0, activity.findViewById<TabLayout>(R.id.compactChartTabs).selectedTabPosition)
            }
            stopWorkout(scenario, R.id.compactStopButton)
            scenario.onActivity { it.findViewById<CheckBox>(R.id.compactLayoutCheckBox).isChecked = false }
        }
    }

    @Test
    fun classicLayoutRemainsAvailable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<CheckBox>(R.id.compactLayoutCheckBox).isChecked = false }
            startIntervals(scenario, power = true)
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.compactWorkoutPanel).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.dataScrollView).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.toolbar).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.stopWorkoutButton).visibility)
                assertEquals(activity.getString(R.string.workout_power_format, 180),
                    text(activity, R.id.workoutResistanceTextView))
            }
            stopWorkout(scenario, R.id.stopWorkoutButton)
        }
    }

    @Test
    fun compactPowerTargetsAndGpxStillUsesClassicLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<CheckBox>(R.id.compactLayoutCheckBox).isChecked = true }
            startIntervals(scenario, power = true)
            scenario.onActivity { activity ->
                assertCompact(activity)
                assertEquals(activity.getString(R.string.workout_power_format, 180),
                    text(activity, R.id.compactCurrentTarget))
                assertEquals(activity.getString(R.string.compact_next_power, 220),
                    text(activity, R.id.compactNextTarget))
            }
            stopWorkout(scenario, R.id.compactStopButton)
            GpxWorkoutHolder.currentTrack = GpxTrack("Test route", listOf(
                GpxTrackPoint(48.85, 2.35, 30.0), GpxTrackPoint(48.87, 2.37, 50.0)
            )).withDistances()
            returnFromSetup(scenario, GpxMapActivity::class.java, R.id.viewGpxButton,
                Intent().putExtra("has_gpx_workout", true))
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.compactWorkoutPanel).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mapCard).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.stopWorkoutButton).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.compactLayoutCheckBox).visibility)
            }
            stopWorkout(scenario, R.id.stopWorkoutButton)
            scenario.onActivity { it.findViewById<CheckBox>(R.id.compactLayoutCheckBox).isChecked = false }
        }
    }

    private fun startIntervals(scenario: ActivityScenario<MainActivity>, power: Boolean = false) {
        val result = Intent()
            .putExtra("workout_duration", 1200)
            .putExtra("workout_type", if (power) "POWER" else "RESISTANCE")
            .putExtra("workout_interval_durations", intArrayOf(300, 300, 300, 300))
            .putExtra("workout_interval_resistances", intArrayOf(20, 40, 60, 30))
            .putExtra("workout_interval_powers", intArrayOf(180, 220, 250, 120))
        returnFromSetup(scenario, WorkoutSetupActivity::class.java, R.id.setupWorkoutButton, result)
    }

    private fun returnFromSetup(
        scenario: ActivityScenario<MainActivity>, activityClass: Class<out Activity>, button: Int, result: Intent
    ) {
        val monitor = instrumentation.addMonitor(activityClass.name, null, false)
        try {
            // No bike required: invoke the setup action directly, then use the real activity-result path.
            scenario.onActivity { it.findViewById<Button>(button).performClick() }
            val setup = instrumentation.waitForMonitorWithTimeout(monitor, 10000)
            assertNotNull("Setup activity did not open", setup)
            instrumentation.runOnMainSync {
                setup.setResult(Activity.RESULT_OK, result)
                setup.finish()
            }
            instrumentation.waitForIdleSync()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun stopWorkout(scenario: ActivityScenario<MainActivity>, button: Int) {
        val monitor = instrumentation.addMonitor(WorkoutSummaryActivity::class.java.name, null, false)
        try {
            scenario.onActivity { it.findViewById<Button>(button).performClick() }
            val summary = instrumentation.waitForMonitorWithTimeout(monitor, 10000)
            assertNotNull("Workout was not saved", summary)
            instrumentation.runOnMainSync { summary.finish() }
            instrumentation.waitForIdleSync()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun assertCompact(activity: MainActivity) {
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.compactWorkoutPanel).visibility)
        for (id in listOf(R.id.toolbar, R.id.connectionStatusTextView, R.id.copyrightTextView,
            R.id.compactLayoutCheckBox, R.id.dataScrollView, R.id.stopWorkoutButton)) {
            assertEquals(View.GONE, activity.findViewById<View>(id).visibility)
        }
        val scroll = activity.findViewById<NestedScrollView>(R.id.compactWorkoutScroll)
        assertFalse("Compact workout must fit the reference viewport", scroll.canScrollVertically(1))
        assertTrue(activity.findViewById<Button>(R.id.compactPauseButton).isShown)
        assertTrue(activity.findViewById<Button>(R.id.compactStopButton).isShown)
        assertEquals(0, activity.findViewById<TabLayout>(R.id.compactChartTabs).selectedTabPosition)
    }

    private fun text(activity: MainActivity, id: Int) = activity.findViewById<TextView>(id).text.toString()
}
