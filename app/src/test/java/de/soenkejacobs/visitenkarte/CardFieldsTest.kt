package de.soenkejacobs.visitenkarte

import de.soenkejacobs.visitenkarte.data.CardFields
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.CardLayout
import de.soenkejacobs.visitenkarte.data.FieldKind
import de.soenkejacobs.visitenkarte.data.LOGO_FILE_KEY
import de.soenkejacobs.visitenkarte.data.PHOTO_FILE_KEY
import de.soenkejacobs.visitenkarte.data.CardSize
import de.soenkejacobs.visitenkarte.data.DisplayOverrides
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
    fun layoutDefaultsPerCardSize() {
        val card = CardItem("x", "Test", mixed, File("x.vcf"), File("foto.jpg"), File("logo.png"), null)
        val small = CardLayout.of(card, DisplayOverrides(), CardSize.SMALL)
        val large = CardLayout.of(card, DisplayOverrides(), CardSize.LARGE)
        assertEquals(listOf(FieldKind.ORG), small.header.map { it.kind })
        assertEquals(listOf("PHONE", "EMAIL"), small.groups.map { it.id })
        assertEquals(listOf("PHONE", "EMAIL", "WEB", "MESSENGER"), large.groups.map { it.id })
        assertTrue(small.showPhoto && small.showLogo && large.showLogo)
        // Details: immer alles, auch Kopfangaben
        assertEquals(CardFields.groups(mixed).map { it.id }, CardLayout.details(card).map { it.id })
    }

    @Test
    fun smallAndLargeAreConfiguredSeparately() {
        val card = CardItem("x", "Test", mixed, File("x.vcf"), File("foto.jpg"), File("logo.png"), null)
        val groups = CardFields.groups(mixed)
        val tels = groups.single { it.kind == FieldKind.PHONE }.items
        val note = groups.single { it.kind == FieldKind.NOTE }.items.single()
        val org = groups.single { it.kind == FieldKind.ORG }.items.single()
        val o = DisplayOverrides(
            small = mapOf(tels[0].key to false, org.key to false, LOGO_FILE_KEY to false),
            large = mapOf(note.key to true),
        )
        val small = CardLayout.of(card, o, CardSize.SMALL)
        val large = CardLayout.of(card, o, CardSize.LARGE)
        assertEquals(listOf("+49 40 2"), small.groups.single { it.kind == FieldKind.PHONE }.items.map { it.lines.single() })
        assertTrue(small.header.isEmpty())
        assertFalse(small.showLogo)
        // groß unverändert bis auf die zusätzliche Notiz
        assertEquals(2, large.groups.single { it.kind == FieldKind.PHONE }.items.size)
        assertEquals(listOf(FieldKind.ORG), large.header.map { it.kind })
        assertTrue(large.showLogo)
        assertEquals("NOTE", large.groups.last().id)
        assertFalse(small.groups.any { it.kind == FieldKind.NOTE })
    }

    @Test
    fun overridesRoundTrip() {
        val o = DisplayOverrides(small = mapOf("TEL:1" to false, "NOTE:2" to true), large = mapOf("TEL:1" to true))
        assertEquals(o, DisplayOverrides.decode(o.encode()))
        // Schlüssel mit Doppelpunkt und Pipe-freiem Inhalt bleiben erhalten
        assertTrue(o.encode().contains("S0|TEL:1"))
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
