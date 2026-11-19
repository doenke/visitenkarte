package de.soenkejacobs.visitenkarte.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.soenkejacobs.visitenkarte.app
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.DisplayChoice
import de.soenkejacobs.visitenkarte.data.FieldGroup
import de.soenkejacobs.visitenkarte.data.FieldItem
import de.soenkejacobs.visitenkarte.data.FieldKind
import de.soenkejacobs.visitenkarte.data.Placement
import de.soenkejacobs.visitenkarte.data.displayChoices

/** Symbol je Thema. */
fun FieldKind.icon(): ImageVector = when (this) {
    FieldKind.NICKNAME -> Icons.Default.Person
    FieldKind.TITLE, FieldKind.ROLE -> Icons.Default.Badge
    FieldKind.ORG -> Icons.Default.Business
    FieldKind.PHONE -> Icons.Default.Phone
    FieldKind.EMAIL -> Icons.Default.Email
    FieldKind.WEB -> Icons.Default.Language
    FieldKind.ADDRESS -> Icons.Default.Place
    FieldKind.MESSENGER -> Icons.AutoMirrored.Filled.Chat
    FieldKind.DATE -> Icons.Default.Event
    FieldKind.RELATED -> Icons.Default.People
    FieldKind.GEO -> Icons.Default.MyLocation
    FieldKind.LANGUAGE -> Icons.Default.Translate
    FieldKind.TIMEZONE -> Icons.Default.Schedule
    FieldKind.GENDER -> Icons.Default.Person
    FieldKind.EXPERTISE -> Icons.Default.School
    FieldKind.INTEREST -> Icons.Default.Favorite
    FieldKind.CATEGORIES -> Icons.AutoMirrored.Filled.Label
    FieldKind.NOTE -> Icons.AutoMirrored.Filled.Notes
    FieldKind.OTHER -> Icons.Default.Info
}

/**
 * Ein Thema als zusammenhängender Block (alle Telefonnummern, alle Adressen …).
 * Bei mehreren Einträgen steht die Bezeichnung (Mobil, Privat …) klein hinter dem Wert.
 */
