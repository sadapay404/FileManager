package com.moto.files.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.moto.files.theme.MotoSpring

/** Pill button whose corner radius morphs between circle and full pill with a spring. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true
) {
    val r by animateFloatAsState(
        if (expanded) 999f else 18f, MotoSpring, label = "pill")
    Surface(
        shape = RoundedCornerShape(r.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clip(RoundedCornerShape(r.dp)).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Slider with an elongated, expressive thumb and a spring-animated fill. */
@Composable
fun ExpressiveSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier
) {
    val frac = (value - range.start) / (range.endInclusive - range.start)
    val animated by animateFloatAsState(frac, MotoSpring, label = "slider")
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        modifier = modifier,
        thumb = {
            Box(
                Modifier
                    .graphicsLayer { scaleX = 1.5f }
                    .size(22.dp, 14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        },
        track = {
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    )
}

/** Animated used/free storage bar. */
@Composable
fun StorageBar(used: Long, free: Long, modifier: Modifier = Modifier) {
    val frac = used.toFloat() / (used + free).coerceAtLeast(1L)
    val animated by animateFloatAsState(frac, MotoSpring, label = "storage")
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Used ${human(used)}", style = MaterialTheme.typography.bodyMedium)
            Text("Free ${human(free)}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(animated).fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Spring-animated Files/Gallery segmented toggle. */
@Composable
fun ModeSwitch(
    gallery: Boolean,
    onFiles: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t by animateFloatAsState(if (gallery) 1f else 0f, MotoSpring, label = "mode")
    Box(
        modifier.height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(0.5f)
                .graphicsLayer { translationX = size.width * 0.5f * t }
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(Modifier.fillMaxSize()) {
            ModeCell("Files", !gallery, onFiles, Modifier.weight(1f))
            ModeCell("Gallery", gallery, onGallery, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModeCell(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier
) {
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge)
    }
}

fun human(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return "%.1f %s".format(v, units[i])
}
