package com.example.myfitnessapp

// ---------------------------------------------------------------------------
// Suggesting a heavier weight. When last time's heaviest set was easy (RPE under 8, or more than
// 2 reps in reserve) for more than 12 reps, the sets done at that weight are suggested one step
// heavier this time: 5 lbs, or 2.5 kg.
// ---------------------------------------------------------------------------

// Last time's heaviest weight, and the weight suggested in its place.
data class WeightIncrease(val fromKg: Double, val toKg: Double)

private const val STEP_LBS = 5.0
private const val STEP_KG = 2.5

fun suggestedIncrease(previous: List<ExerciseSet>, effortType: EffortType, unit: WeightUnit): WeightIncrease? {
    // The heaviest set, not counting warm-ups; the most reps at that weight if there's a tie.
    val heaviest = previous
        .filter { it.setType != SetType.WARM_UP && (it.weight ?: 0.0) > 0 }
        .maxWithOrNull(compareBy({ it.weight }, { it.reps ?: 0.0 }))
        ?: return null
    val reps = heaviest.reps ?: return null
    val effort = heaviest.effort ?: return null
    val easy = when (effortType) {
        EffortType.RPE -> effort < 8
        EffortType.RIR -> effort > 2
    }
    if (!easy || reps <= 12) return null

    val from = heaviest.weight!!
    val step = if (unit == WeightUnit.LBS) STEP_LBS else STEP_KG
    return WeightIncrease(fromKg = from, toKg = unit.toKg(unit.fromKg(from) + step))
}
