package de.soenkejacobs.visitenkarte.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.style.TextAlign
import de.soenkejacobs.visitenkarte.data.CardSize
import de.soenkejacobs.visitenkarte.data.DisplayOverrides
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.DisplayChoice
import de.soenkejacobs.visitenkarte.data.FieldGroup
import de.soenkejacobs.visitenkarte.data.FieldItem
import de.soenkejacobs.visitenkarte.data.FieldKind
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
 * Ein Thema als zusammenhängender Block mit Symbol (alle Telefonnummern, alle Adressen …).
 * Bei mehreren Einträgen steht die Bezeichnung (Mobil, Privat …) klein hinter dem Wert.
 * [alignEnd]: rechtsbündig, Symbol rechts (rechte Spalte, z. B. am QR-Code).
 */
@Composable
fun IconGroupBlock(
    group: FieldGroup,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    val labelColor = color.copy(alpha = 0.55f)
    val iconSize = (style.fontSize.value * 1.15f).dp
    val icon: @Composable () -> Unit = {
        Icon(group.kind.icon(), null, Modifier.padding(top = 2.dp).size(iconSize), tint = color.copy(alpha = 0.7f))
    }
    Row(modifier, verticalAlignment = Alignment.Top) {
        if (!alignEnd) { icon(); Spacer(Modifier.width(8.dp)) }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            val showLabels = group.items.size > 1
            group.items.forEachIndexed { index, item ->
                if (index > 0 && item.lines.size > 1) Spacer(Modifier.height(4.dp))
                item.lines.forEachIndexed { i, line ->
                    val text = buildAnnotatedString {
                        append(line)
                        if (showLabels && i == 0 && item.label.isNotBlank()) {
                            withStyle(SpanStyle(color = labelColor, fontSize = style.fontSize * 0.78f)) { append("  ${item.label}") }
                        }
                    }
                    Text(
                        text, color = color.copy(alpha = 0.92f), style = style,
                        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                        maxLines = if (item.kind == FieldKind.NOTE) 3 else 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (alignEnd) { Spacer(Modifier.width(8.dp)); icon() }
    }
}

/**
 * Raster für die Themen: so viele Spalten wie nebeneinander passen, Themen zeilenweise in
 * Datei-Reihenfolge. Die erste Spalte ist linksbündig, die letzte rechtsbündig (bündig zum
 * rechten Rand, z. B. am QR-Code). Innerhalb einer Zeile sind die Blöcke unten ausgerichtet.
 * Ein Thema wird nie getrennt. Passt nicht alles in die Höhe, wird die Schrift stufenweise
 * kleiner ([styles]); reicht auch das nicht, entfallen die letzten Zeilen vollständig.
 */
@Composable
fun EdgeGrid(
    groups: List<FieldGroup>,
    color: Color,
    styles: List<TextStyle>,
    modifier: Modifier = Modifier,
    columnGap: Dp = 24.dp,
    rowGap: Dp = 12.dp,
    maxColumns: Int = 4,
) {
    SubcomposeLayout(modifier) { constraints ->
        val maxW = constraints.maxWidth
        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val colGapPx = columnGap.roundToPx()
        val rowGapPx = rowGap.roundToPx()
        val n = groups.size

        class Plan(val styleIndex: Int, val k: Int, val colW: IntArray, val rowH: IntArray, val rows: Int)

        fun plan(styleIndex: Int, sizes: List<Placeable>): Plan {
            var k = minOf(maxOf(n, 1), maxColumns)
            while (k > 1) {
                val w = IntArray(k)
                sizes.forEachIndexed { i, p -> w[i % k] = maxOf(w[i % k], p.width) }
                if (w.sum() + (k - 1) * colGapPx <= maxW) break
                k--
            }
            val colW = IntArray(k)
            sizes.forEachIndexed { i, p -> colW[i % k] = maxOf(colW[i % k], minOf(p.width, maxW)) }
            val rowsTotal = (n + k - 1) / k
            val rowH = IntArray(rowsTotal)
            sizes.forEachIndexed { i, p -> rowH[i / k] = maxOf(rowH[i / k], p.height) }
            // so viele ganze Zeilen wie in die Höhe passen
            var rows = 0
            var h = 0
            for (r in 0 until rowsTotal) {
                val next = h + (if (r > 0) rowGapPx else 0) + rowH[r]
                if (next > maxH) break
                h = next
                rows++
            }
            return Plan(styleIndex, k, colW, rowH, rows)
        }

        var chosen: Plan? = null
        for ((index, style) in styles.withIndex()) {
            val sizes = subcompose("m$index") {
                groups.forEach { IconGroupBlock(it, color, style) }
            }.map { it.measure(Constraints(maxWidth = maxW)) }
            val p = plan(index, sizes)
            chosen = p
            if (p.rows * p.k >= n) break // alles passt in dieser Schriftgröße
        }
        val p = chosen ?: return@SubcomposeLayout layout(0, 0) {}
        val style = styles[p.styleIndex]
        val visible = minOf(n, p.rows * p.k)
        val placeables = subcompose("final") {
            groups.take(visible).forEachIndexed { i, g ->
                IconGroupBlock(g, color, style, alignEnd = p.k > 1 && i % p.k == p.k - 1)
            }
        }.mapIndexed { i, m -> m.measure(Constraints(maxWidth = p.colW[i % p.k].coerceAtLeast(1))) }

        // Spalten: erste links, letzte am rechten Rand, Zwischenraum gleichmäßig verteilt
        val free = (maxW - p.colW.sum()).coerceAtLeast(0)
        val gapEach = if (p.k > 1) free / (p.k - 1) else 0
        val colX = IntArray(p.k)
        for (c in 1 until p.k) colX[c] = colX[c - 1] + p.colW[c - 1] + gapEach
        val rowY = IntArray(p.rows)
        for (r in 1 until p.rows) rowY[r] = rowY[r - 1] + p.rowH[r - 1] + rowGapPx
        val height = if (p.rows == 0) 0 else rowY[p.rows - 1] + p.rowH[p.rows - 1]

        layout(maxW, height) {
            placeables.forEachIndexed { i, pl ->
                val c = i % p.k
                val r = i / p.k
                val x = if (p.k > 1 && c == p.k - 1) colX[c] + p.colW[c] - pl.width else colX[c]
                pl.place(x, rowY[r] + p.rowH[r] - pl.height) // unten ausgerichtet
            }
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

/** Zeile „Kleine Karte: … · Große Karte: …“ mit Anpassen-Knopf. */
@Composable
fun DisplayScope(card: CardItem) {
    val settings = LocalContext.current.app.displaySettings
    val all by settings.overrides.collectAsStateWithLifecycle()
    val overrides = all[card.id] ?: DisplayOverrides()
    val choices = remember(card) { card.displayChoices() }
    fun count(size: CardSize) = choices.count { overrides.forSize(size)[it.key] ?: it.default(size) }
    var showDialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ViewAgenda, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            "Kleine Karte: ${count(CardSize.SMALL)} Angaben · Große Karte: ${count(CardSize.LARGE)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showDialog = true }) { Text("Anpassen") }
    }
    if (showDialog) {
        DisplaySelectionDialog(
            choices = choices,
            overrides = overrides,
            onDismiss = { showDialog = false },
            onSave = { settings.set(card.id, it); showDialog = false },
        )
    }
}

/** Dialog: pro Angabe „Klein“ und „Groß“ an/aus, nach Themen gruppiert. */
@Composable
fun DisplaySelectionDialog(
    choices: List<DisplayChoice>,
    overrides: DisplayOverrides,
    onDismiss: () -> Unit,
    onSave: (DisplayOverrides) -> Unit,
) {
    var small by remember { mutableStateOf(choices.associate { it.key to (overrides.small[it.key] ?: it.defaultSmall) }) }
    var large by remember { mutableStateOf(choices.associate { it.key to (overrides.large[it.key] ?: it.defaultLarge) }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Was steht auf den Karten?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Klein = Karte im Hochformat, Groß = Vollbild im Querformat. Die Details unter der Karte zeigen immer alles. " +
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
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                        items(entries, key = { it.key }) { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        c.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                FilterChip(
                                    selected = small[c.key] == true,
                                    onClick = { small = small + (c.key to !(small[c.key] ?: false)) },
                                    label = { Text(CardSize.SMALL.label) },
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                                FilterChip(
                                    selected = large[c.key] == true,
                                    onClick = { large = large + (c.key to !(large[c.key] ?: false)) },
                                    label = { Text(CardSize.LARGE.label) },
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Nur Abweichungen vom Standard speichern
            TextButton(onClick = {
                onSave(
                    DisplayOverrides(
                        small = small.filter { (k, v) -> v != choices.first { it.key == k }.defaultSmall },
                        large = large.filter { (k, v) -> v != choices.first { it.key == k }.defaultLarge },
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
