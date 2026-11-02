package de.soenkejacobs.visitenkarte.ui

import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.app
import de.soenkejacobs.visitenkarte.data.QrCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(card: CardItem, onBack: () -> Unit) {
    val activity = LocalActivity.current ?: return
    // Bildschirm hell und an lassen, damit der Code gut scanbar ist
    DisposableEffect(Unit) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val text = remember(card) { activity.app.shareFilter.compactText(card) }
    val qr = remember(text) { runCatching { QrCode.render(text).asImageBitmap() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${card.title} – QR-Code") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            qr.onSuccess { bmp ->
                Image(
                    bmp, "QR-Code mit Visitenkarte",
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White),
                    filterQuality = FilterQuality.None,
                )
            }.onFailure {
                Text("QR-Code konnte nicht erzeugt werden – die Karte ist zu groß.\n${it.message}", textAlign = TextAlign.Center)
            }
            Text(card.vcard.formattedName, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Mit der Kamera scannen, um den Kontakt zu speichern.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
