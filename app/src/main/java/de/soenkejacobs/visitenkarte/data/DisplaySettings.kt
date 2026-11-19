package de.soenkejacobs.visitenkarte.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Schlüssel für Foto und Logo aus dem Manifest (nicht Teil der vCard). */
const val PHOTO_FILE_KEY = "__photo_file"
const val LOGO_FILE_KEY = "__logo_file"


/**
 * Merkt sich pro Karte, welche Angaben auf der kleinen und der großen Karte stehen.
 * Gespeichert werden nur Abweichungen vom Standard ([CardFields.defaultOn]).
 * Die Details unter der Karte zeigen immer alles.
 */
class DisplaySettings(context: Context) {
    private val prefs = context.getSharedPreferences("display_cards", Context.MODE_PRIVATE)

    private val _overrides = MutableStateFlow(load(context))
    /** cardId → Abweichungen */
    val overrides: StateFlow<Map<String, DisplayOverrides>> = _overrides.asStateFlow()

    fun overridesFor(cardId: String): DisplayOverrides = _overrides.value[cardId] ?: DisplayOverrides()

    fun set(cardId: String, values: DisplayOverrides) {
        prefs.edit().putStringSet(cardId, values.encode()).apply()
        _overrides.value = _overrides.value + (cardId to values)
    }

    private fun load(context: Context): Map<String, DisplayOverrides> {
        migrate(context)
        return prefs.all.mapNotNull { (cardId, v) ->
            val set = (v as? Set<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
            cardId to DisplayOverrides.decode(set.toSet())
        }.toMap()
    }

    /**
     * Ältere Einstellungen übernehmen:
     * 1.2 („ausgeblendet“) → auf beiden Karten aus; 1.3 (Karte/Details/Aus) → Karte = beide an, sonst beide aus.
     */
    private fun migrate(context: Context) {
        val edit = prefs.edit()
        var changed = false
        fun take(name: String, convert: (String) -> List<String>) {
            val old = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            if (old.all.isEmpty()) return
            old.all.forEach { (cardId, v) ->
                if (prefs.contains(cardId)) return@forEach
                val entries = (v as? Set<*>)?.filterIsInstance<String>() ?: return@forEach
                edit.putStringSet(cardId, entries.flatMap(convert).toSet())
                changed = true
            }
            old.edit().clear().apply()
        }
        take("display_placement") { e ->
            val on = e.substringBefore('|') == "CARD"
            val key = e.substringAfter('|')
            listOf(DisplayOverrides.entry(CardSize.SMALL, key, on), DisplayOverrides.entry(CardSize.LARGE, key, on))
        }
        take("display_filter") { key ->
            listOf(DisplayOverrides.entry(CardSize.SMALL, key, false), DisplayOverrides.entry(CardSize.LARGE, key, false))
        }
        if (changed) edit.apply()
    }
}

/** Abweichungen vom Standard für eine Karte. */
data class DisplayOverrides(val small: Map<String, Boolean> = emptyMap(), val large: Map<String, Boolean> = emptyMap()) {
    fun forSize(size: CardSize) = if (size == CardSize.SMALL) small else large

    fun encode(): Set<String> =
        small.map { (k, v) -> entry(CardSize.SMALL, k, v) }.toSet() + large.map { (k, v) -> entry(CardSize.LARGE, k, v) }

    companion object {
        internal fun entry(size: CardSize, key: String, visible: Boolean) =
            (if (size == CardSize.SMALL) "S" else "L") + (if (visible) "1" else "0") + "|" + key

        fun decode(set: Set<String>): DisplayOverrides {
            val small = mutableMapOf<String, Boolean>()
            val large = mutableMapOf<String, Boolean>()
            for (e in set) {
                val head = e.substringBefore('|')
                if (head.length != 2) continue
                val target = if (head[0] == 'S') small else if (head[0] == 'L') large else continue
                target[e.substringAfter('|')] = head[1] == '1'
            }
            return DisplayOverrides(small, large)
        }
    }
}

/** Was auf einer Karte (klein oder groß) steht. */
data class CardLayout(
    val showPhoto: Boolean,
    val showLogo: Boolean,
    /** Titel, Firma … unter dem Namen. */
    val header: List<FieldItem>,
    /** Themen auf der Karte (ohne Kopfbereich), in Datei-Reihenfolge. */
    val groups: List<FieldGroup>,
) {
    companion object {
        fun of(card: CardItem, overrides: DisplayOverrides, size: CardSize): CardLayout {
            val o = overrides.forSize(size)
            fun visible(item: FieldItem) = o[item.key] ?: CardFields.defaultOn(item.kind, size)
            fun image(key: String?) = key != null && (o[key] ?: true)
            val all = CardFields.groups(card.vcard)
            return CardLayout(
                showPhoto = (card.photoFile != null && image(PHOTO_FILE_KEY)) ||
                    (card.photoFile == null && card.vcard.photoBytes != null && image(card.vcard.inlinePhotoKey)),
                showLogo = card.logoFile != null && image(LOGO_FILE_KEY),
                header = all.filter { it.kind.header }.flatMap { it.items }.filter(::visible),
                groups = all.filter { !it.kind.header }.mapNotNull { g ->
                    g.items.filter(::visible).takeIf { it.isNotEmpty() }?.let { g.copy(items = it) }
                },
            )
        }

        /** Details unter der Karte: immer alles. */
        fun details(card: CardItem): List<FieldGroup> = CardFields.groups(card.vcard)
    }
}

/** Ein Eintrag im Dialog „Was steht auf den Karten?“. */
data class DisplayChoice(
    val key: String,
    val section: String,
    val label: String,
    val value: String,
    val defaultSmall: Boolean,
    val defaultLarge: Boolean,
) {
    fun default(size: CardSize) = if (size == CardSize.SMALL) defaultSmall else defaultLarge
}

/** Alle Angaben einer Karte für den Dialog, nach Themen sortiert. */
fun CardItem.displayChoices(): List<DisplayChoice> {
    val images = listOfNotNull(
        photoFile?.let { DisplayChoice(PHOTO_FILE_KEY, "Bilder", "Foto", it.name, true, true) },
        if (photoFile == null) vcard.inlinePhotoKey?.let { DisplayChoice(it, "Bilder", "Foto", "aus der vCard", true, true) } else null,
        logoFile?.let { DisplayChoice(LOGO_FILE_KEY, "Bilder", "Logo", it.name, true, true) },
    )
    return images + CardFields.groups(vcard).flatMap { g ->
        g.items.map {
            DisplayChoice(
                it.key, g.title, it.label, it.lines.joinToString(", "),
                CardFields.defaultOn(it.kind, CardSize.SMALL), CardFields.defaultOn(it.kind, CardSize.LARGE),
            )
        }
    }.distinctBy { it.key } // doppelte Zeilen mit identischem Wert teilen sich eine Einstellung
}
