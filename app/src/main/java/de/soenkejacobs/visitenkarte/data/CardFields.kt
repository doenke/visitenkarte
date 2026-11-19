package de.soenkejacobs.visitenkarte.data

/**
 * Themen, nach denen die Angaben einer Visitenkarte gruppiert werden.
 * Alle Angaben eines Themas stehen immer zusammen und werden nie getrennt umgebrochen.
 *
 * @property header gehört in den Kopfbereich unter dem Namen (Titel, Firma …)
 * @property small Standard: auf der kleinen Karte (Hochformat)
 * @property large Standard: auf der großen Karte (Vollbild quer)
 */
enum class FieldKind(val title: String, val header: Boolean = false, val small: Boolean = false, val large: Boolean = false) {
    NICKNAME("Spitzname", header = true, small = true, large = true),
    TITLE("Titel", header = true, small = true, large = true),
    ROLE("Rolle", header = true, small = true, large = true),
    ORG("Firma", header = true, small = true, large = true),
    PHONE("Telefon", small = true, large = true),
    EMAIL("E-Mail", small = true, large = true),
    WEB("Web", large = true),
    ADDRESS("Adresse", small = true, large = true),
    MESSENGER("Messenger & Profile", large = true),
    DATE("Daten"),
    RELATED("Kontakte"),
    GEO("Standort"),
    LANGUAGE("Sprachen"),
    TIMEZONE("Zeitzone"),
    GENDER("Geschlecht"),
    EXPERTISE("Fachgebiete"),
    INTEREST("Interessen & Hobbys"),
    CATEGORIES("Kategorien"),
    NOTE("Notiz"),
    OTHER("Weitere Angaben"),
}

/** Kleine Karte (Hochformat) oder große Karte (Vollbild quer). */
enum class CardSize(val label: String) {
    SMALL("Klein"),
    LARGE("Groß"),
}

/**
 * Eine anzeigbare Angabe.
 * @property lines Anzeigetext (Adresse mehrzeilig, sonst meist eine Zeile)
 * @property uri Aktion beim Antippen (tel:, mailto:, https:, geo: …), sonst null
 */
data class FieldItem(
    val key: String,
    val kind: FieldKind,
    val label: String,
    val lines: List<String>,
    val uri: String? = null,
)

/** Alle Angaben eines Themas, in Datei-Reihenfolge. */
data class FieldGroup(val id: String, val kind: FieldKind, val title: String, val items: List<FieldItem>)

/**
 * Zerlegt eine vCard generisch in Themen. Jede Eigenschaft des vCard-Standards wird dargestellt;
 * Unbekanntes (z. B. `X-…`) landet als „Bezeichnung: Wert“ in einem eigenen Thema.
 * Die Themen erscheinen in der Reihenfolge ihres ersten Vorkommens in der Datei.
 */
object CardFields {
    /** Werden separat dargestellt (Name, Bilder) oder sind nicht sinnvoll anzeigbar. */
    private val NOT_DISPLAYED = setOf("N", "FN", "PHOTO", "LOGO", "SOUND", "KEY", "BEGIN", "END", "VERSION")

    fun groups(v: VCard): List<FieldGroup> {
        val groups = linkedMapOf<String, MutableList<FieldItem>>()
        val titles = mutableMapOf<String, Pair<FieldKind, String>>()
        for (p in v.properties) {
            if (p.name in NOT_DISPLAYED || p.name in VCard.TECHNICAL_PROPERTIES) continue
            val item = item(v, p) ?: continue
            val id = if (item.kind == FieldKind.OTHER) "OTHER:${p.name}" else item.kind.name
            groups.getOrPut(id) { mutableListOf() } += item
            titles.getOrPut(id) { item.kind to if (item.kind == FieldKind.OTHER) prettyName(p.name) else item.kind.title }
        }
        return groups.map { (id, items) ->
            val (kind, title) = titles.getValue(id)
            FieldGroup(id, kind, title, items)
        }
    }

