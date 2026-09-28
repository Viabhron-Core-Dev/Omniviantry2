package com.example.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.UUID

enum class WidgetType {
    CHOICE,
    SLIDER,
    TOGGLE,
    CONFIRM
}

data class InteractiveWidgetSpec(
    val id: String = UUID.randomUUID().toString(),
    val type: WidgetType,
    val title: String,
    val description: String = "",
    val options: List<String> = emptyList(),
    val minVal: Float = 0f,
    val maxVal: Float = 1f,
    val step: Float = 0.1f,
    val defaultFloat: Float = 0.5f,
    val defaultBool: Boolean = false,
    val confirmText: String = "Confirm",
    val cancelText: String = "Cancel"
)

object InteractiveWidgetParser {
    private val CHOICE_REGEX = Regex("<widget:choice\\s+title=\"([^\"]+)\"\\s+options=\"([^\"]+)\"(?:\\s+desc=\"([^\"]*)\")?\\s*/>", RegexOption.IGNORE_CASE)
    private val SLIDER_REGEX = Regex("<widget:slider\\s+title=\"([^\"]+)\"(?:\\s+min=\"([^\"]+)\")?(?:\\s+max=\"([^\"]+)\")?(?:\\s+step=\"([^\"]+)\")?(?:\\s+default=\"([^\"]+)\")?\\s*/>", RegexOption.IGNORE_CASE)
    private val TOGGLE_REGEX = Regex("<widget:toggle\\s+title=\"([^\"]+)\"(?:\\s+default=\"([^\"]+)\")?(?:\\s+desc=\"([^\"]*)\")?\\s*/>", RegexOption.IGNORE_CASE)
    private val CONFIRM_REGEX = Regex("<widget:confirm\\s+title=\"([^\"]+)\"(?:\\s+message=\"([^\"]*)\")?\\s*/>", RegexOption.IGNORE_CASE)

    fun parseWidgets(rawText: String): List<InteractiveWidgetSpec> {
        val widgets = mutableListOf<InteractiveWidgetSpec>()

        // 1. Choices
        CHOICE_REGEX.findAll(rawText).forEach { match ->
            val title = match.groupValues[1]
            val opts = match.groupValues[2].split(",").map { it.trim() }.filter { it.isNotBlank() }
            val desc = match.groupValues.getOrNull(3) ?: ""
            widgets.add(InteractiveWidgetSpec(type = WidgetType.CHOICE, title = title, options = opts, description = desc))
        }

        // 2. Sliders
        SLIDER_REGEX.findAll(rawText).forEach { match ->
            val title = match.groupValues[1]
            val min = match.groupValues.getOrNull(2)?.toFloatOrNull() ?: 0f
            val max = match.groupValues.getOrNull(3)?.toFloatOrNull() ?: 1f
            val step = match.groupValues.getOrNull(4)?.toFloatOrNull() ?: 0.1f
            val def = match.groupValues.getOrNull(5)?.toFloatOrNull() ?: ((min + max) / 2f)
            widgets.add(InteractiveWidgetSpec(type = WidgetType.SLIDER, title = title, minVal = min, maxVal = max, step = step, defaultFloat = def))
        }

        // 3. Toggles
        TOGGLE_REGEX.findAll(rawText).forEach { match ->
            val title = match.groupValues[1]
            val def = match.groupValues.getOrNull(2)?.toBooleanStrictOrNull() ?: false
            val desc = match.groupValues.getOrNull(3) ?: ""
            widgets.add(InteractiveWidgetSpec(type = WidgetType.TOGGLE, title = title, defaultBool = def, description = desc))
        }

        // 4. Confirms
        CONFIRM_REGEX.findAll(rawText).forEach { match ->
            val title = match.groupValues[1]
            val msg = match.groupValues.getOrNull(2) ?: ""
            widgets.add(InteractiveWidgetSpec(type = WidgetType.CONFIRM, title = title, description = msg))
        }

        return widgets
    }

