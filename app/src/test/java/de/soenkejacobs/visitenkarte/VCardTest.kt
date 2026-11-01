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
}
