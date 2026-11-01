package de.soenkejacobs.visitenkarte.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Beschreibung der Inhalte auf dem Webserver (`manifest.json`).
 * Alle `file`-Angaben sind relativ zur Manifest-URL.
 */
data class Manifest(
    val cards: List<CardEntry>,
    val documents: List<DocumentEntry>,
) {
    data class CardEntry(
        val id: String,
        val title: String,
        val file: String,
        val color: String?,
        val photo: String?,
        val logo: String?,
    )

    data class DocumentEntry(
        val id: String,
        val title: String,
        val file: String,
        val type: String?,
        val description: String?,
        val icon: String?,
        val assets: List<String>,
    )

    /** Alle Dateien, die beim Synchronisieren geladen werden müssen. */
    val allFiles: List<String>
        get() = (cards.flatMap { listOfNotNull(it.file, it.photo, it.logo) } +
            documents.flatMap { listOf(it.file) + it.assets }).distinct()

    companion object {
        fun parse(json: String): Manifest {
            val root = JSONObject(json)
            val cards = root.optJSONArray("cards").objects().mapIndexed { i, o ->
                val file = o.getString("file")
                CardEntry(
                    id = o.optString("id").ifBlank { "card$i" },
                    title = o.optString("title").ifBlank { file.substringBeforeLast('.') },
                    file = file,
                    color = o.optStringOrNull("color"),
                    photo = o.optStringOrNull("photo"),
                    logo = o.optStringOrNull("logo"),
                )
            }
            val docs = root.optJSONArray("documents").objects().mapIndexed { i, o ->
                val file = o.getString("file")
                DocumentEntry(
                    id = o.optString("id").ifBlank { "doc$i" },
                    title = o.optString("title").ifBlank { file.substringAfterLast('/') },
                    file = file,
                    type = o.optStringOrNull("type"),
                    description = o.optStringOrNull("description"),
                    icon = o.optStringOrNull("icon"),
                    assets = o.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                )
            }
            return Manifest(cards, docs)
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null
    }
}
