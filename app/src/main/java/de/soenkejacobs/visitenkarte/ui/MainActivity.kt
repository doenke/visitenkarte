package de.soenkejacobs.visitenkarte.ui

import android.content.res.Configuration
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.soenkejacobs.visitenkarte.data.DocumentItem
import de.soenkejacobs.visitenkarte.launcher.Shortcuts

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            VisitenkarteTheme { App(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Aufrufe aus App-Shortcuts und Widget. */
    private fun handleIntent(intent: Intent?) {
        val cardId = intent?.getStringExtra(Shortcuts.EXTRA_CARD_ID) ?: return
        when (intent.action) {
            Shortcuts.ACTION_SHOW_QR -> vm.requestOverlay("qr:$cardId")
            Shortcuts.ACTION_SHOW_NFC -> vm.requestOverlay("nfc:$cardId")
        }
    }

    override fun onStop() {
        super.onStop()
        // Beim Verlassen der App Dokumente wieder sperren (nicht beim Drehen)
        if (!isChangingConfigurations) vm.lock()
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
    val lastSync by vm.lastSync.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val lockDocuments by vm.lockDocuments.collectAsStateWithLifecycle()
    val secureScreens by vm.secureScreens.collectAsStateWithLifecycle()
    val pending by vm.pendingOverlay.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    var tab by rememberSaveable { mutableStateOf(if (vm.config().isConfigured) Tab.CARDS else Tab.SETTINGS) }
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cardPage by rememberSaveable { mutableIntStateOf(0) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val overlay: Overlay? = overlayKey?.let { key ->
        val (type, id) = key.split(':', limit = 2)
        when (type) {
            "qr" -> Overlay.Qr(id)
            "nfc" -> Overlay.Nfc(id)
            else -> Overlay.Doc(id)
        }
    }

    // Shortcut/Widget: gewünschte Ansicht öffnen
    LaunchedEffect(pending) {
        pending?.let {
            tab = Tab.CARDS
            overlayKey = it
            vm.consumeOverlay()
        }
    }

    fun isLocked(doc: DocumentItem) = lockDocuments && doc.lock && !unlocked

    fun openDocument(doc: DocumentItem) {
        if (isLocked(doc) && activity != null) {
            DocumentLock.authenticate(activity) {
                vm.unlock()
                overlayKey = "doc:${doc.id}"
            }
        } else {
            overlayKey = "doc:${doc.id}"
        }
    }

    if (overlay != null) {
        BackHandler { overlayKey = null }
        val close = { overlayKey = null }
        // Erst schließen, wenn die lokalen Daten gelesen sind (Start über Shortcut)
        val missing: @Composable () -> Unit = { if (content.loaded) LaunchedEffect(Unit) { close() } }
        when (overlay) {
            is Overlay.Qr -> content.cards.firstOrNull { it.id == overlay.cardId }?.let { QrScreen(it, close) } ?: missing()
            is Overlay.Nfc -> content.cards.firstOrNull { it.id == overlay.cardId }?.let { NfcScreen(it, close) } ?: missing()
            is Overlay.Doc -> {
                val doc = content.documents.firstOrNull { it.id == overlay.docId }
                when {
                    doc == null -> missing()
                    // z. B. nach Rückkehr in die App: wieder gesperrt
                    isLocked(doc) -> LaunchedEffect(Unit) { close() }
                    else -> DocumentViewer(doc, secure = secureScreens && lockDocuments && doc.lock, onBack = close)
                }
            }
        }
        return
    }

    // Querformat auf dem Karten-Tab: aktuelle Karte im Vollbild mit QR-Code
    if (landscape && tab == Tab.CARDS && content.cards.isNotEmpty()) {
        FullscreenCards(content.cards, cardPage) { cardPage = it }
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
                page = cardPage,
                onPageChanged = { cardPage = it },
                lastSync = lastSync,
            )
            Tab.DOCS -> DocumentsScreen(
                modifier = modifier,
                content = content,
                sync = sync,
                onSync = vm::syncNow,
                onOpen = ::openDocument,
                isLocked = ::isLocked,
                lastSync = lastSync,
            )
            Tab.SETTINGS -> SettingsScreen(modifier = modifier, vm = vm, content = content, sync = sync)
        }
    }
}
