/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.bluetooth

/**
 * A trainer, identified by its Bluetooth address, with the resistance levels it accepts.
 * The app saves each one that connects, so workouts can be set up in its levels without it.
 */
data class Trainer(val address: String, val name: String, val resistanceRange: ResistanceRange)
