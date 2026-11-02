package de.soenkejacobs.visitenkarte

import de.soenkejacobs.visitenkarte.data.VCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {
    private val sample = """
        BEGIN:VCARD
        VERSION:3.0
        N:Jacobs;Sönke;;;
        FN:Sönke Jacobs
        ORG:Beispiel GmbH;Entwicklung
        TITLE:Berater
        TEL;TYPE=WORK,VOICE:+49 40 123456
        TEL;TYPE=CELL:+49 170 1234567
        EMAIL;TYPE=INTERNET,WORK:soenke@example.de
        URL:https://example.de
        ADR;TYPE=WORK:;;Musterstraße 1;Hamburg;;20095;Deutschland
        NOTE:Zeile 1\nZeile 2\, mit Komma
        PHOTO;ENCODING=b;TYPE=JPEG:/9j/4AAQSkZJRg
         ABAQAAAQABAAD
        END:VCARD
    """.trimIndent()

    @Test
    fun parsesFields() {
        val v = VCard.parse(sample)
        assertEquals("Sönke Jacobs", v.formattedName)
        assertEquals("Beispiel GmbH · Entwicklung", v.organization)
        assertEquals("Berater", v.title)
        assertEquals(listOf("Geschäftlich", "Mobil"), v.phones.map { it.label })
        assertEquals("soenke@example.de", v.emails.single().value)
        assertEquals("https://example.de", v.urls.single().value)
        assertEquals(listOf("Musterstraße 1", "20095 Hamburg", "Deutschland"), v.addresses.single().lines)
        assertEquals("Zeile 1\nZeile 2, mit Komma", v.note)
        assertNotNull(v.photoBytes)
    }

    @Test
    fun compactTextDropsPhoto() {
        val compact = VCard.parse(sample).compactText()
        assertFalse(compact.contains("PHOTO"))
        assertTrue(compact.startsWith("BEGIN:VCARD\r\n"))
        assertTrue(compact.trimEnd().endsWith("END:VCARD"))
        assertTrue(compact.contains("FN:Sönke Jacobs"))
    }

    @Test
    fun nameFallbackAndVcard21QuotedPrintable() {
        val v = VCard.parse(
            "BEGIN:VCARD\nVERSION:2.1\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:M=C3=BCller;J=C3=BC=\nrgen\nTEL;WORK;VOICE:123\nitem1.EMAIL:a@b.de\nEND:VCARD"
        )
        assertEquals("Jürgen Müller", v.formattedName)
        assertEquals("Geschäftlich", v.phones.single().label)
        assertEquals("a@b.de", v.emails.single().value)
    }

    @Test
    fun exampleCardsFromServerFolder() {
        val priv = VCard.parse(java.io.File("../server-beispiel/privat.vcf").readText())
        assertEquals("12.04.1985", priv.birthdayFormatted)
        assertEquals("Sönke", priv.nickname)
        assertEquals(listOf("Mobil privat", "Privat"), priv.phones.map { it.label })
        assertEquals(listOf("Privat", "Familie"), priv.emails.map { it.label })
        assertEquals(listOf("Privat", "Mastodon", "Instagram"), priv.urls.map { it.label })
        assertEquals(listOf("Musterweg 12", "22085 Hamburg", "Deutschland"), priv.addresses.single().lines)

        val biz = VCard.parse(java.io.File("../server-beispiel/geschaeftlich.vcf").readText())
        assertEquals(listOf("Geschäftlich", "Mobil geschäftlich", "Fax Geschäftlich"), biz.phones.map { it.label })
        assertEquals(listOf("Geschäftlich", "LinkedIn", "Termin"), biz.urls.map { it.label })
        assertEquals(listOf("3. OG", "Musterstraße 1", "20095 Hamburg", "Deutschland"), biz.addresses.single().lines)
        assertEquals("Firma GmbH · Beratung & Entwicklung", biz.organization)
        biz.addresses.single().let {
            assertEquals(listOf("3. OG", "Musterstraße 1"), it.streetLines)
            assertEquals("20095 Hamburg", it.cityLine)
            assertTrue(it.isDomestic)
        }
        // Kompakt genug für einen gut scanbaren QR-Code
        assertTrue(priv.compactText().length < 1000 && biz.compactText().length < 1000)
    }

    @Test
    fun birthdayWithoutYear() {
        assertEquals("24.12.", VCard.parse("BEGIN:VCARD\nBDAY:--1224\nEND:VCARD").birthdayFormatted)
    }

    @Test
    fun filteredTextOmitsSelectedEntries() {
        val v = VCard.parse(java.io.File("../server-beispiel/privat.vcf").readText())
        val entries = v.shareableEntries()
        val adr = entries.single { it.label.startsWith("Adresse") }
        val bday = entries.single { it.label == "Geburtstag" }
        assertEquals("Musterweg 12, Hamburg, 22085, Deutschland", adr.value)
        assertEquals("12.04.1985", bday.value)
        // Name ist nie abwählbar
        assertFalse(entries.any { it.label == "N" || it.label == "Fn" })

        val text = v.filteredText(setOf(adr.key, bday.key))
        assertFalse(text.contains("ADR"))
        assertFalse(text.contains("BDAY"))
        assertTrue(text.contains("FN:Sönke Jacobs"))
        assertTrue(text.contains("TEL;TYPE=CELL,HOME,PREF:+49 171 1234567"))
        assertTrue(text.startsWith("BEGIN:VCARD") && text.trimEnd().endsWith("END:VCARD"))
        // Schlüssel sind stabil (gleiche Datei -> gleiche Schlüssel)
        assertEquals(entries.map { it.key }, VCard.parse(java.io.File("../server-beispiel/privat.vcf").readText()).shareableEntries().map { it.key })
    }
}