    private fun item(v: VCard, p: VCardProperty): FieldItem? {
        val key = VCard.entryKey(p)
        val value = p.value.trim()
        fun one(kind: FieldKind, label: String, text: String = value, uri: String? = null) =
            text.takeIf { it.isNotBlank() }?.let { FieldItem(key, kind, label, listOf(it), uri) }

        return when (p.name) {
            "NICKNAME" -> one(FieldKind.NICKNAME, "Spitzname")
            "TITLE" -> one(FieldKind.TITLE, "Titel")
            "ROLE" -> one(FieldKind.ROLE, "Rolle")
            "ORG" -> one(FieldKind.ORG, "Firma", p.components.filter { it.isNotBlank() }.joinToString(" · "))
            "TEL" -> {
                val number = value.removePrefix("tel:").removePrefix("TEL:")
                one(FieldKind.PHONE, v.labelFor(p, phone = true), number, "tel:" + number.filter { it.isDigit() || it == '+' })
            }
            "EMAIL" -> {
                val mail = value.removePrefix("mailto:")
                one(FieldKind.EMAIL, v.labelFor(p), mail, "mailto:$mail")
            }
            "URL" -> one(
                FieldKind.WEB, v.labelFor(p, fallback = "Website"),
                value.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                if (value.contains("://")) value else "https://$value",
            )
            "X-SOCIALPROFILE" -> one(
                FieldKind.MESSENGER, v.groupLabel(p) ?: v.customType(p) ?: "Profil",
                value.removePrefix("https://").removePrefix("http://").removePrefix("x-apple:").trimEnd('/'),
                value.takeIf { it.startsWith("http") },
            )
            "IMPP" -> messenger(v, p, key)
            "ADR" -> address(v, p, key)
            "BDAY" -> one(FieldKind.DATE, "Geburtstag", VCard.formatDate(value))
            "ANNIVERSARY", "X-ANNIVERSARY" -> one(FieldKind.DATE, "Jahrestag", VCard.formatDate(value))
            "DEATHDATE" -> one(FieldKind.DATE, "Todestag", VCard.formatDate(value))
            "X-ABDATE" -> one(FieldKind.DATE, v.groupLabel(p) ?: "Datum", VCard.formatDate(value))
            "RELATED", "X-ABRELATEDNAMES" -> one(FieldKind.RELATED, v.groupLabel(p) ?: relation(p), value.removePrefix("urn:uuid:"))
            "GEO" -> geo(p, key)
            "LANG" -> one(FieldKind.LANGUAGE, if (p.params["PREF"] != null || "pref" in p.types) "Bevorzugt" else "Sprache", language(value))
            "TZ" -> one(FieldKind.TIMEZONE, "Zeitzone")
            "GENDER" -> one(FieldKind.GENDER, "Geschlecht", gender(p))
            "EXPERTISE" -> one(FieldKind.EXPERTISE, level(p) ?: "Fachgebiet")
            "HOBBY" -> one(FieldKind.INTEREST, "Hobby")
            "INTEREST" -> one(FieldKind.INTEREST, "Interesse")
            "CATEGORIES" -> one(FieldKind.CATEGORIES, "Kategorien", p.value.split(',').map { it.trim() }.filter { it.isNotBlank() }.joinToString(", "))
            "NOTE" -> value.takeIf { it.isNotBlank() }?.let { FieldItem(key, FieldKind.NOTE, "Notiz", it.lines().map(String::trim).filter(String::isNotBlank)) }
            "LABEL" -> value.takeIf { it.isNotBlank() }?.let { FieldItem(key, FieldKind.ADDRESS, v.labelFor(p, fallback = "Adresse"), it.lines().map(String::trim).filter(String::isNotBlank)) }
            else -> one(FieldKind.OTHER, v.groupLabel(p) ?: prettyName(p.name))
        }
    }

