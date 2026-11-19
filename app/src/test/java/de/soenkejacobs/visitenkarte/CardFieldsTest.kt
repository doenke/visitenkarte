package de.soenkejacobs.visitenkarte

import de.soenkejacobs.visitenkarte.data.CardFields
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.CardLayout
import de.soenkejacobs.visitenkarte.data.FieldKind
import de.soenkejacobs.visitenkarte.data.LOGO_FILE_KEY
import de.soenkejacobs.visitenkarte.data.PHOTO_FILE_KEY
import de.soenkejacobs.visitenkarte.data.Placement
import de.soenkejacobs.visitenkarte.data.VCard
import de.soenkejacobs.visitenkarte.data.displayChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CardFieldsTest {
    private val mixed = VCard.parse(
        """
        BEGIN:VCARD
        VERSION:3.0
        N:Jacobs;Sönke;;;
        FN:Sönke Jacobs
        ORG:Firma GmbH
        TEL;TYPE=CELL:+49 170 1
        EMAIL;TYPE=WORK:a@firma.example
        TEL;TYPE=WORK:+49 40 2
        item1.EMAIL:b@example.org
        item1.X-ABLabel:Verein
        item2.URL:https://example.org
        item2.X-ABLabel:_${'$'}!<HomePage>!${'$'}_
        IMPP;TYPE=Signal:sgnl://signal.me/#p/+491711234567
        GEO:geo:53.5511,9.9937
        X-SHOE-SIZE:44
        NOTE:Zeile 1\nZeile 2
        PRODID:-//Apple Inc.//iPhone OS//EN
        END:VCARD
        """.trimIndent()
    )

    @Test
    fun groupsKeepTopicsTogetherInFileOrder() {
        val groups = CardFields.groups(mixed)
        assertEquals(
            listOf("ORG", "PHONE", "EMAIL", "WEB", "MESSENGER", "GEO", "OTHER:X-SHOE-SIZE", "NOTE"),
            groups.map { it.id },
        )
        // beide Telefonnummern zusammen, obwohl in der Datei eine Mail dazwischen steht
        assertEquals(listOf("+49 170 1", "+49 40 2"), groups.single { it.kind == FieldKind.PHONE }.items.map { it.lines.single() })
        assertEquals(listOf("tel:+491701", "tel:+49402"), groups.single { it.kind == FieldKind.PHONE }.items.map { it.uri })
    }

    @Test
    fun appleLabelsAndTechnicalLines() {
        val groups = CardFields.groups(mixed)
        val mails = groups.single { it.kind == FieldKind.EMAIL }.items
        assertEquals(listOf("Geschäftlich", "Verein"), mails.map { it.label })
        assertEquals("Website", groups.single { it.kind == FieldKind.WEB }.items.single().label)
        // X-ABLabel und PRODID erscheinen nicht als eigene Angaben
        assertFalse(groups.any { g -> g.id.contains("ABLABEL") || g.id.contains("PRODID") })
        assertFalse(mixed.shareableEntries().any { it.label.contains("ablabel", true) || it.label.contains("prodid", true) })
    }

    @Test
    fun excludingGroupedLineAlsoDropsItsLabel() {
        val key = mixed.shareableEntries().single { it.value == "b@example.org" }.key
        val text = mixed.filteredText(setOf(key))
        assertFalse(text.contains("b@example.org"))
        assertFalse(text.contains("item1.X-ABLabel"))
        assertTrue(text.contains("item2.X-ABLabel"))
    }

    @Test
    fun specialAndUnknownProperties() {
        val groups = CardFields.groups(mixed)
        val signal = groups.single { it.kind == FieldKind.MESSENGER }.items.single()
        assertEquals("Signal", signal.label)
        assertEquals(listOf("+491711234567"), signal.lines)
        assertEquals(listOf("53.5511, 9.9937"), groups.single { it.kind == FieldKind.GEO }.items.single().lines)
        val other = groups.single { it.id == "OTHER:X-SHOE-SIZE" }
        assertEquals("Shoe size", other.title)
        assertEquals(listOf("44"), other.items.single().lines)
        assertEquals(listOf("Zeile 1", "Zeile 2"), groups.single { it.kind == FieldKind.NOTE }.items.single().lines)
    }

    @Test
    fun layoutDefaultsAndOverrides() {
        val card = CardItem("x", "Test", mixed, File("x.vcf"), File("foto.jpg"), File("logo.png"), null)
        val default = CardLayout.of(card, emptyMap())
        assertEquals(listOf(FieldKind.ORG), default.header.map { it.kind })
        assertEquals(listOf("PHONE", "EMAIL", "WEB"), default.cardGroups.map { it.id })
        // Details: alles Sichtbare außer dem Kopfbereich auf der Karte
        assertEquals(listOf("PHONE", "EMAIL", "WEB", "MESSENGER", "GEO", "OTHER:X-SHOE-SIZE", "NOTE"), default.detailGroups.map { it.id })
        assertTrue(default.showPhoto && default.showLogo)

        val tels = CardFields.groups(mixed).single { it.kind == FieldKind.PHONE }.items
        val org = CardFields.groups(mixed).single { it.kind == FieldKind.ORG }.items.single()
        val note = CardFields.groups(mixed).single { it.kind == FieldKind.NOTE }.items.single()
        val custom = CardLayout.of(
            card,
            mapOf(
                tels[0].key to Placement.HIDDEN,
                org.key to Placement.DETAILS,
                note.key to Placement.CARD,
                LOGO_FILE_KEY to Placement.HIDDEN,
            ),
        )
        assertTrue(custom.header.isEmpty())
        assertEquals(listOf("+49 40 2"), custom.cardGroups.single { it.kind == FieldKind.PHONE }.items.map { it.lines.single() })
        assertEquals("NOTE", custom.cardGroups.last().id)
        assertEquals("ORG", custom.detailGroups.first().id)
        assertFalse(custom.detailGroups.flatMap { it.items }.any { it.key == tels[0].key })
        assertFalse(custom.showLogo)
        assertTrue(custom.showPhoto)
    }

    @Test
    fun choicesListImagesFirstThenTopics() {
        val card = CardItem("x", "Test", mixed, File("x.vcf"), File("foto.jpg"), null, null)
        val choices = card.displayChoices()
        assertEquals(PHOTO_FILE_KEY, choices.first().key)
        assertEquals("Bilder", choices.first().section)
        assertEquals(listOf("Bilder", "Firma", "Telefon", "E-Mail", "Web", "Messenger & Profile", "Standort", "Shoe size", "Notiz"), choices.map { it.section }.distinct())
        assertEquals(choices.size, choices.map { it.key }.distinct().size)
    }

    @Test
    fun exampleFilesRenderEveryLine() {
        for (f in listOf("privat.vcf", "geschaeftlich.vcf")) {
            val v = VCard.parse(File("../server-beispiel/$f").readText())
            val shown = CardFields.groups(v).flatMap { it.items }.map { it.key }.toSet()
            val all = v.shareableEntries().map { it.key }.toSet()
            // Alles Teilbare (außer Bildern) wird auch angezeigt
            assertEquals(all, shown)
        }
    }
}
