package de.soenkejacobs.visitenkarte.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Merkt sich pro Karte, welche Angaben beim Teilen weggelassen werden
 * (gilt für QR-Code, NFC, Datei, Quick Share und Widget).
 */
class ShareFilter(context: Context) {
    private val prefs = context.getSharedPreferences("share_filter", Context.MODE_PRIVATE)

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

    /** Kompakte vCard für QR-Code und NFC (ohne Bilder). */
    fun compactText(card: CardItem): String = card.vcard.filteredText(excludedFor(card.id), compact = true)
}
