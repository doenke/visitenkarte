package de.soenkejacobs.visitenkarte.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.soenkejacobs.visitenkarte.app
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
import androidx.compose.runtime.LaunchedEffect
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
import de.soenkejacobs.visitenkarte.data.ShareEntry
import de.soenkejacobs.visitenkarte.data.ShareFilter
import de.soenkejacobs.visitenkarte.data.CardLayout
import de.soenkejacobs.visitenkarte.data.CardSize
import de.soenkejacobs.visitenkarte.data.DisplayOverrides
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import de.soenkejacobs.visitenkarte.data.FieldKind
import androidx.compose.ui.text.TextStyle
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
    page: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    lastSync: Long = 0,
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
        val displaySettings = LocalContext.current.app.displaySettings
        val overrides by displaySettings.overrides.collectAsStateWithLifecycle()
        val pager = rememberCardPager(page, content.cards.size)
        LaunchedEffect(pager.currentPage) { onPageChanged(pager.currentPage) }
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize()) {
            StaleDataBanner(lastSync, onSync, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                // Aufteilung Karte / Details / Aus; Teilen nutzt weiter die vollständige Karte
                val layout = remember(card, overrides[card.id]) {
                    CardLayout.of(card, overrides[card.id] ?: DisplayOverrides(), CardSize.SMALL)
                }
                val details = remember(card) { CardLayout.details(card) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BusinessCard(card, layout)
                    DisplayScope(card)
                    ShareActions(card, onQr = { onQr(card) }, onNfc = { onNfc(card) })
                    ShareScope(card)
                    DetailsList(details)
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
internal fun rememberImage(file: File?): ImageBitmap? = remember(file, file?.lastModified()) {
    file?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
}

@Composable
internal fun rememberPhoto(card: CardItem): ImageBitmap? {
    val fromFile = rememberImage(card.photoFile)
    val inline = remember(card.vcard) {
        card.vcard.photoBytes?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
    }
    return fromFile ?: inline
}

/**
 * Kleine Karte (Design „Klassisch“): oben Kartentitel, Name und Kopfangaben, rechts das Logo;
 * darunter eine feine Linie und die Themen, unten ausgerichtet. Ganze Themen brechen gemeinsam um.
 */
@Composable
fun BusinessCard(card: CardItem, layout: CardLayout, modifier: Modifier = Modifier) {
    val base = card.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val onBase = if (base.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val photo = rememberPhoto(card).takeIf { layout.showPhoto }
    val logo = rememberImage(card.logoFile).takeIf { layout.showLogo }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f).compositeOver(Color.Black))))
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (photo != null) {
                    Image(photo, null, Modifier.padding(top = 18.dp).size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    CardTitle(card.title, onBase, 10)
                    Spacer(Modifier.height(6.dp))
                    CardHeader(card, layout, onBase, MaterialTheme.typography.headlineSmall, MaterialTheme.typography.bodyMedium)
                }
                if (logo != null) {
                    Spacer(Modifier.width(12.dp))
                    CardLogo(logo, 120.dp, 72.dp)
                }
            }
            if (layout.groups.isNotEmpty()) {
                Column(Modifier.padding(top = 18.dp)) {
                    HorizontalDivider(color = onBase.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))
                    EdgeGrid(layout.groups, onBase, listOf(MaterialTheme.typography.bodySmall), Modifier.fillMaxWidth(), columnGap = 22.dp, rowGap = 10.dp)
                }
            }
        }
    }
}

/** Kartentitel in gesperrten Großbuchstaben („GESCHÄFTLICH“). */
@Composable
internal fun CardTitle(title: String, color: Color, sizeSp: Int) {
    Text(title.uppercase(), color = color.copy(alpha = 0.55f), fontSize = sizeSp.sp, letterSpacing = 2.sp)
}

/** Logo, in eine Fläche von höchstens [maxWidth] × [maxHeight] eingepasst (Seitenverhältnis bleibt). */
@Composable
internal fun CardLogo(logo: ImageBitmap, maxWidth: Dp, maxHeight: Dp) {
    val ratio = logo.width.toFloat() / logo.height.coerceAtLeast(1)
    val w = if (maxWidth / ratio <= maxHeight) maxWidth else maxHeight * ratio
    Image(logo, "Logo", Modifier.size(w, w / ratio), contentScale = ContentScale.Fit)
}

/** Name und Kopfangaben in Datei-Reihenfolge. */
@Composable
internal fun CardHeader(card: CardItem, layout: CardLayout, color: Color, nameStyle: TextStyle, style: TextStyle) {
    Column {
        Text(card.vcard.formattedName, color = color, style = nameStyle, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        layout.header.forEach { item ->
            val text = if (item.kind == FieldKind.NICKNAME) "„${item.lines.first()}“" else item.lines.first()
            Text(text, color = color.copy(alpha = 0.8f), style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}


internal fun Color.compositeOver(background: Color): Color {
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

/** Zeigt, was geteilt wird, und öffnet die Auswahl. */
@Composable
private fun ShareScope(card: CardItem) {
    FieldScope(
        card = card,
        filter = LocalContext.current.app.shareFilter,
        entries = remember(card.vcard) { card.vcard.shareableEntries() },
        allText = "Alle Angaben werden geteilt",
        omittedPrefix = "Geteilt ohne: ",
        icon = { Icons.Default.Share },
        dialogTitle = "Was wird geteilt?",
        dialogHint = "Gilt für QR-Code, NFC, Teilen, Quick Share und Widget. Name ist immer enthalten.",
    )
}

@Composable
private fun FieldScope(
    card: CardItem,
    filter: ShareFilter,
    entries: List<ShareEntry>,
    allText: String,
    omittedPrefix: String,
    icon: (Boolean) -> ImageVector,
    dialogTitle: String,
    dialogHint: String,
) {
    val all by filter.excluded.collectAsStateWithLifecycle()
    val excluded = all[card.id].orEmpty()
    val omitted = entries.filter { it.key in excluded }
    var showDialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon(omitted.isNotEmpty()), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            if (omitted.isEmpty()) allText else omittedPrefix + omitted.joinToString(", ") { it.label },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { showDialog = true }) { Text("Auswählen") }
    }
    if (showDialog) {
        ShareSelectionDialog(
            entries = entries,
            excluded = excluded,
            onDismiss = { showDialog = false },
            onSave = { filter.setExcluded(card.id, it); showDialog = false },
            title = dialogTitle,
            hint = dialogHint,
        )
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