    private fun address(v: VCard, p: VCardProperty, key: String): FieldItem? {
        // Postfach; Zusatz; Straße; Ort; Region; PLZ; Land
        val c = p.components + List(7) { "" }
        val street = listOf(c[0], c[1], c[2]).filter { it.isNotBlank() }.joinToString(", ")
        val city = listOf(listOf(c[5], c[3]).filter { it.isNotBlank() }.joinToString(" "), c[4]).filter { it.isNotBlank() }.joinToString(", ")
        val domestic = c[6].isBlank() || c[6].equals("Deutschland", true) || c[6].equals("Germany", true)
        val lines = listOf(street, city, if (domestic) "" else c[6]).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val query = listOf(c[2], c[5], c[3], c[6]).filter { it.isNotBlank() }.joinToString(", ")
        return FieldItem(key, FieldKind.ADDRESS, v.labelFor(p, fallback = "Adresse"), lines, "geo:0,0?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
    }

    private fun messenger(v: VCard, p: VCardProperty, key: String): FieldItem? {
        val value = p.value.trim()
        if (value.isBlank()) return null
        val scheme = value.substringBefore(':', "").lowercase()
        val service = v.groupLabel(p) ?: v.customType(p) ?: when (scheme) {
            "sgnl", "signal" -> "Signal"
            "xmpp" -> "XMPP"
            "matrix" -> "Matrix"
            "tg", "telegram" -> "Telegram"
            "whatsapp" -> "WhatsApp"
            "threema" -> "Threema"
            "skype" -> "Skype"
            "sip" -> "SIP"
            "msteams" -> "Teams"
            else -> "Messenger"
        }
        val shown = when {
            "#p/" in value -> value.substringAfter("#p/")
            "://" in value -> value.substringAfter("://")
            ":" in value -> value.substringAfter(':')
            else -> value
        }.trimEnd('/')
        return FieldItem(key, FieldKind.MESSENGER, service, listOf(shown), value.takeIf { scheme.isNotBlank() })
    }

    private fun geo(p: VCardProperty, key: String): FieldItem? {
        val raw = p.value.trim().removePrefix("geo:")
        val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(raw).map { it.value }.take(2).toList()
        if (nums.size < 2) return raw.takeIf { it.isNotBlank() }?.let { FieldItem(key, FieldKind.GEO, "Standort", listOf(it)) }
        val (lat, lon) = nums
        return FieldItem(key, FieldKind.GEO, "Koordinaten", listOf("$lat, $lon"), "geo:$lat,$lon?q=$lat,$lon")
    }

    private fun relation(p: VCardProperty): String = when (p.types.firstOrNull()) {
        "spouse" -> "Ehepartner"
        "sweetheart" -> "Partner"
        "child" -> "Kind"
        "parent" -> "Elternteil"
        "sibling" -> "Geschwister"
        "friend" -> "Freund"
        "colleague", "co-worker" -> "Kollege"
        "emergency" -> "Notfallkontakt"
        "agent", "assistant" -> "Assistenz"
        else -> "Kontakt"
    }

    private fun gender(p: VCardProperty): String {
        val c = p.components
        val sex = when (c.firstOrNull()?.uppercase()) {
            "M" -> "männlich"
            "F" -> "weiblich"
            "O" -> "divers"
            "N" -> "keine Angabe"
            "U" -> "unbekannt"
            else -> c.firstOrNull().orEmpty()
        }
        return listOf(sex, c.getOrNull(1).orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
    }

    private fun level(p: VCardProperty): String? = when (p.params["LEVEL"]?.firstOrNull()?.lowercase()) {
        "expert" -> "Experte"
        "moderate" -> "Fortgeschritten"
        "beginner" -> "Einsteiger"
        else -> null
    }

    private fun language(code: String): String = when (code.lowercase().substringBefore('-')) {
        "de" -> "Deutsch"
        "en" -> "Englisch"
        "fr" -> "Französisch"
        "es" -> "Spanisch"
        "it" -> "Italienisch"
        "nl" -> "Niederländisch"
        "da" -> "Dänisch"
        "pl" -> "Polnisch"
        else -> code
    }

    /** `X-MY-FIELD` → „My field“. */
    internal fun prettyName(name: String): String =
        name.removePrefix("X-").replace('-', ' ').replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

    /** Steht eine Angabe dieses Themas standardmäßig auf der kleinen bzw. großen Karte? */
    fun defaultOn(kind: FieldKind, size: CardSize): Boolean = if (size == CardSize.SMALL) kind.small else kind.large
}
