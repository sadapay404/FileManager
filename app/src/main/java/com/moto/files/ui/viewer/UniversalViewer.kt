package com.moto.files.ui.viewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Built-in common formats; Open with exposes other installed format/codec handlers. */
@Composable
fun UniversalViewer(uri: Uri, name: String, reportedMime: String?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val type = remember(name, reportedMime) { FileTypes.kind(name, reportedMime) }
    var message by remember(uri) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onClose)
    fun openExternal() {
        try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileTypes.mime(name, reportedMime))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Open with"))
        } catch (_: ActivityNotFoundException) { message = "No installed app can open this format" }
        catch (_: SecurityException) { message = "File permission is no longer available" }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.ArrowBack, "Close viewer") }
            Text(name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = ::openExternal) { Text("Open with") }
        }
        if (message != null) Text(message!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        when (type) {
            ContentKind.IMAGE -> AsyncImage(model = uri, contentDescription = name, contentScale = ContentScale.Fit,
                onError = { message = "This image cannot be decoded here. Try Open with." }, modifier = Modifier.fillMaxSize())
            ContentKind.VIDEO, ContentKind.AUDIO -> PlayerContent(uri, type == ContentKind.VIDEO, Modifier.fillMaxSize(), onError = { message = it })
            ContentKind.PDF -> PdfContent(uri, Modifier.fillMaxSize(), onError = { message = it })
            ContentKind.TEXT -> TextContent(uri, Modifier.fillMaxSize(), onError = { message = it })
            ContentKind.EXTERNAL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No built-in preview for this format")
                    TextButton(onClick = ::openExternal) { Text("Open with another app") }
                }
            }
        }
    }
}

@Composable
fun PlayerContent(uri: Uri, video: Boolean, modifier: Modifier = Modifier, onError: (String) -> Unit = {}) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onError("Playback failed or codec unavailable. Try Open with.")
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } },
            update = { it.player = player }, modifier = if (video) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(130.dp))
    }
}

@Composable
private fun PdfContent(uri: Uri, modifier: Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    var pageIndex by remember(uri) { mutableIntStateOf(0) }
    var count by remember(uri) { mutableIntStateOf(0) }
    var image by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, pageIndex) {
        try {
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val total = renderer.pageCount
                        require(total > 0) { "Empty PDF" }
                        val page = renderer.openPage(pageIndex.coerceIn(0, total - 1))
                        page.use {
                            val width = page.width.coerceAtMost(1600)
                            val height = (page.height.toFloat() * width / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 2400)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            total to bitmap
                        }
                    }
                } ?: error("Cannot read PDF")
            }
            count = result.first
            image = result.second
        } catch (failure: Exception) { onError(failure.message ?: "PDF cannot be rendered") }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = pageIndex > 0, onClick = { pageIndex-- }) { Text("Previous") }
            Text("${pageIndex + 1} / $count")
            TextButton(enabled = pageIndex + 1 < count, onClick = { pageIndex++ }) { Text("Next") }
        }
        image?.let { Image(it.asImageBitmap(), contentDescription = "PDF page ${pageIndex + 1}",
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentScale = ContentScale.Fit) }
    }
}

@Composable
private fun TextContent(uri: Uri, modifier: Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    var content by remember(uri) { mutableStateOf("Loading…") }
    LaunchedEffect(uri) {
        try {
            content = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(8192)
                        val result = StringBuilder()
                        while (result.length < 1_000_000) {
                            val n = reader.read(buffer, 0, minOf(buffer.size, 1_000_000 - result.length))
                            if (n < 0) break
                            result.append(buffer, 0, n)
                        }
                        result.toString()
                    }
                } ?: error("Cannot read text")
            }
        } catch (failure: Exception) { onError(failure.message ?: "Cannot read text") }
    }
    Text(content, modifier.verticalScroll(rememberScrollState()).padding(20.dp), style = MaterialTheme.typography.bodyMedium)
}
