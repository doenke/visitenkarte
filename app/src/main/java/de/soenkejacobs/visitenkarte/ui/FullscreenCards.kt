package de.soenkejacobs.visitenkarte.ui

import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.CardLayout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import de.soenkejacobs.visitenkarte.app
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

    val displaySettings = LocalContext.current.app.displaySettings
    val placements by displaySettings.placements.collectAsStateWithLifecycle()
        val pager = rememberCardPager(page, cards.size)
    LaunchedEffect(pager.currentPage) { onPageChanged(pager.currentPage) }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            val card = cards[index]
            // Anzeige nach Karten-Auswahl, QR-Code nach Teilen-Auswahl
            val layout = remember(card, placements[card.id]) { CardLayout.of(card, placements[card.id].orEmpty()) }
            FullscreenCard(card, layout)
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
private fun FullscreenCard(card: CardItem, layout: CardLayout) {
    val base = cardColor(card)
    val onBase = onColorFor(base)
    val photo = rememberPhoto(card).takeIf { layout.showPhoto }
    val logo = rememberImage(card.logoFile).takeIf { layout.showLogo }
    val filter = LocalContext.current.app.shareFilter
    val qr = remember(card) { runCatching { QrCode.render(filter.compactText(card)).asImageBitmap() }.getOrNull() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f).compositeOver(Color.Black))))
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(24.dp),
    ) {
        val qrSize = min(maxHeight * 0.62f, maxWidth * 0.38f)
        val logoMax = min(168.dp, maxHeight - qrSize - 16.dp).coerceAtLeast(40.dp)
        val photoSize = min(104.dp, maxHeight * 0.26f)
        val leftWidth = maxWidth - qrSize - 32.dp

        // Links: Titel, Foto + Name + Kopfangaben, darunter die Themen in Spalten
        Column(Modifier.align(Alignment.TopStart).width(leftWidth).fillMaxHeight()) {
            Text(card.title.uppercase(), color = onBase.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) {
                    Image(photo, null, Modifier.size(photoSize).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(20.dp))
                }
                CardHeader(card, layout, onBase, MaterialTheme.typography.headlineLarge, MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            // Ganze Themen wandern in die nächste Spalte; was nicht passt, entfällt (nie halbe Themen)
            val t = MaterialTheme.typography
            GroupColumns(
                layout.cardGroups,
                onBase,
                listOf(t.bodyLarge, t.bodyMedium, t.bodySmall),
                Modifier.weight(1f).fillMaxWidth(),
            )
        }

        // Rechts oben: Logo (nur über dem QR-Code, damit es die Angaben nicht überdeckt)
        if (logo != null) {
            val ratio = logo.width.toFloat() / logo.height.coerceAtLeast(1)
            val w = min(logoMax * ratio, qrSize)
            Image(logo, "Logo", Modifier.align(Alignment.TopEnd).size(w, w / ratio), contentScale = ContentScale.Fit)
        }

        // Rechts unten: QR-Code
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
