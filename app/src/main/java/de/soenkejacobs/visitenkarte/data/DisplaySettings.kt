package de.soenkejacobs.visitenkarte.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Schlüssel für Foto und Logo aus dem Manifest (nicht Teil der vCard). */
const val PHOTO_FILE_KEY = "__photo_file"
const val LOGO_FILE_KEY = "__logo_file"

/**
 * Merkt sich pro Karte, wo jede Angabe erscheint (Karte / Details / Aus).
 * Gespeichert werden nur Abweichungen vom Standard ([CardFields.defaultPlacement]).
 */
class DisplaySettings(context: Context) {
    private val prefs = context.getSharedPreferences("display_placement", Context.MODE_PRIVATE)

    private val _placements = MutableStateFlow(load(context))
    /** cardId → (Schlüssel → Platzierung) */
    val placements: StateFlow<Map<String, Map<String, Placement>>> = _placements.asStateFlow()

    fun placementsFor(cardId: String): Map<String, Placement> = _placements.value[cardId].orEmpty()

    fun set(cardId: String, values: Map<String, Placement>) {
        prefs.edit().putStringSet(cardId, values.map { (k, v) -> "${v.name}|$k" }.toSet()).apply()
        _placements.value = _placements.value + (cardId to values)
    }

    private fun load(context: Context): Map<String, Map<String, Placement>> {
        migrateFromHiddenOnly(context)
        return prefs.all.mapNotNull { (cardId, v) ->
            val set = (v as? Set<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
            cardId to set.mapNotNull { e ->
                val state = runCatching { Placement.valueOf(e.substringBefore('|')) }.getOrNull() ?: return@mapNotNull null
                e.substringAfter('|') to state
            }.toMap()
        }.toMap()
    }

    /** Version 1.2: nur „ausgeblendet“ gespeichert → als [Placement.HIDDEN] übernehmen. */
    private fun migrateFromHiddenOnly(context: Context) {
        val old = context.getSharedPreferences("display_filter", Context.MODE_PRIVATE)
        if (old.all.isEmpty()) return
        val edit = prefs.edit()
        old.all.forEach { (cardId, v) ->
            val keys = (v as? Set<*>)?.filterIsInstance<String>() ?: return@forEach
            edit.putStringSet(cardId, keys.map { "${Placement.HIDDEN.name}|$it" }.toSet())
        }
        edit.apply()
        old.edit().clear().apply()
    }
}

/** Ergebnis: was auf der Karte steht und was in den Details. */
data class CardLayout(
    val showPhoto: Boolean,
    val showLogo: Boolean,
    /** Titel, Firma … unter dem Namen. */
    val header: List<FieldItem>,
    /** Themen auf der Karte (ohne Kopfbereich). */
    val cardGroups: List<FieldGroup>,
    /** Themen in der Liste unter der Karte: alles Sichtbare, was nicht schon im Kopfbereich steht. */
    val detailGroups: List<FieldGroup>,
) {
    companion object {
        fun of(card: CardItem, placements: Map<String, Placement>): CardLayout {
            fun placement(item: FieldItem) = placements[item.key] ?: CardFields.defaultPlacement(item.kind)
            val groups = CardFields.groups(card.vcard)
            val all = groups.flatMap { it.items }
            val header = all.filter { it.kind.header && placement(it) == Placement.CARD }
            fun filtered(keep: (FieldItem) -> Boolean) =
                groups.mapNotNull { g -> g.items.filter(keep).takeIf { it.isNotEmpty() }?.let { g.copy(items = it) } }

            val inlinePhoto = card.vcard.inlinePhotoKey
            fun imageOnCard(key: String?) = key != null && (placements[key] ?: Placement.CARD) == Placement.CARD
            return CardLayout(
                showPhoto = (card.photoFile != null && imageOnCard(PHOTO_FILE_KEY)) ||
                    (card.photoFile == null && card.vcard.photoBytes != null && imageOnCard(inlinePhoto)),
                showLogo = card.logoFile != null && imageOnCard(LOGO_FILE_KEY),
                header = header,
                cardGroups = filtered { !it.kind.header && placement(it) == Placement.CARD },
                detailGroups = filtered { placement(it) != Placement.HIDDEN && it !in header },
            )
        }
    }
}

/** Ein Eintrag im Dialog „Was wird angezeigt?“. */
data class DisplayChoice(val key: String, val section: String, val label: String, val value: String, val default: Placement, val imageOnly: Boolean)

/** Alle Angaben einer Karte für den Anzeige-Dialog, nach Themen sortiert. */
fun CardItem.displayChoices(): List<DisplayChoice> {
    val images = listOfNotNull(
        photoFile?.let { DisplayChoice(PHOTO_FILE_KEY, "Bilder", "Foto", it.name, Placement.CARD, true) },
        if (photoFile == null) vcard.inlinePhotoKey?.let { DisplayChoice(it, "Bilder", "Foto", "aus der vCard", Placement.CARD, true) } else null,
        logoFile?.let { DisplayChoice(LOGO_FILE_KEY, "Bilder", "Logo", it.name, Placement.CARD, true) },
    )
    return images + CardFields.groups(vcard).flatMap { g ->
        g.items.map { DisplayChoice(it.key, g.title, it.label, it.lines.joinToString(", "), CardFields.defaultPlacement(it.kind), false) }
    }.distinctBy { it.key } // doppelte Zeilen mit identischem Wert teilen sich eine Einstellung
}
