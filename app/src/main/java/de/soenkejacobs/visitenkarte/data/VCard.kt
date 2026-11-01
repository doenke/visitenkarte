package de.soenkejacobs.visitenkarte.data

import java.nio.charset.Charset
import java.util.Base64

/** Eine einzelne Eigenschaft einer vCard, z. B. `TEL;TYPE=work:+49 ...`. */
data class VCardProperty(
    val name: String,
    val params: Map<String, List<String>>,
    val rawValue: String,
) {
    val types: List<String>
        get() = params["TYPE"].orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() } +
            params[""].orEmpty().map { it.lowercase() } // vCard 2.1: TEL;WORK;VOICE

    /** Wert mit aufgelösten Escapes (\n, \, \; \\). */
    val value: String get() = VCard.unescape(rawValue)

    /** Strukturierte Werte (N, ADR, ORG) – nach `;` getrennt. */
    val components: List<String> get() = VCard.splitUnescaped(rawValue, ';').map(VCard::unescape)
}

data class ContactEntry(val label: String, val value: String)

data class PostalAddress(val label: String, val lines: List<String>) {
    val singleLine: String get() = lines.joinToString(", ")
}

/**
 * Minimaler, robuster vCard-Parser (2.1, 3.0, 4.0) – genug, um Visitenkarten anzuzeigen.
 * Der Originaltext wird zum Teilen unverändert weitergegeben.
 */
class VCard private constructor(val raw: String, val properties: List<VCardProperty>) {

    private fun first(name: String) = properties.firstOrNull { it.name == name }
    private fun all(name: String) = properties.filter { it.name == name }

    val formattedName: String
        get() = first("FN")?.value?.takeIf { it.isNotBlank() }
            ?: first("N")?.components?.let { c ->
                listOfNotNull(c.getOrNull(3), c.getOrNull(1), c.getOrNull(0), c.getOrNull(4))
                    .filter { it.isNotBlank() }.joinToString(" ")
            }.orEmpty()

    val organization: String? get() = first("ORG")?.components?.filter { it.isNotBlank() }?.joinToString(" · ")?.ifBlank { null }
    val title: String? get() = (first("TITLE") ?: first("ROLE"))?.value?.ifBlank { null }
    val note: String? get() = first("NOTE")?.value?.ifBlank { null }
    val birthday: String? get() = first("BDAY")?.value?.ifBlank { null }

    val phones: List<ContactEntry> get() = all("TEL").map { ContactEntry(labelFor(it, phone = true), cleanUri(it.value, "tel:")) }
    val emails: List<ContactEntry> get() = all("EMAIL").map { ContactEntry(labelFor(it), cleanUri(it.value, "mailto:")) }
    val urls: List<ContactEntry> get() = all("URL").map { ContactEntry(labelFor(it, fallback = "Web"), it.value) }

    val addresses: List<PostalAddress>
        get() = all("ADR").map { p ->
            // PO-Box; Extended; Street; Locality; Region; Postal code; Country
            val c = p.components + List(7) { "" }
            val cityLine = listOf(c[5], c[3]).filter { it.isNotBlank() }.joinToString(" ")
            val lines = listOf(c[0], c[1], c[2], cityLine, c[4], c[6]).filter { it.isNotBlank() }
            PostalAddress(labelFor(p, fallback = "Adresse"), lines)
        }.filter { it.lines.isNotEmpty() }

    /** Inline-Foto (Base64 oder data:-URI), falls vorhanden. */
    val photoBytes: ByteArray?
        get() {
            val p = first("PHOTO") ?: return null
            val v = p.rawValue.trim()
            return runCatching {
                when {
                    v.startsWith("data:") -> Base64.getMimeDecoder().decode(v.substringAfter("base64,", ""))
                    p.params["ENCODING"]?.any { it.equals("b", true) || it.equals("base64", true) } == true ->
                        Base64.getMimeDecoder().decode(v)
                    else -> null
                }
            }.getOrNull()
        }

    /**
     * vCard ohne Foto – kompakt genug für QR-Code und NFC.
     */
    fun compactText(): String = buildString {
        for (line in unfold(raw)) {
            val name = propertyName(line)
            if (name == "PHOTO" || name == "LOGO" || name == "SOUND" || name == "KEY") continue
            if (line.isBlank()) continue
            append(line).append("\r\n")
        }
    }

