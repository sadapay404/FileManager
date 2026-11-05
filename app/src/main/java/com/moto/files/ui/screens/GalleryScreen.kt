package com.moto.files.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moto.files.model.Album
import com.moto.files.model.Media
import com.moto.files.vm.GalleryVm

private enum class Tab(val label: String) { PHOTOS("Photos"), ALBUMS("Albums"), MEMORIES("Memories") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(vm: GalleryVm) {
    var tab by remember { mutableStateOf(Tab.PHOTOS) }
    val items by vm.items.collectAsState()
    val albums by vm.albums.collectAsState()
    val memories by vm.memories.collectAsState()
    val cols by vm.cols.collectAsState()
    var viewerAt by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.label.first().toString()) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.PHOTOS -> Grid(items, cols) { viewerAt = it }
                Tab.ALBUMS -> AlbumList(albums)
                Tab.MEMORIES -> AlbumList(memories)
            }
        }
        // Full-screen viewer, outside scaffold padding.
        if (viewerAt != null && items.isNotEmpty()) {
            Viewer(items, viewerAt!!.coerceIn(items.indices)) { viewerAt = null }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Grid(items: List<Media>, cols: Int, onOpen: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols.coerceIn(2..6)),
        contentPadding = PaddingValues(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(items, key = { _, m -> m.id }) { index, m ->
            Box(
                Modifier.aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(onClick = { onOpen(index) })
            ) {
                AsyncImage(m.uri, m.name, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
                if (m.isVideo) {
                    Icon(Icons.Default.PlayCircle, null,
                        Modifier.align(Alignment.Center).size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun AlbumList(albums: List<Album>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.name }) { a ->
            ListItem(
                headlineContent = { Text(a.name) },
                supportingContent = { Text("${a.items.size} items") },
                leadingContent = {
                    AsyncImage(a.cover, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
                }
            )
        }
    }
}

/** Swipeable viewer with pinch-to-zoom and pan. */
@Composable
private fun Viewer(items: List<Media>, start: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { items.size }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager) { page ->
            var scale by remember { mutableStateOf(1f) }
            var dx by remember { mutableStateOf(0f) }
            var dy by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    items[page].uri, null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) { dx += pan.x; dy += pan.y } else { dx = 0f; dy = 0f }
                            }
                        }
                )
                if (items[page].isVideo) {
                    Icon(Icons.Default.PlayCircle, "Play", Modifier.align(Alignment.Center).size(72.dp),
                        tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        IconButton(onClick = onClose, Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}
