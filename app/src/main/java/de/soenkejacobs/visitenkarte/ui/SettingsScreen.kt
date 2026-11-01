package de.soenkejacobs.visitenkarte.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.soenkejacobs.visitenkarte.data.ContentState
import de.soenkejacobs.visitenkarte.data.ServerConfig
import de.soenkejacobs.visitenkarte.data.SyncState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(modifier: Modifier, vm: MainViewModel, content: ContentState, sync: SyncState) {
    val initial = remember { vm.config() }
    var url by rememberSaveable { mutableStateOf(initial.url) }
    var user by rememberSaveable { mutableStateOf(initial.username) }
    var pass by remember { mutableStateOf(initial.password) }
    var showPass by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val config = ServerConfig(url, user, pass)
    val insecure = url.trim().startsWith("http://", ignoreCase = true)
    val urlValid = url.trim().let { it.startsWith("https://", true) || it.startsWith("http://", true) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Webserver", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Visitenkarten und Dokumente werden von diesem Server geladen und lokal gespeichert – " +
                "sie sind danach auch offline verfügbar.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it; testResult = null },
            label = { Text("URL") },
            placeholder = { Text("https://example.de/visitenkarte/") },
            supportingText = { Text("Ordner mit manifest.json oder direkte URL zur JSON-Datei") },
            singleLine = true,
            isError = url.isNotBlank() && !urlValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it; testResult = null },
            label = { Text("Benutzername (Basic Auth)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it; testResult = null },
            label = { Text("Passwort") },
            singleLine = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Passwort anzeigen")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (insecure) {
            Text(
                "Achtung: Ohne HTTPS werden Passwort und Daten unverschlüsselt übertragen. " +
                    "Unverschlüsseltes HTTP ist nur im Heimnetz (private IP-Adressen, *.local) erlaubt.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = urlValid && !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        testResult = vm.test(config).fold({ it }, { "Fehler: ${it.message}" })
                        testing = false
                    }
                },
            ) { Text("Verbindung testen") }
            Button(enabled = urlValid && sync !is SyncState.Running, onClick = { vm.saveAndSync(config) }) {
                Text("Speichern & laden")
            }
        }
        if (testing) CircularProgressIndicator(Modifier.size(24.dp))
        testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                val last = vm.repository.settings.lastSync
                Text("Letzter Abgleich: " + if (last > 0) DateFormat.getDateTimeInstance().format(Date(last)) else "noch nie")
                Text("Offline gespeichert: ${content.cards.size} Karte(n), ${content.documents.size} Dokument(e)")
                when (sync) {
                    SyncState.Running -> Text("Abgleich läuft …")
                    is SyncState.Done -> Text(sync.message)
                    is SyncState.Failed -> Text("Fehler: ${sync.message}", color = MaterialTheme.colorScheme.error)
                    SyncState.Idle -> vm.repository.settings.lastError?.let {
                        Text("Letzter Fehler: $it", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (initial.isConfigured) {
                    OutlinedButton(onClick = vm::syncNow, enabled = sync !is SyncState.Running) { Text("Jetzt abgleichen") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Aufbau der manifest.json", style = MaterialTheme.typography.titleMedium)
                Text(MANIFEST_EXAMPLE, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val MANIFEST_EXAMPLE = """
{
  "cards": [
    { "id": "business", "title": "Geschäftlich",
      "file": "geschaeftlich.vcf", "color": "#1E3A5F",
      "photo": "foto.jpg", "logo": "logo.png" },
    { "id": "private", "title": "Privat",
      "file": "privat.vcf", "color": "#2E7D32" }
  ],
  "documents": [
    { "id": "health", "title": "Gesundheitsinfos",
      "file": "dokumente/gesundheit.html", "icon": "health",
      "assets": ["dokumente/style.css"] },
    { "id": "pv", "title": "Patientenverfügung",
      "file": "dokumente/patientenverfuegung.pdf",
      "icon": "legal" }
  ]
}
""".trimIndent()
