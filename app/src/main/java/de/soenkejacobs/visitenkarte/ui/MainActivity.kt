package de.soenkejacobs.visitenkarte.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VisitenkarteTheme { App(vm) }
        }
    }
}

private enum class Tab(val label: String) { CARDS("Karten"), DOCS("Dokumente"), SETTINGS("Server") }

/** Vollbild-Unterseiten, über den Tabs. */
private sealed interface Overlay {
    data class Qr(val cardId: String) : Overlay
    data class Nfc(val cardId: String) : Overlay
    data class Doc(val docId: String) : Overlay
}

@Composable
private fun App(vm: MainViewModel) {
    val content by vm.content.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (vm.config().isConfigured) Tab.CARDS else Tab.SETTINGS) }
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    val overlay: Overlay? = overlayKey?.let { key ->
        val (type, id) = key.split(':', limit = 2)
        when (type) {
            "qr" -> Overlay.Qr(id)
            "nfc" -> Overlay.Nfc(id)
            else -> Overlay.Doc(id)
        }
    }

    if (overlay != null) {
        BackHandler { overlayKey = null }
        val close = { overlayKey = null }
        when (overlay) {
            is Overlay.Qr -> content.cards.firstOrNull { it.id == overlay.cardId }?.let { QrScreen(it, close) } ?: LaunchedEffect(Unit) { close() }
            is Overlay.Nfc -> content.cards.firstOrNull { it.id == overlay.cardId }?.let { NfcScreen(it, close) } ?: LaunchedEffect(Unit) { close() }
            is Overlay.Doc -> content.documents.firstOrNull { it.id == overlay.docId }?.let { DocumentViewer(it, close) } ?: LaunchedEffect(Unit) { close() }
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.CARDS -> Icons.Default.Badge
                                    Tab.DOCS -> Icons.Default.Folder
                                    Tab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.CARDS -> CardsScreen(
                modifier = modifier,
                content = content,
                sync = sync,
                onSync = vm::syncNow,
                onSetup = { tab = Tab.SETTINGS },
                onQr = { overlayKey = "qr:${it.id}" },
                onNfc = { overlayKey = "nfc:${it.id}" },
            )
            Tab.DOCS -> DocumentsScreen(
                modifier = modifier,
                content = content,
                sync = sync,
                onSync = vm::syncNow,
                onOpen = { overlayKey = "doc:${it.id}" },
            )
            Tab.SETTINGS -> SettingsScreen(modifier = modifier, vm = vm, content = content, sync = sync)
        }
    }
}
