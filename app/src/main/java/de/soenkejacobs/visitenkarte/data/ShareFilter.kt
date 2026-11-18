package de.soenkejacobs.visitenkarte.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Merkt sich pro Karte, welche Angaben weggelassen werden.
 * Zwei Instanzen: „Teilen“ (QR-Code, NFC, Datei, Quick Share, Widget) und
 * „Anzeige“ (Karte und Kontaktdetails in der App, Vollbild im Querformat).
 */
class ShareFilter(context: Context, prefsName: String = "share_filter") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val _excluded = MutableStateFlow(load())
    val excluded: StateFlow<Map<String, Set<String>>> = _excluded.asStateFlow()

    private fun load(): Map<String, Set<String>> =
        prefs.all.mapNotNull { (k, v) -> (v as? Set<*>)?.let { k to it.filterIsInstance<String>().toSet() } }.toMap()

    fun excludedFor(cardId: String): Set<String> = _excluded.value[cardId].orEmpty()

    fun setExcluded(cardId: String, keys: Set<String>) {
        prefs.edit().putStringSet(cardId, keys).apply()
        _excluded.value = _excluded.value + (cardId to keys)
    }

    /** vCard zum Teilen als Datei (mit Foto). */
    fun fullText(card: CardItem): String = card.vcard.filteredText(excludedFor(card.id))

    /** Karte, wie sie angezeigt wird (ohne ausgeblendete Angaben, Foto/Logo ggf. entfernt). */
    fun displayed(card: CardItem): CardItem {
        val excluded = excludedFor(card.id)
        if (excluded.isEmpty()) return card
        return card.copy(
            vcard = VCard.parse(card.vcard.filteredText(excluded)),
            photoFile = card.photoFile.takeUnless { PHOTO_FILE_KEY in excluded },
            logoFile = card.logoFile.takeUnless { LOGO_FILE_KEY in excluded },
        )
    }

    /** Kompakte vCard für QR-Code und NFC (ohne Bilder). */
    fun compactText(card: CardItem): String = card.vcard.filteredText(excludedFor(card.id), compact = true)
}

/** Schlüssel für Foto und Logo aus dem Manifest (nicht Teil der vCard). */
const val PHOTO_FILE_KEY = "__photo_file"
const val LOGO_FILE_KEY = "__logo_file"

/** Angaben, die man in der Anzeige ausblenden kann: alle vCard-Angaben plus Foto/Logo-Datei. */
fun CardItem.displayEntries(): List<ShareEntry> =
    listOfNotNull(
        photoFile?.let { ShareEntry(PHOTO_FILE_KEY, "Foto", it.name) },
        logoFile?.let { ShareEntry(LOGO_FILE_KEY, "Logo", it.name) },
    ) + vcard.shareableEntries()
