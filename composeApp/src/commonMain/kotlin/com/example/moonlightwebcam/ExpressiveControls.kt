package com.example.moonlightwebcam

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Composable
fun ExpressiveSwitch(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        thumbColor: Color,
        trackColor: Color = Color.Gray.copy(alpha = 0.3f),
        checkedTrackColor: Color = thumbColor.copy(alpha = 0.5f)
) {
    val haptic = LocalHapticFeedback.current
    val finalTrackColor by animateColorAsState(if (checked) checkedTrackColor else trackColor)
    val thumbOffset by animateDpAsState(if (checked) 28.dp else 4.dp, animationSpec = tween(300))
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Elastic thumb size effect
    val thumbSize by animateDpAsState(if (isPressed) 20.dp else 24.dp)

    Box(
            modifier =
                    Modifier.width(56.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(finalTrackColor)
                            .clickable(interactionSource = interactionSource, indication = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCheckedChange(!checked)
                            }
                            .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
    ) {
        Box(
                modifier =
                        Modifier.offset(x = thumbOffset)
                                .size(thumbSize)
                                .clip(CircleShape)
                                .background(Color.White)
        )
    }
}

@Composable
fun ExpressiveSlider(
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
        thumbColor: Color,
        trackColor: Color = thumbColor.copy(alpha = 0.3f)
) {
    val haptic = LocalHapticFeedback.current
    val currentSteps =
            remember(value) {
                (value /
                                ((valueRange.endInclusive - valueRange.start) /
                                        (if (steps > 0) steps else 100)))
                        .toInt()
            }

    // Haptic on step change if discrete
    LaunchedEffect(currentSteps) {
        if (steps > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Slider(
            value = value,
            onValueChange = {
                onValueChange(it)
                // Continuous feedback logic if desired, but can be too noisy
            },
            valueRange = valueRange,
            steps = steps,
            colors =
                    SliderDefaults.colors(
                            thumbColor = thumbColor,
                            activeTrackColor = thumbColor,
                            inactiveTrackColor = trackColor
                    ),
            modifier = Modifier.fillMaxWidth().height(40.dp) // Slightly taller touch target
    )
}
