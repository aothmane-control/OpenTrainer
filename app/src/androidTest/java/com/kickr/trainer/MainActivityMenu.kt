package com.kickr.trainer

import android.view.MenuItem
import androidx.appcompat.widget.Toolbar

// The workout layout item in the overflow menu, as the menu was last prepared
internal val MainActivity.compactLayoutMenuItem: MenuItem
    get() = findViewById<Toolbar>(R.id.toolbar).menu.findItem(R.id.action_compact_layout)
