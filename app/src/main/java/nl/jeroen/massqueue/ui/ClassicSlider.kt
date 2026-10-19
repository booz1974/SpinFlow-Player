package nl.jeroen.massqueue.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Slider met het uiterlijk van Material3 1.2: rond bolletje (20 dp) en een dunne, afgeronde
 * balk (4 dp). Material3 1.4 tekent standaard een staafje met een dikke balk met gat; dit
 * houdt de app er hetzelfde uitzien na het bijwerken van de bibliotheken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        interactionSource = interactionSource,
        thumb = {
            Spacer(
                Modifier
                    .size(20.dp)
                    .shadow(1.dp, CircleShape, clip = false)
                    .background(thumbColor, CircleShape)
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val y = size.height / 2
                val stroke = size.height
                drawLine(inactiveTrackColor, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                drawLine(activeTrackColor, Offset(0f, y), Offset(size.width * fraction, y), stroke, StrokeCap.Round)
            }
        }
    )
}
