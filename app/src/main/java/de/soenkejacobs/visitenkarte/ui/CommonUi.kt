package de.soenkejacobs.visitenkarte.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.soenkejacobs.visitenkarte.data.ShareEntry
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Ab diesem Alter gilt der lokale Stand als veraltet. */
private val STALE_AFTER_MS = TimeUnit.DAYS.toMillis(30)

/** Hinweis, wenn der letzte erfolgreiche Abgleich länger als 30 Tage her ist. */
@Composable
fun StaleDataBanner(lastSync: Long, onSync: () -> Unit, modifier: Modifier = Modifier) {
    if (lastSync <= 0 || System.currentTimeMillis() - lastSync < STALE_AFTER_MS) return
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastSync)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    "Seit $days Tagen nicht abgeglichen",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Stand vom " + DateFormat.getDateInstance().format(Date(lastSync)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = onSync) { Text("Abgleichen") }
        }
    }
}

/** Auswahl, welche Angaben einer Karte geteilt werden. */
@Composable
fun ShareSelectionDialog(
    entries: List<ShareEntry>,
    excluded: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selection by remember { mutableStateOf(excluded.intersect(entries.map { it.key }.toSet())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Was wird geteilt?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Gilt für QR-Code, NFC, Teilen, Quick Share und Widget. Name ist immer enthalten.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn {
                    items(entries, key = { it.key }) { e ->
                        val checked = e.key !in selection
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selection = if (it) selection - e.key else selection + e.key
                            })
                            Column(Modifier.weight(1f)) {
                                Text(e.label, style = MaterialTheme.typography.bodyMedium)
                                Text(e.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selection) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
