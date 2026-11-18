package de.soenkejacobs.visitenkarte

import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.LOGO_FILE_KEY
import de.soenkejacobs.visitenkarte.data.PHOTO_FILE_KEY
import de.soenkejacobs.visitenkarte.data.VCard
import de.soenkejacobs.visitenkarte.data.displayEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class DisplayEntriesTest {
    private val vcf = File("../server-beispiel/geschaeftlich.vcf")

    @Test
    fun logoAndPhotoFilesAreSelectable() {
        val card = CardItem("business", "Geschäftlich", VCard.parse(vcf.readText()), vcf, File("foto.jpg"), File("logo.png"), null)
        val entries = card.displayEntries()
        assertEquals(listOf(PHOTO_FILE_KEY, LOGO_FILE_KEY), entries.take(2).map { it.key })
        assertEquals(listOf("Foto", "Logo"), entries.take(2).map { it.label })
        // Danach alle vCard-Angaben
        assertEquals(card.vcard.shareableEntries(), entries.drop(2))
    }

    @Test
    fun noFileEntriesWithoutFiles() {
        val card = CardItem("business", "Geschäftlich", VCard.parse(vcf.readText()), vcf, null, null, null)
        assertFalse(card.displayEntries().any { it.key == PHOTO_FILE_KEY || it.key == LOGO_FILE_KEY })
    }

    @Test
    fun hidingOrgAndAddressRemovesThemFromDisplay() {
        val v = VCard.parse(vcf.readText())
        val hide = v.shareableEntries().filter { it.label == "Firma" || it.label.startsWith("Adresse") }.map { it.key }.toSet()
        val shown = VCard.parse(v.filteredText(hide))
        assertEquals(null, shown.organization)
        assertEquals(0, shown.addresses.size)
        assertEquals("Sönke Jacobs", shown.formattedName)
        assertEquals(v.phones, shown.phones)
    }
}
