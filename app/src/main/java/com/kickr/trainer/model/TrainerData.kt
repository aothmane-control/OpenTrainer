/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

data class TrainerData(
    val power: Int = 0,              // Instantaneous power in watts
    val hasPower: Boolean = false,   // The trainer reports power on this connection, so 0 W is a reading
    val speed: Float = 0f,           // Speed in km/h
    val cadence: Int = 0,            // Cadence in RPM
    val heartRate: Int = 0,          // Heart rate in BPM
    val distance: Float = 0f,        // Distance in km
    val timestamp: Long = System.currentTimeMillis()
)
