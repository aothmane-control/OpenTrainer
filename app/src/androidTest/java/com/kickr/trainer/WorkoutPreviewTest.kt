package com.kickr.trainer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutPreviewTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun missingOrFalseCompactFlagAlwaysShowsClassicPreview() = withSavedPreference(true) { preferences ->
        val saved = preferences.all
        for (compact in listOf<Boolean?>(null, false)) {
            val preview = Intent(context, MainActivity::class.java).putExtra("preview", true)
            if (compact != null) preview.putExtra("compact", compact)
            ActivityScenario.launch<MainActivity>(preview).use { scenario ->
                fun checkPreview() {
                    scenario.onActivity { activity ->
                        assertCommonPreview(activity)
                        assertEquals(View.GONE, activity.findViewById<View>(R.id.compactWorkoutPanel).visibility)
                        assertEquals(activity.getString(R.string.workout_preview),
                            text(activity, R.id.connectionStatusTextView))
                        assertEquals(activity.getString(R.string.workout_time_format, 12, 34),
                            text(activity, R.id.workoutTimeTextView))
                        assertEquals(activity.getString(R.string.power_format, 180), text(activity, R.id.powerTextView))
                        for (id in listOf(R.id.dataScrollView, R.id.workoutStatusCard, R.id.workoutChartCard)) {
                            assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
                        }
                        assertDisabled(activity, R.id.stopWorkoutButton, R.id.pauseResumeWorkoutButton)
                    }
                }
                checkPreview()
                SystemClock.sleep(1500)
                checkPreview()
                scenario.recreate()
                checkPreview()
            }
            assertEquals("Preview must not change the saved preference", saved, preferences.all)
        }
        checkNormalLaunch(expectedCompactPreference = true)
    }

    @Test
    fun compactFlagShowsFrozenCompactPreviewWithInteractiveMetricsAndTabs() = withSavedPreference(false) { preferences ->
        val saved = preferences.all
        val preview = Intent(context, MainActivity::class.java)
            .putExtra("preview", true).putExtra("compact", true)
        ActivityScenario.launch<MainActivity>(preview).use { scenario ->
            fun checkPreview() {
                scenario.onActivity { activity ->
                    assertCommonPreview(activity)
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.compactWorkoutPanel).visibility)
                    for (id in listOf(R.id.toolbar, R.id.connectionStatusTextView, R.id.copyrightTextView,
                        R.id.dataScrollView, R.id.stopWorkoutButton, R.id.pauseResumeWorkoutButton)) {
                        assertEquals(View.GONE, activity.findViewById<View>(id).visibility)
                    }
                    assertEquals(activity.getString(R.string.workout_time_format, 12, 34),
                        text(activity, R.id.compactWorkoutTime))
                    assertEquals(activity.getString(R.string.workout_interval_format, 2, 4),
                        text(activity, R.id.compactIntervalText))
                    assertEquals(activity.getString(R.string.workout_resistance_format, 40),
                        text(activity, R.id.compactCurrentTarget))
                    assertDisabled(activity, R.id.compactPauseButton, R.id.compactStopButton, R.id.compactConnectionButton)
                    val connection = activity.findViewById<Button>(R.id.compactConnectionButton)
                    assertEquals("●", connection.text.toString())
                    assertEquals(activity.getString(R.string.workout_preview), connection.contentDescription.toString())
                }
            }
            checkPreview()
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.compactChartTabs)
                assertEquals(activity.getString(R.string.power_format, 180), text(activity, R.id.compactFocusedValue))
                tabs.getTabAt(2)!!.select()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.speedChart).visibility)
                for ((button, expected) in listOf(
                    R.id.compactMetricTwo to activity.getString(R.string.speed_format, 28f),
                    R.id.compactMetricThree to activity.getString(R.string.distance_format, 6.25),
                    R.id.compactMetricOne to activity.getString(R.string.cadence_format, 85),
                    R.id.compactMetricTwo to activity.getString(R.string.power_format, 180)
                )) {
                    activity.findViewById<Button>(button).performClick()
                    assertEquals(expected, text(activity, R.id.compactFocusedValue))
                    assertEquals(2, tabs.selectedTabPosition)
                }
            }
            SystemClock.sleep(1500)
            checkPreview()
            scenario.recreate()
            checkPreview()
        }
        assertEquals("Preview must not change the saved preference", saved, preferences.all)
        checkNormalLaunch(expectedCompactPreference = false)
    }

    private fun assertCommonPreview(activity: MainActivity) {
        assertNull(activity.supportActionBar?.subtitle)
        for (id in listOf(R.id.scanButton, R.id.setupWorkoutButton, R.id.disconnectButton,
            R.id.viewGpxButton, R.id.viewHistoryButton, R.id.compactLayoutCheckBox)) {
            assertEquals(View.GONE, activity.findViewById<View>(id).visibility)
        }
        for (id in listOf(R.id.powerChart, R.id.speedChart, R.id.workoutProfileChart)) {
            assertTrue(activity.findViewById<LineChart>(id).data.entryCount > 1)
        }
    }

    private fun assertDisabled(activity: MainActivity, vararg ids: Int) {
        for (id in ids) {
            val button = activity.findViewById<Button>(id)
            assertEquals(View.VISIBLE, button.visibility)
            assertFalse(button.isEnabled)
            assertFalse(button.hasOnClickListeners())
        }
    }

    private fun checkNormalLaunch(expectedCompactPreference: Boolean) {
        // The compact launch flag has no effect without preview=true.
        val intent = Intent(context, MainActivity::class.java).putExtra("compact", !expectedCompactPreference)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.disconnected), text(activity, R.id.connectionStatusTextView))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.workoutStatusCard).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scanButton).visibility)
                val checkbox = activity.findViewById<CheckBox>(R.id.compactLayoutCheckBox)
                assertEquals(View.VISIBLE, checkbox.visibility)
                assertEquals(expectedCompactPreference, checkbox.isChecked)
            }
        }
    }

    private fun withSavedPreference(enabled: Boolean, check: (SharedPreferences) -> Unit) {
        lateinit var preferences: SharedPreferences
        var previous: Boolean? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                preferences = activity.getPreferences(Context.MODE_PRIVATE)
                if (preferences.contains("compact_workout_layout")) {
                    previous = preferences.getBoolean("compact_workout_layout", false)
                }
                activity.findViewById<CheckBox>(R.id.compactLayoutCheckBox).isChecked = enabled
            }
        }
        try {
            check(preferences)
        } finally {
            preferences.edit().apply {
                val original = previous
                if (original == null) remove("compact_workout_layout") else putBoolean("compact_workout_layout", original)
            }.commit()
        }
    }

    private fun text(activity: MainActivity, id: Int) = activity.findViewById<TextView>(id).text.toString()
}
