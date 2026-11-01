package de.soenkejacobs.visitenkarte.ui

import android.content.ComponentName
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.CardEmulation
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.nfc.CardHceService
import de.soenkejacobs.visitenkarte.nfc.NfcPayload
import de.soenkejacobs.visitenkarte.nfc.Type4TagEmulator

private enum class NfcMode(val label: String) { PHONE("Handy an Handy"), TAG("NFC-Tag beschreiben") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScreen(card: CardItem, onBack: () -> Unit) {
    val activity = LocalActivity.current ?: return
    val adapter = remember { NfcAdapter.getDefaultAdapter(activity) }
    val hceSupported = remember { activity.packageManager.hasSystemFeature("android.hardware.nfc.hce") }
    var mode by remember { mutableStateOf(NfcMode.PHONE) }
    var status by remember { mutableStateOf<String?>(null) }
    var reads by remember { mutableIntStateOf(0) }
    var nfcEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }

    val message = remember(card) { NfcPayload.vcardMessage(card.vcard.compactText()) }
    val tooLarge = message.size > Type4TagEmulator.MAX_NDEF_SIZE

    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            NfcPayload.current = null
            NfcPayload.onRead = null
        }
    }

    LifecycleResumeEffect(mode, card) {
        nfcEnabled = adapter?.isEnabled == true
        status = null
        if (adapter != null && !tooLarge) {
            when (mode) {
                NfcMode.PHONE -> {
                    NfcPayload.current = message
                    NfcPayload.onRead = { activity.runOnUiThread { reads++ } }
                    runCatching {
                        CardEmulation.getInstance(adapter)
                            .setPreferredService(activity, ComponentName(activity, CardHceService::class.java))
                    }
                    // Ab Android 15: eigenes Lesen abschalten, damit das andere Handy uns sicher als Tag erkennt
                    if (Build.VERSION.SDK_INT >= 35) {
                        runCatching {
                            adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_DISABLE, NfcAdapter.FLAG_LISTEN_KEEP)
                        }
                    }
                }
                NfcMode.TAG -> {
                    NfcPayload.current = null
                    adapter.enableReaderMode(
                        activity,
                        { tag -> val r = writeTag(tag, message); activity.runOnUiThread { status = r } },
                        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                        null,
                    )
                }
            }
        }
        onPauseOrDispose {
            NfcPayload.current = null
            if (adapter != null) {
                runCatching { CardEmulation.getInstance(adapter).unsetPreferredService(activity) }
                if (Build.VERSION.SDK_INT >= 35) runCatching { adapter.resetDiscoveryTechnology(activity) }
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${card.title} – NFC") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                adapter == null -> Text("Dieses Gerät hat kein NFC.", textAlign = TextAlign.Center)
                !nfcEnabled -> {
                    Text("NFC ist ausgeschaltet.", textAlign = TextAlign.Center)
                    Button(onClick = { activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text("NFC einschalten") }
                }
                tooLarge -> Text("Die Visitenkarte ist zu groß für NFC.", textAlign = TextAlign.Center)
                else -> {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        NfcMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = mode == m,
                                onClick = { mode = m },
                                shape = SegmentedButtonDefaults.itemShape(i, NfcMode.entries.size),
                            ) { Text(m.label) }
                        }
                    }
                    Icon(Icons.Default.Nfc, null, Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
                    when (mode) {
                        NfcMode.PHONE -> {
                            if (!hceSupported) {
                                Text("Dieses Gerät kann keine NFC-Tags emulieren. Du kannst aber einen NFC-Tag beschreiben.", textAlign = TextAlign.Center)
                            } else {
                                Text(
                                    "Bereit. Halte die Rückseite deines Handys an das andere Handy – " +
                                        "das andere Gerät muss entsperrt sein und NFC eingeschaltet haben.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (reads > 0) Text("✓ Karte wurde gelesen", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        NfcMode.TAG -> Text(
                            "Halte einen NFC-Tag (z. B. Sticker oder Karte, NTAG215/216) an die Rückseite. " +
                                "Die Visitenkarte „${card.title}“ wird darauf gespeichert.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                    Row { Text("Größe: ${message.size} Bytes", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private fun writeTag(tag: Tag, bytes: ByteArray): String {
    val msg = NdefMessage(bytes)
    return try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            ndef.use {
                when {
                    !it.isWritable -> "Tag ist schreibgeschützt"
                    it.maxSize < bytes.size -> "Tag zu klein (${it.maxSize} Bytes, benötigt ${bytes.size})"
                    else -> { it.writeNdefMessage(msg); "✓ Auf Tag geschrieben" }
                }
            }
        } else {
            val formatable = NdefFormatable.get(tag) ?: return "Tag unterstützt kein NDEF"
            formatable.connect()
            formatable.use { it.format(msg) }
            "✓ Tag formatiert und beschrieben"
        }
    } catch (e: Exception) {
        "Schreiben fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
    }
}
