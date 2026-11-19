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
import de.soenkejacobs.visitenkarte.data.CardSize
import de.soenkejacobs.visitenkarte.data.DisplayOverrides
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
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
    val overrides by displaySettings.overrides.collectAsStateWithLifecycle()
    val filter = LocalContext.current.app.shareFilter
        val pager = rememberCardPager(page, cards.size)
    LaunchedEffect(pager.currentPage) { onPageChanged(pager.currentPage) }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            val card = cards[index]
            // Anzeige nach Karten-Auswahl, QR-Code nach Teilen-Auswahl
            val layout = remember(card, overrides[card.id]) {
                CardLayout.of(card, overrides[card.id] ?: DisplayOverrides(), CardSize.LARGE)
            }
            val qrText = remember(card) { filter.compactText(card) }
            FullscreenCard(card, layout, qrText)
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

/**
 * Große Karte (Design „Klassisch“): oben Kartentitel, Name und Kopfangaben; unten eine feine
 * Linie und die Themen im Raster – die rechte Spalte rechtsbündig am QR-Code. Rechts der QR-Code,
 * darüber das Logo.
 */
@Composable
internal fun FullscreenCard(card: CardItem, layout: CardLayout, qrText: String) {
    val base = cardColor(card)
    val onBase = onColorFor(base)
    val photo = rememberPhoto(card).takeIf { layout.showPhoto }
    val logo = rememberImage(card.logoFile).takeIf { layout.showLogo }
    val qr = remember(qrText) { runCatching { QrCode.render(qrText).asImageBitmap() }.getOrNull() }
    val t = MaterialTheme.typography

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f).compositeOver(Color.Black))))
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(28.dp),
    ) {
        val qrSize = min(maxHeight * 0.55f, maxWidth * 0.3f)
        val logoMaxHeight = (maxHeight - qrSize - 16.dp).coerceAtLeast(32.dp).coerceAtMost(120.dp)
        val photoSize = min(96.dp, maxHeight * 0.26f)
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photo != null) {
                        Image(photo, null, Modifier.size(photoSize).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(20.dp))
                    }
                    Column {
                        CardTitle(card.title, onBase, 11)
                        Spacer(Modifier.height(6.dp))
                        CardHeader(card, layout, onBase, t.headlineLarge, t.titleSmall)
                    }
                }
                Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
                if (layout.groups.isNotEmpty()) {
                    HorizontalDivider(color = onBase.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))
                    // Ganze Themen im Raster; die rechte Spalte schließt bündig zum QR-Code ab
                    EdgeGrid(layout.groups, onBase, listOf(t.bodyLarge, t.bodyMedium, t.bodySmall), Modifier.fillMaxWidth(), columnGap = 28.dp)
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
                if (logo != null) {
                    CardLogo(logo, qrSize, logoMaxHeight)
                    Spacer(Modifier.height(16.dp))
                }
                if (qr != null) {
                    Box(
                        Modifier
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
    }
}
