package de.soenkejacobs.visitenkarte.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CardItem(
    val id: String,
    val title: String,
    val vcard: VCard,
    val vcfFile: File,
    val photoFile: File?,
    val logoFile: File?,
    val color: Long?,
)

data class DocumentItem(
    val id: String,
    val title: String,
    val description: String?,
    val icon: String?,
    val file: File,
    val mimeType: String,
)

data class ContentState(
    val cards: List<CardItem> = emptyList(),
    val documents: List<DocumentItem> = emptyList(),
    val loadError: String? = null,
)

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val message: String) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Hält die Inhalte lokal vor (offline verfügbar) und gleicht sie mit dem Webserver ab.
 * Ein Abgleich ersetzt den lokalen Stand nur, wenn alle Dateien erfolgreich geladen wurden.
 */
class Repository(private val context: Context, val settings: SettingsStore) {
    private val contentDir get() = File(context.filesDir, "content")
    private val stagingDir get() = File(context.filesDir, "content.tmp")
    private val oldDir get() = File(context.filesDir, "content.old")
    private val manifestFile get() = File(contentDir, MANIFEST_NAME)
    private val mutex = Mutex()

    private val _content = MutableStateFlow(ContentState())
    val content: StateFlow<ContentState> = _content.asStateFlow()

    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    suspend fun reload() = withContext(Dispatchers.IO) {
        _content.value = loadLocal()
    }

    private fun loadLocal(): ContentState {
        recoverInterruptedSwap()
        if (!manifestFile.exists()) return ContentState()
        return try {
            val manifest = Manifest.parse(manifestFile.readText())
            val paths = ContentPaths(settings.load().manifestUrl.ifBlank { "https://localhost/manifest.json" })
            // Pfade wurden beim Sync relativ zur damaligen Manifest-URL gespeichert;
            // die Zuordnung ist im lokalen Index hinterlegt.
            val index = readIndex()
            fun local(file: String?): File? {
                if (file == null) return null
                val rel = index[file] ?: runCatching { paths.localPath(file) }.getOrNull() ?: return null
                return File(contentDir, rel).takeIf { it.exists() }
            }
            val cards = manifest.cards.mapNotNull { c ->
                val f = local(c.file) ?: return@mapNotNull null
                CardItem(
                    id = c.id,
                    title = c.title,
                    vcard = VCard.parse(f.readText()),
                    vcfFile = f,
                    photoFile = local(c.photo),
                    logoFile = local(c.logo),
                    color = c.color?.let(::parseColor),
                )
            }
            val docs = manifest.documents.mapNotNull { d ->
                val f = local(d.file) ?: return@mapNotNull null
                DocumentItem(d.id, d.title, d.description, d.icon, f, d.type ?: guessMime(f.name))
            }
            ContentState(cards, docs)
        } catch (e: Exception) {
            ContentState(loadError = "Lokale Daten konnten nicht gelesen werden: ${e.message}")
        }
    }

