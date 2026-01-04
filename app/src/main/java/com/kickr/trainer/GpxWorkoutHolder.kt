package com.kickr.trainer

import com.kickr.trainer.model.GpxTrack

// Singleton to hold GPX track data during workout
object GpxWorkoutHolder {
    var currentTrack: GpxTrack? = null
}
