package de.soenkejacobs.visitenkarte

import de.soenkejacobs.visitenkarte.data.ContentPaths
import de.soenkejacobs.visitenkarte.data.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestTest {
    @Test
    fun parsesManifest() {
        val m = Manifest.parse(java.io.File("../server-beispiel/manifest.json").readText())
        assertEquals(2, m.cards.size)
        assertTrue(m.documents.isNotEmpty())
        assertTrue(m.allFiles.contains("geschaeftlich.vcf"))
        // Notfallinfos ohne Sperre, Patientenverfügung gesperrt (Standard)
        assertEquals(false, m.documents.single { it.id == "health" }.lock)
        assertEquals(true, m.documents.single { it.id == "patientenverfuegung" }.lock)
    }

    @Test
    fun resolvesPaths() {
        val p = ContentPaths("https://example.de/karten/manifest.json")
        assertEquals("https://example.de/karten/privat.vcf", p.remoteUrl("privat.vcf"))
        assertEquals(
            "https://example.de/karten/dokumente/Patienten%20verf%C3%BCgung.pdf",
            p.remoteUrl("dokumente/Patienten verfügung.pdf"),
        )
        assertEquals("dokumente/Patienten verfügung.pdf", p.localPath("dokumente/Patienten verfügung.pdf"))
        assertEquals("a/b.css", p.localPath("./a/./b.css"))
        // Außerhalb des Basisordners landet die Datei in _extern statt außerhalb des Speichers
        assertTrue(p.localPath("../../etc/passwd").startsWith("_extern/"))
        assertTrue(p.localPath("https://other.example/x.pdf").startsWith("_extern/"))
    }
}