    /**
     * Parses widget specification from tool JSON arguments if the AI invoked `ask_user_input` or `prompt_parameters`.
     */
    fun parseFromToolArgs(toolName: String, argsJson: String?): InteractiveWidgetSpec? {
        if (argsJson.isNullOrBlank()) return null
        return try {
            val json = JSONObject(argsJson)
            val title = json.optString("title", if (toolName.contains("choice")) "Choose Option" else "User Input")
            val desc = json.optString("description", "")

            if (json.has("options") || toolName.contains("choice")) {
                val opts = mutableListOf<String>()
                val arr = json.optJSONArray("options")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        opts.add(arr.getString(i))
                    }
                } else if (json.has("options")) {
                    opts.addAll(json.getString("options").split(",").map { it.trim() })
                }
                if (opts.isNotEmpty()) {
                    InteractiveWidgetSpec(type = WidgetType.CHOICE, title = title, description = desc, options = opts)
                } else null
            } else if (json.has("min") || json.has("max") || toolName.contains("slider")) {
                val min = json.optDouble("min", 0.0).toFloat()
                val max = json.optDouble("max", 1.0).toFloat()
                val step = json.optDouble("step", 0.1).toFloat()
                val def = json.optDouble("default", ((min + max) / 2.0)).toFloat()
                InteractiveWidgetSpec(type = WidgetType.SLIDER, title = title, description = desc, minVal = min, maxVal = max, step = step, defaultFloat = def)
            } else if (json.has("toggle") || toolName.contains("toggle") || toolName.contains("confirm")) {
                InteractiveWidgetSpec(type = WidgetType.CONFIRM, title = title, description = desc)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cleans XML widget markup from display text.
     */
    fun stripWidgetMarkup(text: String): String {
        return text
            .replace(CHOICE_REGEX, "")
            .replace(SLIDER_REGEX, "")
            .replace(TOGGLE_REGEX, "")
            .replace(CONFIRM_REGEX, "")
            .trim()
    }
}

/**
 * Proactive In-Chat M3 Parameter Widget (Mini-Phase 5 Deliverable 1).
 * Renders native interactive chips, sliders, toggles, or confirmation buttons inside chat bubbles.
 * Dispatches concise, token-efficient responses directly back to the active agent pipeline.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InteractiveWidgetCard(
    spec: InteractiveWidgetSpec,
    onAnswerSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var answeredResult by remember { mutableStateOf<String?>(null) }
    var currentSliderValue by remember { mutableFloatStateOf(spec.defaultFloat) }
    var currentToggleValue by remember { mutableStateOf(spec.defaultBool) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Widget Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (spec.type) {
                                WidgetType.CHOICE -> Icons.Default.Tune
                                WidgetType.SLIDER -> Icons.Default.LinearScale
                                WidgetType.TOGGLE -> Icons.Default.ToggleOn
                                WidgetType.CONFIRM -> Icons.Default.CheckCircleOutline
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (spec.description.isNotBlank()) {
                        Text(
                            text = spec.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // If already answered, show compact receipt badge
            if (answeredResult != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Selected: ${answeredResult}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Interactive Controls
                when (spec.type) {
                    WidgetType.CHOICE -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            spec.options.forEach { option ->
                                OutlinedButton(
                                    onClick = {
                                        answeredResult = option
                                        onAnswerSubmitted("Selected: $option")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(option, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    WidgetType.SLIDER -> {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "Value: %.2f", currentSliderValue),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "[${spec.minVal} - ${spec.maxVal}]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Slider(
                                value = currentSliderValue,
                                onValueChange = { currentSliderValue = it },
                                valueRange = spec.minVal..spec.maxVal,
                                steps = if (spec.step > 0f) ((spec.maxVal - spec.minVal) / spec.step).toInt() - 1 else 0
                            )
                            Button(
                                onClick = {
                                    val formatted = String.format(java.util.Locale.US, "%.2f", currentSliderValue)
                                    answeredResult = formatted
                                    onAnswerSubmitted("${spec.title}: $formatted")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text("Apply Value")
                            }
                        }
                    }

                    WidgetType.TOGGLE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (currentToggleValue) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = currentToggleValue,
                                onCheckedChange = { currentToggleValue = it }
                            )
                            Button(
                                onClick = {
                                    val label = if (currentToggleValue) "Enabled" else "Disabled"
                                    answeredResult = label
                                    onAnswerSubmitted("${spec.title}: $label")
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Confirm")
                            }
                        }
                    }

                    WidgetType.CONFIRM -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    answeredResult = "Cancelled"
                                    onAnswerSubmitted("${spec.title}: Cancelled")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(spec.cancelText)
                            }
                            Button(
                                onClick = {
                                    answeredResult = "Confirmed"
                                    onAnswerSubmitted("${spec.title}: Confirmed")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(spec.confirmText)
                            }
                        }
                    }
                }
            }
        }
    }
}
