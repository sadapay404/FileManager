package com.moto.files.ui.files

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moto.files.ui.components.SectionHeading
import com.moto.files.ui.components.StorageMeter

private data class Category(val name: String, val icon: ImageVector)
private val categories = listOf(
    Category("Downloads", Icons.Outlined.Download), Category("Images", Icons.Outlined.Image),
    Category("Videos", Icons.Outlined.Movie), Category("Audio", Icons.Outlined.Headphones),
    Category("Documents", Icons.Outlined.Description), Category("Apps (APKs)", Icons.Outlined.Android),
    Category("Large files", Icons.Outlined.FolderOpen)
)

@Composable
fun FilesScreen(onCategory: (String) -> Unit, modifier: Modifier = Modifier) {
    val usage = remember {
        val stats = StatFs(Environment.getDataDirectory().path)
        Pair(stats.totalBytes - stats.availableBytes, stats.totalBytes)
    }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        item { Text("Your files", style = MaterialTheme.typography.headlineLarge) }
        item { Spacer(Modifier.height(24.dp)); StorageMeter(usage.first, usage.second) }
        item { SectionHeading("Browse by category") }
        items(categories, key = { it.name }) { category ->
            ListItem(
                headlineContent = { Text(category.name) },
                leadingContent = { Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onCategory(category.name) }
            )
        }
    }
}
