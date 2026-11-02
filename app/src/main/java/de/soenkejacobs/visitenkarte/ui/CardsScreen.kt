package de.soenkejacobs.visitenkarte.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.ContentState
import de.soenkejacobs.visitenkarte.data.SyncState
import de.soenkejacobs.visitenkarte.share.ShareHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    modifier: Modifier,
    content: ContentState,
    sync: SyncState,
    onSync: () -> Unit,
    onSetup: () -> Unit,
    onQr: (CardItem) -> Unit,
    onNfc: (CardItem) -> Unit,
) {
    PullToRefreshBox(isRefreshing = sync is SyncState.Running, onRefresh = onSync, modifier = modifier.fillMaxSize()) {
        if (content.cards.isEmpty()) {
            EmptyState(
                text = content.loadError ?: if (sync is SyncState.Failed) "Abgleich fehlgeschlagen:\n${sync.message}"
                else "Noch keine Visitenkarten vorhanden.\nRichte den Server ein, um deine Karten zu laden.",
                action = "Server einrichten",
                onAction = onSetup,
            )
            return@PullToRefreshBox
        }
        val pager = rememberPagerState { content.cards.size }
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize()) {
            if (content.cards.size > 1) {
                PrimaryTabRow(selectedTabIndex = pager.currentPage.coerceAtMost(content.cards.lastIndex)) {
                    content.cards.forEachIndexed { i, c ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            text = { Text(c.title) },
                        )
                    }
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val card = content.cards[page]
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BusinessCard(card)
                    ShareActions(card, onQr = { onQr(card) }, onNfc = { onNfc(card) })
                    ContactDetails(card)
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun rememberImage(file: File?): ImageBitmap? = remember(file, file?.lastModified()) {
    file?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
}

@Composable
private fun rememberPhoto(card: CardItem): ImageBitmap? {
    val fromFile = rememberImage(card.photoFile)
    val inline = remember(card.vcard) {
        card.vcard.photoBytes?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
    }
    return fromFile ?: inline
}

/** Die Visitenkarte im klassischen Format (85 × 55 mm). */
@Composable
fun BusinessCard(card: CardItem, modifier: Modifier = Modifier) {
    val base = card.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val onBase = if (base.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val photo = rememberPhoto(card)
    val logo = rememberImage(card.logoFile)
    val v = card.vcard
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f).compositeOver(Color.Black))))
                .padding(20.dp),
        ) {
            if (logo != null) {
                Image(logo, null, Modifier.align(Alignment.TopEnd).size(56.dp), contentScale = ContentScale.Fit)
            }
            Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) {
                    Image(
                        photo, null,
                        Modifier.size(72.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    Text(v.formattedName, color = onBase, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    v.nickname?.let { Text("„$it“", color = onBase.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium) }
                    v.title?.let { Text(it, color = onBase.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge) }
                    v.organization?.let { Text(it, color = onBase.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Text(
                card.title.uppercase(),
                color = onBase.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
private fun ShareActions(card: CardItem, onQr: () -> Unit, onNfc: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Icons.Default.QrCode2, "QR-Code", Modifier.weight(1f), onQr)
            ActionButton(Icons.Default.Nfc, "NFC", Modifier.weight(1f), onNfc)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Icons.Default.Share, "Teilen", Modifier.weight(1f)) { ShareHelper.shareCard(context, card) }
            ActionButton(Icons.AutoMirrored.Filled.Send, "Quick Share", Modifier.weight(1f)) { ShareHelper.quickShareCard(context, card) }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(56.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ContactDetails(card: CardItem) {
    val context = LocalContext.current
    val v = card.vcard
    fun open(uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: Exception) {
            Toast.makeText(context, "Keine passende App gefunden", Toast.LENGTH_SHORT).show()
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            v.phones.forEach { DetailRow(Icons.Default.Phone, it.label, it.value) { open("tel:${it.value.filter { c -> c.isDigit() || c == '+' }}") } }
            v.emails.forEach { DetailRow(Icons.Default.Email, it.label, it.value) { open("mailto:${it.value}") } }
            v.urls.forEach {
                DetailRow(Icons.Default.Language, it.label, it.value) {
                    open(if (it.value.contains("://")) it.value else "https://${it.value}")
                }
            }
            v.addresses.forEach {
                DetailRow(Icons.Default.Place, it.label, it.lines.joinToString("\n")) { open("geo:0,0?q=${Uri.encode(it.singleLine)}") }
            }
            v.birthdayFormatted?.let { DetailRow(Icons.Default.Cake, "Geburtstag", it) {} }
            v.note?.let {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
