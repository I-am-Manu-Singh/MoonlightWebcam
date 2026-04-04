package com.example.moonlightwebcam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

@Composable
fun TabButton(
        text: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        activeColor: Color,
        modifier: Modifier = Modifier
) {
        val theme = LocalAppTheme.current

        Box(
                modifier =
                        modifier.clip(RoundedCornerShape(50))
                                .background(if (isSelected) activeColor else Color.Transparent)
                                .clickable(onClick = onClick)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
        ) {
                Text(
                        text = text,
                        color =
                                if (isSelected) theme.onPrimary
                                else theme.textPrimary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.subtitle2, // works everywhere
                        fontWeight = FontWeight.Bold
                )
        }
}

@Composable
fun GlassBox(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit
) {
        val theme = LocalAppTheme.current
        val isLight = theme == AppTheme.White
        // Dark Mode: Use proper Dark surface not text color
        val baseColor = if (isLight) Color(0xFFFAFAFA) else Color.Black
        val borderColor = if (isLight) Color(0xFFD1D1D1) else Color.White

        modifier
                .run {
                        if (isLight) {
                                shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(28.dp),
                                        spotColor = Color(0xFF404040),
                                        ambientColor = Color(0xFF404040)
                                )
                        } else this
                }
                .clip(RoundedCornerShape(28.dp)) // Slightly rounder
        Box(
                modifier = Modifier
                        .clip(RoundedCornerShape(28.dp)).hazeChild(
                                LocalHazeState.current,
                                HazeStyle(
                                        tint =
                                                if (theme == AppTheme.White)
                                                        HazeTint(
                                                                Color.White.copy(
                                                                        alpha = 0.4f
                                                                )
                                                        )
                                                else
                                                        HazeTint(
                                                                Color.White.copy(
                                                                        alpha =
                                                                                0.15f
                                                                )
                                                        ),
                                        blurRadius = 25.dp,
                                        backgroundColor =
                                                if (theme == AppTheme.White)
                                                        Color.White.copy(
                                                                alpha = 0.85f
                                                        )
                                                else Color.White.copy(alpha = 0.25f)
                                )
                        )
                                .border(
                                        width = 1.dp,
                                        brush =
                                                if (isLight) {
                                                        Brush.linearGradient(
                                                                listOf(
                                                                        borderColor.copy(
                                                                                alpha = 0.8f
                                                                        ),
                                                                        borderColor.copy(
                                                                                alpha = 0.4f
                                                                        )
                                                                )
                                                        )
                                                } else {
                                                        Brush.linearGradient(
                                                                listOf(
                                                                        Color.White.copy(
                                                                                alpha = 0.15f
                                                                        ),
                                                                        Color.White.copy(
                                                                                alpha = 0.05f
                                                                        )
                                                                )
                                                        )
                                                },
                                        shape = RoundedCornerShape(28.dp)
                                )
                                .run { if (onClick != null) clickable(onClick = onClick) else this }
                                .padding(18.dp),
                content = content
        )
}

@Composable
fun LiquidBackground(theme: AppTheme, content: @Composable BoxScope.() -> Unit) {
        // Use a dark base for Dark themes, but a lighter base for Light themes
        val baseColor = if (theme == AppTheme.White) Color(0xFFFAFAFA) else Color(0xFF050505)
        Box(modifier = Modifier.fillMaxSize().background(baseColor)) {
                Box(
                        modifier =
                                Modifier.size(600.dp)
                                        .offset(x = (-150).dp, y = (-150).dp)
                                        .background(
                                                Brush.radialGradient(
                                                        colors =
                                                                listOf(
                                                                        theme.backgroundStart.copy(
                                                                                alpha = 0.25f
                                                                        ),
                                                                        Color.Transparent
                                                                )
                                                )
                                        )
                )
                Box(
                        modifier =
                                Modifier.align(Alignment.BottomEnd)
                                        .size(500.dp)
                                        .offset(x = 100.dp, y = 100.dp)
                                        .background(
                                                Brush.radialGradient(
                                                        colors =
                                                                listOf(
                                                                        theme.backgroundEnd.copy(
                                                                                alpha = 0.2f
                                                                        ),
                                                                        Color.Transparent
                                                                )
                                                )
                                        )
                )
                content()
        }
}

@Composable
fun EyeIcon(contentDescription: String?, filled: Boolean, tint: Color) {
        Icon(
                if (filled) Icons.Filled.Visibility else Icons.Filled.Visibility, // Simplify
                contentDescription = contentDescription,
                tint = tint
        )
}