    /** Verbindung prüfen, ohne lokale Daten anzufassen. */
    suspend fun testConnection(config: ServerConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = download(config.manifestUrl, config, null).body ?: throw IOException("Leere Antwort")
            val m = Manifest.parse(String(bytes, Charsets.UTF_8))
            "Verbindung ok: ${m.cards.size} Karte(n), ${m.documents.size} Dokument(e)"
        }.recoverCatching { throw IOException(humanMessage(it), it) }
    }

    suspend fun syncNow(): Result<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val config = settings.load()
            if (!config.isConfigured) return@withContext Result.failure(IOException("Kein Server konfiguriert"))
            _sync.value = SyncState.Running
            val result = runCatching { doSync(config) }
            result.onSuccess {
                settings.lastSync = System.currentTimeMillis()
                settings.lastError = null
                _sync.value = SyncState.Done(it)
            }.onFailure {
                stagingDir.deleteRecursively()
                val msg = humanMessage(it)
                settings.lastError = msg
                _sync.value = SyncState.Failed(msg)
            }
            _content.value = loadLocal()
            result.fold({ Result.success(it) }, { Result.failure(IOException(humanMessage(it), it)) })
        }
    }

    private fun doSync(config: ServerConfig): String {
        val manifestBytes = download(config.manifestUrl, config, null).body ?: throw IOException("Leeres Manifest")
        val manifest = Manifest.parse(String(manifestBytes, Charsets.UTF_8))
        val paths = ContentPaths(config.manifestUrl)

        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        val index = linkedMapOf<String, String>()
        var downloaded = 0
        var unchanged = 0
        for (file in manifest.allFiles) {
            val rel = paths.localPath(file)
            index[file] = rel
            val target = File(stagingDir, rel)
            check(target.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) { "Ungültiger Pfad: $file" }
            target.parentFile?.mkdirs()
            val existing = File(contentDir, rel).takeIf { it.isFile }
            val res = download(paths.remoteUrl(file), config, existing?.lastModified())
            if (res.notModified && existing != null) {
                existing.copyTo(target, overwrite = true)
                target.setLastModified(existing.lastModified())
                unchanged++
            } else {
                val body = res.body ?: throw IOException("Leere Antwort für $file")
                target.writeBytes(body)
                if (res.lastModified > 0) target.setLastModified(res.lastModified)
                downloaded++
            }
        }
        File(stagingDir, MANIFEST_NAME).writeBytes(manifestBytes)
        File(stagingDir, INDEX_NAME).writeText(
            index.entries.joinToString("\n") { (k, v) -> b64(k) + "\t" + b64(v) }
        )

        // Atomar austauschen
        oldDir.deleteRecursively()
        if (contentDir.exists() && !contentDir.renameTo(oldDir)) throw IOException("Lokale Daten konnten nicht ersetzt werden")
        if (!stagingDir.renameTo(contentDir)) {
            oldDir.renameTo(contentDir)
            throw IOException("Lokale Daten konnten nicht ersetzt werden")
        }
        oldDir.deleteRecursively()
        return "Aktualisiert: $downloaded neu, $unchanged unverändert"
    }

    private fun recoverInterruptedSwap() {
        if (!contentDir.exists() && oldDir.exists()) oldDir.renameTo(contentDir)
    }

    private fun readIndex(): Map<String, String> {
        val f = File(contentDir, INDEX_NAME)
        if (!f.exists()) return emptyMap()
        return f.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2) unb64(parts[0]) to unb64(parts[1]) else null
        }.toMap()
    }

    private class Response(val body: ByteArray?, val notModified: Boolean, val lastModified: Long)

    private fun download(url: String, config: ServerConfig, ifModifiedSince: Long?): Response {
        var current = URL(url)
        repeat(5) {
            if (current.protocol == "http" && !isLocalNetworkHost(current.host)) {
                throw IOException("Unverschlüsseltes HTTP ist nur im Heimnetz erlaubt – bitte HTTPS verwenden")
            }
            val conn = current.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
                    val token = Base64.encodeToString("${config.username}:${config.password}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    conn.setRequestProperty("Authorization", "Basic $token")
                }
                if (ifModifiedSince != null && ifModifiedSince > 0) conn.ifModifiedSince = ifModifiedSince
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> return Response(conn.inputStream.use { it.readBytes() }, false, conn.lastModified)
                    HttpURLConnection.HTTP_NOT_MODIFIED -> return Response(null, true, 0)
                    301, 302, 303, 307, 308 -> {
                        val next = URL(current, conn.getHeaderField("Location") ?: throw IOException("Weiterleitung ohne Ziel"))
                        // Zugangsdaten niemals an fremde Hosts oder unverschlüsselt weitergeben
                        if (next.host != current.host || (current.protocol == "https" && next.protocol != "https")) {
                            throw IOException("Weiterleitung auf ${next.host} wurde aus Sicherheitsgründen blockiert")
                        }
                        current = next
                    }
                    401 -> throw IOException("Anmeldung fehlgeschlagen (401) – Benutzername/Passwort prüfen")
                    403 -> throw IOException("Zugriff verweigert (403): ${current.path}")
                    404 -> throw IOException("Nicht gefunden (404): ${current.path}")
                    else -> throw IOException("Serverfehler $code bei ${current.path}")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Zu viele Weiterleitungen")
    }

    companion object {
        private const val MANIFEST_NAME = ".manifest.json"
        private const val INDEX_NAME = ".index"

        private fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        private fun unb64(s: String) = String(Base64.decode(s, Base64.NO_WRAP), Charsets.UTF_8)

        fun humanMessage(t: Throwable): String = when (t) {
            is java.net.UnknownHostException -> "Server nicht erreichbar (Name unbekannt / offline)"
            is java.net.SocketTimeoutException -> "Zeitüberschreitung beim Verbindungsaufbau"
            is javax.net.ssl.SSLException -> "TLS-/Zertifikatsfehler: ${t.message}"
            is org.json.JSONException -> "manifest.json ist fehlerhaft: ${t.message}"
            is java.net.MalformedURLException, is java.net.URISyntaxException -> "Ungültige URL: ${t.message}"
            else -> t.message ?: t.javaClass.simpleName
        }

        /** Hosts im lokalen Netz, für die unverschlüsseltes HTTP toleriert wird. */
        fun isLocalNetworkHost(host: String): Boolean {
            val h = host.lowercase().trim('[', ']')
            if (h == "localhost" || listOf(".local", ".lan", ".home.arpa", ".fritz.box", ".internal").any { h.endsWith(it) }) return true
            val parts = h.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size == 4) {
                return parts[0] == 10 || parts[0] == 127 ||
                    (parts[0] == 192 && parts[1] == 168) ||
                    (parts[0] == 172 && parts[1] in 16..31)
            }
            return h.contains(":") && (h.startsWith("fd") || h.startsWith("fe80"))
        }

        fun parseColor(s: String): Long? {
            val hex = s.trim().removePrefix("#")
            val v = hex.toLongOrNull(16) ?: return null
            return when (hex.length) {
                6 -> 0xFF000000 or v
                8 -> v
                else -> null
            }
        }

        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "html", "htm" -> "text/html"
            "txt", "md" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "vcf" -> "text/x-vcard"
            else -> "application/octet-stream"
        }
    }
}
