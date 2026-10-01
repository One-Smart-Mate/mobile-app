package com.ih.osm.features.opl.detail.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ih.osm.R
import com.ih.osm.designsystem.anatomy.AnatomyBanner
import com.ih.osm.designsystem.anatomy.AnatomyBannerType
import com.ih.osm.designsystem.anatomy.AnatomyButton
import com.ih.osm.designsystem.anatomy.AnatomyText
import com.ih.osm.designsystem.anatomy.AnatomyTextProperties
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Full-height PDF reader; no WebView, external browser or whole-document bitmap allocation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OplPdfBottomSheet(
    title: String,
    path: String?,
    failed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        modifier = Modifier.fillMaxSize(),
        sheetMaxWidth = Dp.Unspecified,
        shape = RectangleShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets.safeDrawing },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnatomyText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    properties = AnatomyTextProperties(maxLines = 1, overflow = TextOverflow.Ellipsis))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.card_detail_close))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    failed -> PdfError(onRetry)
                    path == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        AnatomyText(stringResource(R.string.opl_detail_loading_pdf))
                    }
                    else -> PdfDocument(path)
                }
            }
        }
    }
}

@Composable
private fun PdfDocument(path: String) {
    var revision by remember(path) { mutableIntStateOf(0) }
    val document = remember(path, revision) { PdfDocumentSession() }
    var pages by remember(document) { mutableIntStateOf(0) }
    var failed by remember(document) { mutableStateOf(false) }
    LaunchedEffect(document) {
        try {
            pages = withContext(Dispatchers.IO) { document.open(File(path)) }
            check(pages > 0)
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { document.close() }
        }
    }
    when {
        failed -> PdfError { revision++ }
        pages == 0 -> CircularProgressIndicator()
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = with(LocalDensity.current) { maxWidth.toPx().roundToInt() }.coerceIn(320, 1440)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(pages, key = { it }) { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnatomyText(stringResource(R.string.opl_detail_pdf_page, index + 1, pages),
                            style = MaterialTheme.typography.labelMedium)
                        PdfPage(document, index, width)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocumentSession, index: Int, width: Int) {
    var revision by remember(document, index) { mutableIntStateOf(0) }
    val result by produceState<Result<Bitmap>?>(null, document, index, width, revision) {
        value = null
        try {
            value = Result.success(withContext(Dispatchers.IO) { document.render(index, width) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            value = Result.failure(error)
        }
    }
    val bitmap = result?.getOrNull()
    when {
        result == null -> Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
        bitmap == null -> PdfError { revision++ }
        else -> {
            var scale by remember(document, index) { mutableFloatStateOf(1f) }
            var x by remember(document, index) { mutableFloatStateOf(0f) }
            var y by remember(document, index) { mutableFloatStateOf(0f) }
            // Keep transforms clipped to this page so adjacent pages and the close button stay usable.
            Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .graphicsLayer { clip = true }) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.opl_detail_pdf_page_description, index + 1),
                    modifier = Modifier.fillMaxSize().pointerInput(document, index) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                // At 1x, one-finger drags belong to the vertical page list.
                                if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                                    val pan = event.calculatePan()
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                    x = if (scale == 1f) 0f else (x + pan.x).coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2)
                                    y = if (scale == 1f) 0f else (y + pan.y).coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = x, translationY = y),
                )
            }
        }
    }
}

@Composable
private fun PdfError(onRetry: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnatomyBanner(stringResource(R.string.opl_detail_pdf_failed), AnatomyBannerType.ERROR)
        AnatomyButton(stringResource(R.string.cards_retry), onClick = onRetry)
    }
}

/** Serialize page rendering and close, including cancellation while a native page is open. */
private class PdfDocumentSession {
    private val mutex = Mutex()
    private var renderer: PdfRenderer? = null

    suspend fun open(file: File): Int = mutex.withLock {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            PdfRenderer(descriptor).also { renderer = it }.pageCount
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }

    suspend fun render(index: Int, requestedWidth: Int): Bitmap = mutex.withLock {
        val pdf = checkNotNull(renderer)
        pdf.openPage(index).use { page ->
            val factor = min(requestedWidth.toFloat() / page.width.coerceAtLeast(1), 4096f / page.height.coerceAtLeast(1))
            val width = (page.width * factor).roundToInt().coerceAtLeast(1)
            val height = (page.height * factor).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (error: Exception) {
                bitmap.recycle()
                throw error
            }
        }
    }

    suspend fun close() = mutex.withLock {
        renderer?.close()
        renderer = null
    }
}
