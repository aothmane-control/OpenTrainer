/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

import com.kickr.trainer.bluetooth.ResistanceRange

/**
 * A trainer the app has connected to, remembered so that resistance workouts can be set up in
 * its levels while no trainer is connected. Identified by its Bluetooth address.
 */
data class SavedTrainer(val address: String, val name: String, val resistanceRange: ResistanceRange)
