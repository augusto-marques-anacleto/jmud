package br.com.augusto.jmud.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import kotlin.math.roundToInt

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    min: Float,
    max: Float,
    step: Float,
    modifier: Modifier = Modifier
) {
    val stepCount = ((max - min) / step).roundToInt() - 1
    val current = value.coerceIn(min, max)

    fun snap(raw: Float): Float {
        val snapped = min + ((raw - min) / step).roundToInt() * step
        return snapped.coerceIn(min, max)
    }

    Box(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(current, min..max, stepCount)
            setProgress { target ->
                val newValue = when {
                    target > current -> current + step
                    target < current -> current - step
                    else -> current
                }.coerceIn(min, max)
                if (newValue != current) {
                    onValueChange(snap(newValue))
                    true
                } else {
                    false
                }
            }
        }
    ) {
        Slider(
            value = current,
            onValueChange = { onValueChange(snap(it)) },
            valueRange = min..max,
            steps = stepCount,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