@Composable
fun GroupBlock(group: FieldGroup, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val showLabels = group.items.size > 1
    Column(modifier) {
        group.items.forEachIndexed { index, item ->
            if (index > 0 && item.lines.size > 1) Spacer(Modifier.padding(top = 4.dp))
            item.lines.forEachIndexed { i, line ->
                val text = buildAnnotatedString {
                    append(line)
                    if (showLabels && i == 0 && item.label.isNotBlank()) {
                        withStyle(SpanStyle(color = color.copy(alpha = 0.6f), fontSize = style.fontSize * 0.8f)) {
                            append("  ${item.label}")
                        }
                    }
                }
                Text(text, color = color.copy(alpha = 0.9f), style = style, maxLines = if (item.kind == FieldKind.NOTE) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Themen nebeneinander, die ganze Themen in die nächste Zeile umbrechen (Hochformat-Karte). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupFlow(groups: List<FieldGroup>, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { GroupBlock(it, color, style) }
    }
}

/**
 * Themen spaltenweise von oben nach unten (Vollbild im Querformat). Passt ein Thema nicht mehr
 * in die Spalte, beginnt es als Ganzes in der nächsten Spalte – ein Thema wird nie getrennt.
 * Passen nicht alle Themen, wird die Schrift stufenweise kleiner ([styles]); erst wenn auch die
 * kleinste Stufe nicht reicht, entfallen die letzten Themen vollständig.
 */
@Composable
fun GroupColumns(
    groups: List<FieldGroup>,
    color: Color,
    styles: List<TextStyle>,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    columnGap: Dp = 28.dp,
    maxColumnWidth: Dp = 360.dp,
) {
    SubcomposeLayout(modifier) { constraints ->
        val maxW = constraints.maxWidth
        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val gapPx = gap.roundToPx()
        val columnGapPx = columnGap.roundToPx()

        /** Packt die Blöcke spaltenweise; liefert Positionen (null = passt nicht). */
        fun pack(placeables: List<Placeable>): List<Pair<Int, Int>?> {
            val positions = MutableList<Pair<Int, Int>?>(placeables.size) { null }
            var x = 0
            var y = 0
            var columnWidth = 0
            for ((i, p) in placeables.withIndex()) {
                if (y > 0 && y + p.height > maxH) {
                    x += columnWidth + columnGapPx
                    y = 0
                    columnWidth = 0
                }
                if (x + p.width > maxW || p.height > maxH) break
                positions[i] = x to y
                y += p.height + gapPx
                columnWidth = maxOf(columnWidth, p.width)
            }
            return positions
        }

        var chosen: List<Placeable> = emptyList()
        var positions: List<Pair<Int, Int>?> = emptyList()
        for ((index, style) in styles.withIndex()) {
            val placeables = subcompose(index) {
                groups.forEach { GroupBlock(it, color, style, Modifier.widthIn(max = maxColumnWidth)) }
            }.map { it.measure(Constraints(maxWidth = maxW, maxHeight = maxH)) }
            chosen = placeables
            positions = pack(placeables)
            if (positions.all { it != null }) break // alles passt in dieser Schriftgröße
        }
        layout(maxW, if (constraints.hasBoundedHeight) constraints.maxHeight else (chosen.lastOrNull()?.height ?: 0)) {
            chosen.forEachIndexed { i, p -> positions[i]?.let { (px, py) -> p.place(px, py) } }
        }
    }
}

/** Liste unter der Karte: alle sichtbaren Themen mit Überschrift, antippbar. */
@Composable
fun DetailsList(groups: List<FieldGroup>) {
    if (groups.isEmpty()) return
    val context = LocalContext.current
    fun open(uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: Exception) {
            Toast.makeText(context, "Keine passende App gefunden", Toast.LENGTH_SHORT).show()
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            groups.forEach { group ->
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 56.dp, top = 8.dp, bottom = 2.dp),
                )
                group.items.forEach { item -> DetailItemRow(item) { item.uri?.let(::open) } }
            }
        }
    }
}

@Composable
private fun DetailItemRow(item: FieldItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.uri != null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.kind.icon(), null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(item.lines.joinToString("\n"), style = MaterialTheme.typography.bodyLarge)
            Text(item.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Zeile „Angezeigt: …“ mit Auswahl-Knopf. */
@Composable
fun DisplayScope(card: CardItem) {
    val settings = LocalContext.current.app.displaySettings
    val all by settings.placements.collectAsStateWithLifecycle()
    val placements = all[card.id].orEmpty()
    val choices = remember(card) { card.displayChoices() }
    val effective = choices.map { placements[it.key] ?: it.default }
    val onCard = effective.count { it == Placement.CARD }
    val details = effective.count { it == Placement.DETAILS }
    val hidden = effective.count { it == Placement.HIDDEN }
    var showDialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ViewAgenda, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            "Anzeige: $onCard auf der Karte, $details in Details" + if (hidden > 0) ", $hidden aus" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showDialog = true }) { Text("Anpassen") }
    }
    if (showDialog) {
        DisplaySelectionDialog(
            choices = choices,
            placements = placements,
            onDismiss = { showDialog = false },
            onSave = { settings.set(card.id, it); showDialog = false },
        )
    }
}

/** Dialog: pro Angabe Karte / Details / Aus, nach Themen gruppiert. */
@Composable
fun DisplaySelectionDialog(
    choices: List<DisplayChoice>,
    placements: Map<String, Placement>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Placement>) -> Unit,
) {
    var current by remember { mutableStateOf(choices.associate { it.key to (placements[it.key] ?: it.default) }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Was wird angezeigt?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Karte = auf der Visitenkarte und im Vollbild, Details = nur in der Liste darunter. " +
                        "Themen stehen in der Reihenfolge der vCard-Datei. Was geteilt wird, stellst du separat ein.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn {
                    choices.groupBy { it.section }.forEach { (section, entries) ->
                        item(key = "h:$section") {
                            Text(
                                section,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(entries, key = { it.key }) { c ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(c.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    c.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val options = if (c.imageOnly) listOf(Placement.CARD, Placement.HIDDEN) else Placement.entries
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    options.forEachIndexed { i, option ->
                                        SegmentedButton(
                                            selected = current[c.key] == option,
                                            onClick = { current = current + (c.key to option) },
                                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                                            icon = {},
                                        ) {
                                            if (option == Placement.HIDDEN) {
                                                Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(option.label, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Nur Abweichungen vom Standard speichern
            TextButton(onClick = { onSave(current.filter { (k, v) -> v != choices.first { it.key == k }.default }) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
