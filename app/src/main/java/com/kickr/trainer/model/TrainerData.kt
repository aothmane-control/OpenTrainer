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
) {
    /** The speed in m/s for counting distance; readings of 100 km/h or more are glitches and count as 0. */
    val plausibleSpeedMps: Double
        get() = if (speed > 0 && speed < MAX_PLAUSIBLE_SPEED_KMH) speed / 3.6 else 0.0

    /** Metres covered at this speed until the [next] message; nothing if the clock went back. */
    fun distanceUntil(next: TrainerData): Double =
        plausibleSpeedMps * (next.timestamp - timestamp).coerceAtLeast(0) / 1000.0

    private companion object {
        const val MAX_PLAUSIBLE_SPEED_KMH = 100f
    }
}
