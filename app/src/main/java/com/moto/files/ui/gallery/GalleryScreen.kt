package com.moto.files.ui.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moto.files.data.media.MediaEntry
import com.moto.files.data.media.MediaRepository
import com.moto.files.ui.viewer.PlayerContent
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(tab: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember(context) { MediaRepository(context) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick++ }
    val imageAccess = permissionTick.let {
        context.checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val videoAccess = permissionTick.let {
        context.checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    val partialAccess = Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    var media by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<MediaEntry?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    LaunchedEffect(imageAccess, videoAccess, partialAccess, permissionTick) {
        if (imageAccess || videoAccess || partialAccess) {
            loading = true
            media = repo.load(imageAccess || partialAccess, videoAccess || partialAccess)
            loading = false
        } else media = emptyList()
    }
    BackHandler(open != null || selected.isNotEmpty() || album != null) {
        if (open != null) open = null else if (selected.isNotEmpty()) selected.clear() else album = null
    }
    if (open != null) {
        MediaViewer(media, open!!, onClose = { open = null })
        return
    }
    LaunchedEffect(tab) { album = null; selected.clear() }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (selected.isNotEmpty()) "${selected.size} selected" else album ?: tab, style = MaterialTheme.typography.headlineLarge)
            if (selected.isNotEmpty()) TextButton(onClick = { selected.clear() }) { Text("Clear") }
            else if (album != null) TextButton(onClick = { album = null }) { Text("All albums") }
        }
        if (!imageAccess && !videoAccess && !partialAccess) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your photos stay yours", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Allow access to show your photos and videos here.")
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { launcher.launch(permissions) }) { Text("Allow media access") }
                }
            }
        } else if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (media.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No photos or videos found") }
        } else {
            val display = if (album == null) media else media.filter { it.bucket == album }
            val groups = when (tab) {
                "Albums" -> if (album == null) display.groupBy { it.bucket } else display.groupBy { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.dateSeconds * 1000)) }
                "Memories" -> display.groupBy { DateFormat.getDateInstance(DateFormat.LONG).format(Date(it.dateSeconds * 1000)) }
                else -> display.groupBy { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.dateSeconds * 1000)) }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = if (tab == "Albums") 150.dp else 104.dp),
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                groups.forEach { (label, entries) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(label, Modifier.padding(start = 10.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    val shown = if (tab == "Albums" && album == null) entries.take(1) else entries
                    items(shown, key = { it.uri.toString() }) { entry ->
                        val key = entry.uri.toString()
                        Box(Modifier.aspectRatio(if (tab == "Albums") 1.15f else 1f)
                            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .combinedClickable(onLongClick = { if (key !in selected) selected.add(key) }, onClick = {
                                if (selected.isNotEmpty()) {
                                    if (key in selected) selected.remove(key) else selected.add(key)
                                } else if (tab == "Albums" && album == null) album = label else open = entry
                            })) {
                            AsyncImage(model = entry.uri, contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (entry.isVideo) Icon(Icons.Outlined.PlayArrow, "Video", Modifier.align(Alignment.BottomEnd).padding(8.dp))
                            if (key in selected) Checkbox(true, onCheckedChange = { if (!it) selected.remove(key) }, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(media: List<MediaEntry>, initial: MediaEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = media.indexOf(initial).coerceAtLeast(0), pageCount = { media.size })
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.ArrowBack, "Close viewer") }
            Text(media.getOrNull(pager.currentPage)?.name.orEmpty(), maxLines = 1, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { media[it].uri.toString() }) { index ->
            val item = media[index]
            if (item.isVideo) {
                PlayerContent(item.uri, video = true, modifier = Modifier.fillMaxSize())
            } else {
                var scale by remember(item.uri) { mutableFloatStateOf(1f) }
                var offsetX by remember(item.uri) { mutableFloatStateOf(0f) }
                var offsetY by remember(item.uri) { mutableFloatStateOf(0f) }
                AsyncImage(model = item.uri, contentDescription = item.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().pointerInput(item.uri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX = if (scale == 1f) 0f else (offsetX + pan.x).coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2)
                            offsetY = if (scale == 1f) 0f else (offsetY + pan.y).coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2)
                        }
                    }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY })
            }
        }
    }
}
