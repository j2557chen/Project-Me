package com.example.myfitnessapp

import com.example.myfitnessapp.ui.theme.APP_FONT_FEATURES
import com.example.myfitnessapp.ui.theme.AppFont
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// The user's profile: age, height and weight, plus free-form goals. Height and weight are
// stored in centimetres and kilograms, and shown in feet and inches with pounds, or centimetres
// with kilograms, following the weight unit setting.
// ---------------------------------------------------------------------------

private const val PROFILE_FILE = "profile"
private const val CM_PER_INCH = 2.54

// A height in centimetres as whole feet and inches, rounded to the nearest inch.
fun cmToFeetInches(cm: Double): Pair<Int, Int> {
    val totalInches = (cm / CM_PER_INCH).roundToInt()
    return totalInches / 12 to totalInches % 12
}

fun feetInchesToCm(feet: Int, inches: Int): Double = (feet * 12 + inches) * CM_PER_INCH

// A number for a text box: whole numbers without ".0", others to one decimal.
private fun numberText(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

// Keeps digits, and one decimal point if decimals are allowed.
private fun cleanNumber(text: String, decimals: Boolean): String {
    var seenPoint = false
    return text.filter { c ->
        when {
            c.isDigit() -> true
            c == '.' && decimals && !seenPoint -> { seenPoint = true; true }
            else -> false
        }
    }
}

internal class ProfilePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PROFILE_FILE, Context.MODE_PRIVATE)

    private fun double(key: String): Double? =
        if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null

    private fun putDouble(key: String, value: Double?) = prefs.edit {
        if (value == null) remove(key) else putFloat(key, value.toFloat())
    }

    var age: Int?
        get() = if (prefs.contains("age")) prefs.getInt("age", 0) else null
        set(value) = prefs.edit { if (value == null) remove("age") else putInt("age", value) }

    var heightCm: Double?
        get() = double("height_cm")
        set(value) = putDouble("height_cm", value)

    var weightKg: Double?
        get() = double("weight_kg")
        set(value) = putDouble("weight_kg", value)

    var goals: String
        get() = prefs.getString("goals", "").orEmpty()
        set(value) = prefs.edit { putString("goals", value) }
}

// The Profile tab. The gear in the top right opens Settings.
@Composable
fun ProfileScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { ProfilePrefs(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(
            title = Screen.Profile.label,
            end = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )

        Spacer(modifier = Modifier.height(CARD_GAP))
        AboutYouCard(prefs)
        // With Track Weight on, a chart of the body weight log drops open from here.
        if (TrackWeight.enabled) {
            Spacer(modifier = Modifier.height(CARD_GAP))
            WeightOverTimeCard(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))
        }
        Spacer(modifier = Modifier.height(CARD_GAP))
        GoalsCard(prefs)
        Spacer(modifier = Modifier.height(CARD_GAP))
    }
}

// What's typed in the About You boxes, in the current units. It starts from what's saved,
// and save() stores it back in centimetres and kilograms.
private class AboutYouDraft(prefs: ProfilePrefs, val unit: WeightUnit) {
    var age by mutableStateOf(prefs.age?.toString().orEmpty())
    var feet by mutableStateOf(prefs.heightCm?.let { cmToFeetInches(it).first.toString() }.orEmpty())
    var inches by mutableStateOf(prefs.heightCm?.let { cmToFeetInches(it).second.toString() }.orEmpty())
    var cm by mutableStateOf(prefs.heightCm?.let(::numberText).orEmpty())
    var weight by mutableStateOf(prefs.weightKg?.let { numberText(unit.fromKg(it)) }.orEmpty())

    fun save(prefs: ProfilePrefs) {
        prefs.age = age.toIntOrNull()
        prefs.heightCm = if (unit == WeightUnit.LBS) {
            val f = feet.toIntOrNull()
            val i = inches.toIntOrNull()
            if (f == null && i == null) null else feetInchesToCm(f ?: 0, i ?: 0)
        } else {
            cm.toDoubleOrNull()
        }
        prefs.weightKg = weight.toDoubleOrNull()?.let(unit::toKg)
    }
}

// Age, height and weight, each saved as soon as it's typed.
@Composable
private fun AboutYouCard(prefs: ProfilePrefs) {
    val unit = LocalWeightUnit.current
    // Filled again from the saved values if the unit setting changes.
    val draft = remember(unit) { AboutYouDraft(prefs, unit) }

    // With Track Weight on, a new weight typed here is logged for today too, once typing stops.
    // A weight the same as the one already saved isn't logged again.
    val context = LocalContext.current
    if (TrackWeight.enabled) {
        val savedKg = remember(unit) { prefs.weightKg }
        LaunchedEffect(draft.weight) {
            val kg = draft.weight.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toKg) ?: return@LaunchedEffect
            if (savedKg != null && unit.fromKg(savedKg) == unit.fromKg(kg)) return@LaunchedEffect
            delay(1000)
            logBodyWeight(context, kg)
        }
    }

    Card(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "About You", fontSize = AppText.heading, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AboutYouFields(draft, onChange = { draft.save(prefs) })
        }
    }
}

