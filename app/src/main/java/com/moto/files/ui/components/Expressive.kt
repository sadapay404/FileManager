package com.moto.files.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(top = 24.dp, bottom = 12.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
fun StorageMeter(usedBytes: Long, totalBytes: Long, modifier: Modifier = Modifier) {
    val fraction = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val progress = animateFloatAsState(fraction, spring(), label = "Storage usage")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Internal storage", style = MaterialTheme.typography.titleMedium)
            Text("${(fraction * 100).toInt()}% used", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { progress.value }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Text("${formatBytes(usedBytes)} of ${formatBytes(totalBytes)}", style = MaterialTheme.typography.bodySmall)
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1) "%.1f GB".format(java.util.Locale.US, gb) else "%.1f MB".format(java.util.Locale.US, bytes / 1_048_576.0)
}
