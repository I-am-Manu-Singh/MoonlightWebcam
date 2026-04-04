package com.example.moonlightwebcam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun EyeIcon(
        contentDescription: String?,
        filled: Boolean,
        tint: Color,
        modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val width = size.width
        val height = size.height

        // Eye Path properties
        val path =
                Path().apply {
                    moveTo(0f, height / 2)
                    quadraticBezierTo(width / 2, 0f, width, height / 2)
                    quadraticBezierTo(width / 2, height, 0f, height / 2)
                    close()
                }

        // Draw Eye Shape
        if (filled) {
            drawPath(path, color = tint, style = Fill)
            // Draw Pupil (Inverse color or just hole?)
            // If filled, maybe we want a solid eye.
            // Let's draw a white/transparent pupil if filled?
            // Or just solid shape.
            drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = height / 4, center = center)
        } else {
            drawPath(
                    path,
                    color = tint,
                    style =
                            Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                            )
            )
            // Draw Pupil Outline
            drawCircle(
                    color = tint,
                    radius = height / 4,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