// The Age, Height and Weight rows. onChange runs after each edit.
@Composable
private fun AboutYouFields(draft: AboutYouDraft, onChange: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileRow("Age") {
            NumberBox(draft.age, onChange = {
                draft.age = cleanNumber(it, decimals = false).take(3)
                onChange()
            })
            UnitLabel("years")
        }

        ProfileRow("Height") {
            if (draft.unit == WeightUnit.LBS) {
                NumberBox(draft.feet, width = 48, onChange = {
                    draft.feet = cleanNumber(it, decimals = false).take(1)
                    onChange()
                })
                // Only as wide as "ft", so the inches box sits just after it.
                UnitLabel("ft", fixedWidth = false)
                Spacer(modifier = Modifier.width(10.dp))
                NumberBox(draft.inches, width = 48, onChange = {
                    draft.inches = cleanNumber(it, decimals = false).take(2)
                    onChange()
                })
                UnitLabel("in")
            } else {
                NumberBox(draft.cm, onChange = {
                    draft.cm = cleanNumber(it, decimals = true).take(5)
                    onChange()
                })
                UnitLabel("cm")
            }
        }

        ProfileRow("Weight") {
            NumberBox(draft.weight, onChange = {
                draft.weight = cleanNumber(it, decimals = true).take(5)
                onChange()
            })
            UnitLabel(draft.unit.label)
        }
    }
}

// Asks for age, height and weight when none are saved. Skip closes it without saving; Save
// stores what was typed.
@Composable
fun AboutYouPrompt(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ProfilePrefs(context) }
    val unit = LocalWeightUnit.current
    val draft = remember(unit) { AboutYouDraft(prefs, unit) }

    AlertDialog(
        // The back gesture skips, but a stray tap outside doesn't close it.
        onDismissRequest = onDone,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("About You") },
        text = {
            Column {
                Text(
                    text = "Add your age, height and weight. You can change them any time on the Profile tab.",
                    fontSize = AppText.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                AboutYouFields(draft)
            }
        },
        // One row across the dialog, so Skip sits bottom left and Save bottom right.
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDone) { Text("Skip") }
                TextButton(onClick = {
                    draft.save(prefs)
                    onDone()
                }) { Text("Save") }
            }
        }
    )
}

// True when no age, height or weight has been saved.
fun isAboutYouEmpty(context: Context): Boolean {
    val prefs = ProfilePrefs(context)
    return prefs.age == null && prefs.heightCm == null && prefs.weightKg == null
}

// A free-form notepad for the user's goals, saved as they type.
@Composable
private fun GoalsCard(prefs: ProfilePrefs) {
    var goals by rememberSaveable { mutableStateOf(prefs.goals) }
    val colors = MaterialTheme.colorScheme

    Card(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Goals", fontSize = AppText.heading, color = colors.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = goals,
                onValueChange = {
                    goals = it
                    prefs.goals = it
                },
                textStyle = TextStyle(fontSize = AppText.body, color = colors.onSurface, fontFamily = AppFont, fontFeatureSettings = APP_FONT_FEATURES),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                decorationBox = { field ->
                    Box {
                        if (goals.isEmpty()) {
                            Text(
                                text = "Write your goals here",
                                fontSize = AppText.body,
                                color = colors.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        field()
                    }
                }
            )
        }
    }
}

// A label on the left, and boxes with their units on the right.
@Composable
private fun ProfileRow(label: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = AppText.subheading,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        content()
    }
}

@Composable
private fun UnitLabel(text: String, fixedWidth: Boolean = true) {
    Text(
        text = text,
        fontSize = AppText.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // The fixed width lines up the boxes on every row, whatever the unit.
        modifier = Modifier
            .padding(start = 6.dp)
            .then(if (fixedWidth) Modifier.width(48.dp) else Modifier),
        maxLines = 1
    )
}

// A small outlined box for a number, centred.
@Composable
private fun NumberBox(value: String, onChange: (String) -> Unit, width: Int = 72) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = AppText.subheading,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            fontFamily = AppFont,
            fontFeatureSettings = APP_FONT_FEATURES
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier
            .width(width.dp)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp)
    )
}
