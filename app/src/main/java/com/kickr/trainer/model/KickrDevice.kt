/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

import android.bluetooth.BluetoothDevice

data class KickrDevice(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int
)
