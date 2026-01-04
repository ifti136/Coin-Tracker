package com.cointracker.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    
    // We use a Box to layer: 
    // 1. The blurred background
    // 2. The sharp content on top
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        // Layer 1: Blurred Background
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        // Subtle white gradient for glass effect
                        listOf(
                            Color(0xFFFFFFFF).copy(alpha = 0.1f), 
                            Color(0xFFFFFFFF).copy(alpha = 0.05f)
                        )
                    )
                )
                .blur(10.dp) // Only this background layer is blurred
        )

        // Layer 2: Content (Sharp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    // A very slight tint to help contrast against the background
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                )
                .padding(18.dp),
            content = content
        )
    }
}