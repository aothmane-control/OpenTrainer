/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.kickr.trainer.model.RiderBikeSettings
import com.kickr.trainer.model.RiderBikeSettings.Parameter
import com.kickr.trainer.utils.RiderBikeSettingsStorage
import com.kickr.trainer.utils.applyEdgeToEdgeInsets
import java.math.BigDecimal

class RiderBikeSettingsActivity : AppCompatActivity() {

    private lateinit var storage: RiderBikeSettingsStorage
    private lateinit var fields: Map<Parameter, TextInputLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_bike_settings)
        applyEdgeToEdgeInsets()

        storage = RiderBikeSettingsStorage(this)
        fields = mapOf(
            Parameter.RIDER_WEIGHT to findViewById(R.id.riderWeightLayout),
            Parameter.BIKE_WEIGHT to findViewById(R.id.bikeWeightLayout),
            Parameter.WHEEL_CIRCUMFERENCE to findViewById(R.id.wheelCircumferenceLayout),
            Parameter.ROLLING_RESISTANCE to findViewById(R.id.rollingResistanceLayout),
            Parameter.WIND_RESISTANCE to findViewById(R.id.windResistanceLayout),
            Parameter.WIND_SPEED to findViewById(R.id.windSpeedLayout)
        )
        // After e.g. a rotation the fields restore what was typed, which may not be saved yet
        if (savedInstanceState == null) show(storage.load())

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        // Only fills in the defaults; they are kept once saved
        findViewById<Button>(R.id.resetButton).setOnClickListener { show(RiderBikeSettings.DEFAULT) }
        findViewById<Button>(R.id.saveButton).setOnClickListener { save() }
    }

    private fun show(settings: RiderBikeSettings) {
        fields.forEach { (parameter, layout) ->
            layout.error = null
            layout.editText?.setText(format(settings.valueOf(parameter)))
        }
    }

    private fun save() {
        val values = fields.mapNotNull { (parameter, layout) ->
            // Accept the decimal comma of many European keyboards
            val value = layout.editText?.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
            if (value != null && value in parameter.range) {
                layout.error = null
                parameter to value
            } else {
                layout.error = getString(
                    R.string.value_out_of_range,
                    format(parameter.range.start), format(parameter.range.endInclusive)
                )
                null
            }
        }.toMap()
        if (values.size < fields.size) return

        storage.save(RiderBikeSettings.of(values))
        Toast.makeText(this, R.string.rider_bike_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    // Plain number without trailing zeros, e.g. 75 or 0.004
    private fun format(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
