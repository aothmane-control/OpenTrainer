/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer

import android.content.Context
import androidx.annotation.ColorRes
import com.kickr.trainer.bluetooth.KickrBluetoothService.ConnectionState

/** Status color shared by the classic status text and the compact connection button. */
@ColorRes
internal fun ConnectionState.statusColorRes(): Int = when (this) {
    ConnectionState.Connected -> android.R.color.holo_green_dark
    ConnectionState.Connecting -> android.R.color.holo_blue_dark
    else -> android.R.color.holo_red_dark
}

internal fun ConnectionState.statusLabel(context: Context): String = when (this) {
    ConnectionState.Connected -> context.getString(R.string.connected)
    ConnectionState.Connecting -> context.getString(R.string.connecting)
    ConnectionState.Disconnected -> context.getString(R.string.disconnected)
    is ConnectionState.Error -> message
}
