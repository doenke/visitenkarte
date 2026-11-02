package de.soenkejacobs.visitenkarte.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.QrCode

/**
 * Querformat: die Visitenkarte füllt den ganzen Bildschirm, rechts unten der QR-Code.
 * Wischen wechselt zwischen den Karten.
 */
@Composable
fun FullscreenCards(cards: List<CardItem>, page: Int, onPageChanged: (Int) -> Unit) {
    val activity = LocalActivity.current
    // Vollbild, Display an und hell, damit der QR-Code gut scanbar ist
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            val previousBrightness = window.attributes.screenBrightness
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val pager = rememberPagerState(initialPage = page.coerceIn(0, cards.lastIndex)) { cards.size }
    LaunchedEffect(pager.currentPage) { onPageChanged(pager.currentPage) }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            FullscreenCard(cards[index])
        }
        if (cards.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val onBase = onColorFor(cardColor(cards[pager.currentPage]))
                cards.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(onBase.copy(alpha = if (i == pager.currentPage) 0.9f else 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun cardColor(card: CardItem): Color = card.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

private fun onColorFor(base: Color): Color = if (base.luminance() > 0.5f) Color(0xFF111111) else Color.White

@Composable
private fun FullscreenCard(card: CardItem) {
    val base = cardColor(card)
    val onBase = onColorFor(base)
    val photo = rememberPhoto(card)
    val logo = rememberImage(card.logoFile)
    val v = card.vcard
    val qr = remember(card) { runCatching { QrCode.render(v.compactText()).asImageBitmap() }.getOrNull() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f).compositeOver(Color.Black))))
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(24.dp),
    ) {
        val qrSize = min(maxHeight * 0.62f, maxWidth * 0.38f)
        val logoMax = min(168.dp, maxHeight - qrSize - 16.dp).coerceAtLeast(40.dp)
        val photoSize = min(120.dp, maxHeight * 0.3f)

        // Oben links: Kartentitel
        Text(
            card.title.uppercase(),
            color = onBase.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopStart),
        )

        // Oben rechts: Logo
        if (logo != null) {
            // In ein Feld von max. logoMax Höhe und 1,6 × QR-Breite einpassen
            val ratio = logo.width.toFloat() / logo.height.coerceAtLeast(1)
            val maxW = qrSize * 1.6f
            val w = min(logoMax * ratio, maxW)
            Image(logo, "Logo", Modifier.align(Alignment.TopEnd).size(w, w / ratio), contentScale = ContentScale.Fit)
        }

        // Unten links: Foto, Name und wichtigste Kontaktdaten
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(end = qrSize + 24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (photo != null) {
                Image(
                    photo, null,
                    Modifier.size(photoSize).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    v.formattedName,
                    color = onBase,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                v.title?.let { Text(it, color = onBase.copy(alpha = 0.9f), style = MaterialTheme.typography.titleLarge) }
                v.organization?.let { Text(it, color = onBase.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(10.dp))
                val lines = listOfNotNull(
                    v.phones.firstOrNull()?.value,
                    v.emails.firstOrNull()?.value,
                    v.urls.firstOrNull()?.value?.removePrefix("https://")?.removePrefix("http://"),
                )
                lines.forEach {
                    Text(it, color = onBase.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Unten rechts: QR-Code
        if (qr != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(qrSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(6.dp),
            ) {
                Image(qr, "QR-Code mit Visitenkarte", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            }
        }
    }
}