    private fun labelFor(p: VCardProperty, phone: Boolean = false, fallback: String? = null): String {
        val t = p.types
        val base = when {
            "cell" in t || "mobile" in t -> "Mobil"
            "work" in t -> "Geschäftlich"
            "home" in t -> "Privat"
            "fax" in t -> "Fax"
            else -> null
        }
        val label = when {
            phone && "fax" in t && base != "Fax" -> "Fax ${base.orEmpty()}".trim()
            phone && "cell" in t && "work" in t -> "Mobil geschäftlich"
            phone && "cell" in t && "home" in t -> "Mobil privat"
            else -> base
        }
        return label ?: fallback ?: if (phone) "Telefon" else "E-Mail"
    }

    companion object {
        fun parse(text: String): VCard {
            val props = unfold(text).mapNotNull(::parseLine)
            return VCard(text, props)
        }

        private fun cleanUri(v: String, scheme: String) =
            if (v.startsWith(scheme, ignoreCase = true)) v.substring(scheme.length) else v

        /** Zeilen entfalten (RFC 6350 3.2) und Quoted-Printable-Fortsetzungen (vCard 2.1) zusammenfügen. */
        internal fun unfold(text: String): List<String> {
            val result = mutableListOf<String>()
            for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                    result[result.lastIndex] = result.last() + line.substring(1)
                } else if (result.isNotEmpty() && result.last().endsWith("=") &&
                    result.last().contains("QUOTED-PRINTABLE", ignoreCase = true)
                ) {
                    result[result.lastIndex] = result.last().dropLast(1) + line
                } else {
                    result += line
                }
            }
            return result
        }

        private fun propertyName(line: String): String {
            val head = line.substringBefore(':').substringBefore(';')
            return head.substringAfterLast('.').uppercase()
        }

        private fun parseLine(line: String): VCardProperty? {
            val colon = findValueColon(line) ?: return null
            val head = line.substring(0, colon)
            var value = line.substring(colon + 1)
            val parts = splitUnescaped(head, ';', respectQuotes = true)
            val name = parts.first().substringAfterLast('.').uppercase()
            if (name.isBlank() || name == "BEGIN" || name == "END" || name == "VERSION") return null
            val params = mutableMapOf<String, MutableList<String>>()
            for (p in parts.drop(1)) {
                val key = if (p.contains('=')) p.substringBefore('=').uppercase() else ""
                val v = if (p.contains('=')) p.substringAfter('=').trim('"') else p
                params.getOrPut(key) { mutableListOf() } += v
            }
            if (params["ENCODING"]?.any { it.equals("QUOTED-PRINTABLE", true) } == true ||
                params[""]?.any { it.equals("QUOTED-PRINTABLE", true) } == true
            ) {
                val charset = params["CHARSET"]?.firstOrNull()?.let { runCatching { Charset.forName(it) }.getOrNull() }
                    ?: Charsets.UTF_8
                value = decodeQuotedPrintable(value, charset)
            }
            return VCardProperty(name, params, value)
        }

        private fun findValueColon(line: String): Int? {
            var inQuotes = false
            for (i in line.indices) {
                when (line[i]) {
                    '"' -> inQuotes = !inQuotes
                    ':' -> if (!inQuotes) return i
                }
            }
            return null
        }

        internal fun splitUnescaped(s: String, sep: Char, respectQuotes: Boolean = false): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var i = 0
            var inQuotes = false
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' && i + 1 < s.length -> { cur.append(c).append(s[i + 1]); i++ }
                    respectQuotes && c == '"' -> { inQuotes = !inQuotes; cur.append(c) }
                    c == sep && !inQuotes -> { out += cur.toString(); cur.clear() }
                    else -> cur.append(c)
                }
                i++
            }
            out += cur.toString()
            return out
        }

        internal fun unescape(s: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (val n = s[i + 1]) {
                        'n', 'N' -> sb.append('\n')
                        else -> sb.append(n)
                    }
                    i += 2
                } else {
                    sb.append(c); i++
                }
            }
            return sb.toString()
        }

        private fun decodeQuotedPrintable(s: String, charset: Charset): String {
            val bytes = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '=' && i + 2 < s.length) {
                    val b = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (b != null) {
                        bytes.write(b); i += 3; continue
                    }
                }
                bytes.write(c.code and 0xFF)
                i++
            }
            return String(bytes.toByteArray(), charset)
        }
    }
}
