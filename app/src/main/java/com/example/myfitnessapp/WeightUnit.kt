package com.example.myfitnessapp

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.edit
import kotlin.math.roundToLong

// The preferences file that holds every setting.
const val SETTINGS_FILE = "settings"

private const val KG_PER_LB = 0.45359237

// Weights are always stored in kilograms, so switching the unit changes how every workout is
// shown, past ones included.
enum class WeightUnit(val label: String) {
    LBS("lbs"),
    KG("kg");

    // Rounded to one decimal, so 135 lbs shows as 135 again instead of 134.99999.
    fun fromKg(kg: Double): Double {
        val value = if (this == LBS) kg / KG_PER_LB else kg
        return (value * 10).roundToLong() / 10.0
    }

    fun toKg(value: Double): Double = if (this == LBS) value * KG_PER_LB else value
}

object WeightUnitPrefs {
    private const val KEY_UNIT = "weight_unit"

    fun load(context: Context): WeightUnit {
        val savedName = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_UNIT, null)
        return WeightUnit.entries.firstOrNull { it.name == savedName } ?: WeightUnit.LBS
    }

    fun save(context: Context, unit: WeightUnit) {
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit {
            putString(KEY_UNIT, unit.name)
        }
    }
}

// Provided in MainActivity.
val LocalWeightUnit = compositionLocalOf { WeightUnit.LBS }
