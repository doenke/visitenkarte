package de.soenkejacobs.visitenkarte.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.soenkejacobs.visitenkarte.data.ContentState
import de.soenkejacobs.visitenkarte.data.DocumentItem
import de.soenkejacobs.visitenkarte.data.SyncState
import de.soenkejacobs.visitenkarte.share.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    modifier: Modifier,
    content: ContentState,
    sync: SyncState,
    onSync: () -> Unit,
    onOpen: (DocumentItem) -> Unit,
    isLocked: (DocumentItem) -> Boolean = { false },
    lastSync: Long = 0,
) {
    PullToRefreshBox(isRefreshing = sync is SyncState.Running, onRefresh = onSync, modifier = modifier.fillMaxSize()) {
        if (content.documents.isEmpty()) {
            EmptyState("Keine Dokumente vorhanden.\nDokumente werden über die manifest.json auf dem Server eingebunden.")
            return@PullToRefreshBox
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { StaleDataBanner(lastSync, onSync, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            items(content.documents, key = { it.id }) { doc ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(doc) },
                    leadingContent = { Icon(iconFor(doc), null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(doc.title) },
                    supportingContent = doc.description?.let { { Text(it) } },
                    trailingContent = if (isLocked(doc)) {
                        { Icon(Icons.Default.Lock, "Gesperrt", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else null,
                )
                HorizontalDivider()
            }
        }
    }
}

private fun iconFor(doc: DocumentItem): ImageVector = when (doc.icon?.lowercase()) {
    "health", "medical", "gesundheit" -> Icons.Default.LocalHospital
    "heart", "notfall" -> Icons.Default.Favorite
    "legal", "recht", "verfuegung" -> Icons.Default.Gavel
    else -> when {
        doc.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        doc.mimeType.startsWith("image/") -> Icons.Default.Image
        else -> Icons.Default.Description
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewer(doc: DocumentItem, secure: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    // Keine Screenshots / keine Vorschau in der App-Übersicht
    val window = LocalActivity.current?.window
    DisposableEffect(secure, window) {
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { ShareHelper.shareDocument(context, doc) }) { Icon(Icons.Default.Share, "Teilen") }
                    IconButton(onClick = { ShareHelper.openDocumentExternally(context, doc) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Extern öffnen")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                doc.mimeType == "application/pdf" -> PdfView(doc)
                doc.mimeType == "text/html" -> HtmlView(doc)
                doc.mimeType.startsWith("image/") -> ImageView(doc)
                doc.mimeType.startsWith("text/") -> TextView(doc)
                else -> EmptyState("Dieser Dateityp kann nicht direkt angezeigt werden.", "Extern öffnen") {
                    ShareHelper.openDocumentExternally(context, doc)
                }
            }
        }
    }
}

@Composable
private fun ZoomBox(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxSize()
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
    ) { content() }
}

@Composable
private fun PdfView(doc: DocumentItem) {
    val renderer = remember(doc.file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(doc.file, ParcelFileDescriptor.MODE_READ_ONLY)) }
    }
    DisposableEffect(renderer) { onDispose { renderer.getOrNull()?.close() } }
    val pdf = renderer.getOrElse {
        EmptyState("PDF konnte nicht geöffnet werden: ${it.message}")
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF808080))) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
        ZoomBox {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pdf.pageCount) { index ->
                    val ratio = remember(index) { synchronized(pdf) { pdf.openPage(index).use { it.width.toFloat() / it.height } } }
                    val bitmap by produceState<Bitmap?>(null, index) {
                        value = withContext(Dispatchers.IO) {
                            synchronized(pdf) {
                                pdf.openPage(index).use { page ->
                                    val w = (widthPx * 2).coerceAtMost(2400)
                                    val h = (w / ratio).toInt()
                                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                        it.eraseColor(android.graphics.Color.WHITE)
                                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        bitmap?.let { Image(it.asImageBitmap(), "Seite ${index + 1}", Modifier.fillMaxSize()) }
                            ?: CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlView(doc: DocumentItem) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = true // nur lokale, synchronisierte Dateien
                settings.allowContentAccess = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.blockNetworkLoads = true // vollständig offline
                webViewClient = WebViewClient()
                loadUrl(android.net.Uri.fromFile(doc.file).toString())
            }
        },
    )
}

@Composable
private fun ImageView(doc: DocumentItem) {
    val bmp = remember(doc.file) { BitmapFactory.decodeFile(doc.file.path)?.asImageBitmap() }
    if (bmp == null) {
        EmptyState("Bild konnte nicht geladen werden.")
    } else {
        ZoomBox { Image(bmp, doc.title, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun TextView(doc: DocumentItem) {
    val text by produceState("", doc.file) { value = withContext(Dispatchers.IO) { doc.file.readText() } }
    SelectionContainer {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(text)
        }
    }
}
