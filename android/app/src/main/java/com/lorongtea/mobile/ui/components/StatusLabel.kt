package com.lorongtea.mobile.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal enum class StatusTone { NEUTRAL, ACTIVE, ATTENTION }

@Composable
internal fun StatusLabel(text: String, tone: StatusTone) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        StatusTone.NEUTRAL -> colors.surfaceVariant to colors.onSurfaceVariant
        StatusTone.ACTIVE -> colors.primaryContainer to colors.onPrimaryContainer
        StatusTone.ATTENTION -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
    }
}
