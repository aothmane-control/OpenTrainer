package com.kickr.trainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kickr.trainer.bluetooth.ResistanceRange
import com.kickr.trainer.bluetooth.Trainer
import com.kickr.trainer.utils.SavedTrainerStorage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedTrainerStorageTest {
    // Test APK's own preferences, never the user's saved trainers.
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val preferences = context.getSharedPreferences("saved_trainers", 0)
    private val storage = SavedTrainerStorage(context)
    private val kickr = Trainer("AA:BB:CC:DD:EE:01", "KICKR CORE 1234", ResistanceRange(0, 100, 1))
    private val other = Trainer("AA:BB:CC:DD:EE:02", "Another trainer", ResistanceRange(10, 240, 10))

    @Before
    fun setUp() { preferences.edit().clear().commit() }

    @After
    fun tearDown() { preferences.edit().clear().commit() }

    @Test
    fun nothingIsSavedAtFirst() {
        assertTrue(storage.loadAll().isEmpty())
        assertNull(storage.lastConnected)
    }

    @Test
    fun savesTrainersSortedByNameAndRemembersTheLastOneConnected() {
        storage.save(kickr)
        storage.save(other)
        assertEquals(listOf(other, kickr), SavedTrainerStorage(context).loadAll())
        assertEquals(other, storage.lastConnected)
        storage.save(kickr)
        assertEquals(kickr, storage.lastConnected)
    }

    @Test
    fun aReconnectedTrainerIsUpdatedNotAddedAgain() {
        storage.save(kickr)
        storage.save(other)
        val renamed = kickr.copy(name = "KICKR CORE", resistanceRange = ResistanceRange(0, 200, 2))
        storage.save(renamed)
        assertEquals(listOf(other, renamed), storage.loadAll())
    }

    @Test
    fun skipsUnreadableTrainersAndUnusableRanges() {
        preferences.edit().putString("trainers", """{
            "AA:BB:CC:DD:EE:01":{"name":"KICKR CORE 1234","minimum":0,"maximum":100,"increment":1},
            "AA:BB:CC:DD:EE:03":{"minimum":0,"maximum":100,"increment":1},
            "AA:BB:CC:DD:EE:04":{"name":"Empty range","minimum":100,"maximum":100,"increment":1},
            "AA:BB:CC:DD:EE:05":{"name":"No increment","minimum":0,"maximum":100,"increment":0},
            "AA:BB:CC:DD:EE:06":{"name":"No levels"},
            "AA:BB:CC:DD:EE:07":false
        }""").putString("last_connected", "AA:BB:CC:DD:EE:06").commit()
        val unnamed = Trainer("AA:BB:CC:DD:EE:03", "AA:BB:CC:DD:EE:03", ResistanceRange(0, 100, 1))
        // Like a trainer reporting an increment of 0
        val noIncrement = Trainer("AA:BB:CC:DD:EE:05", "No increment", ResistanceRange(0, 100, 1))
        assertEquals(listOf(unnamed, kickr, noIncrement), storage.loadAll())
        assertNull(storage.lastConnected)
    }

    @Test
    fun anUnreadableListIsReplacedWhenATrainerConnects() {
        preferences.edit().putString("trainers", "not json").commit()
        assertTrue(storage.loadAll().isEmpty())
        storage.save(kickr)
        assertEquals(listOf(kickr), storage.loadAll())
        assertEquals(kickr, storage.lastConnected)
    }
}
