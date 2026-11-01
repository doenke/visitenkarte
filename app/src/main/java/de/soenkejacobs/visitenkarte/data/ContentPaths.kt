package de.soenkejacobs.visitenkarte.data

import java.net.URI
import java.net.URLDecoder

/**
 * Bildet Dateiangaben aus dem Manifest auf Download-URLs und sichere lokale Pfade ab.
 * Lokale Pfade spiegeln die Verzeichnisstruktur unterhalb des Manifests, damit relative
 * Verweise in HTML-Dokumenten (Bilder, CSS) auch offline funktionieren.
 */
class ContentPaths(manifestUrl: String) {
    private val manifestUri: URI = URI(encode(manifestUrl.trim()))
    private val baseDir: String = manifestUri.resolve(".").toString()

    fun remoteUrl(file: String): String = manifestUri.resolve(encode(file)).toASCIIString()

    /** Relativer, gegen Pfad-Traversal abgesicherter lokaler Pfad. */
    fun localPath(file: String): String {
        val resolved = manifestUri.resolve(encode(file)).normalize().toString().substringBefore('?').substringBefore('#')
        val relative = if (resolved.startsWith(baseDir)) {
            resolved.removePrefix(baseDir)
        } else {
            "_extern/" + Integer.toHexString(resolved.hashCode()) + "_" + resolved.substringAfterLast('/')
        }
        val segments = relative.split('/')
            .map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
            .filter { it.isNotEmpty() && it != "." }
        require(segments.isNotEmpty() && segments.none { it == ".." || it.contains('\u0000') }) {
            "Ungültiger Dateipfad im Manifest: $file"
        }
        return segments.joinToString("/")
    }

    private companion object {
        /** Leerzeichen & Co. tolerieren, die Anwender gern in Dateinamen verwenden. */
        fun encode(s: String): String = buildString {
            for (c in s) when (c) {
                ' ' -> append("%20")
                '"' -> append("%22")
                '<' -> append("%3C")
                '>' -> append("%3E")
                '\\' -> append('/')
                '^' -> append("%5E")
                '`' -> append("%60")
                '{' -> append("%7B")
                '|' -> append("%7C")
                '}' -> append("%7D")
                else -> append(c)
            }
        }
    }
}
