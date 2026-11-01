package de.soenkejacobs.visitenkarte

import de.soenkejacobs.visitenkarte.nfc.Ndef
import de.soenkejacobs.visitenkarte.nfc.Type4TagEmulator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Type4TagTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun shortMimeRecord() {
        val msg = Ndef.mimeMessage("text/vcard", "abc".toByteArray())
        assertArrayEquals(hex("D2 0A 03") + "text/vcard".toByteArray() + "abc".toByteArray(), msg)
    }

    @Test
    fun longMimeRecord() {
        val payload = ByteArray(300) { 'x'.code.toByte() }
        val msg = Ndef.mimeMessage("text/vcard", payload)
        assertEquals(0xC2.toByte(), msg[0])
        assertArrayEquals(hex("00 00 01 2C"), msg.copyOfRange(2, 6))
        assertEquals(1 + 1 + 4 + 10 + 300, msg.size)
    }

    /** Liest das emulierte Tag so, wie es ein Android-/iOS-Lesegerät tun würde. */
    @Test
    fun fullReadSequence() {
        val ndef = Ndef.mimeMessage("text/vcard", ByteArray(600) { (it % 251).toByte() })
        val tag = Type4TagEmulator { ndef }
        val ok = Type4TagEmulator.SW_OK

        assertArrayEquals(ok, tag.process(hex("00 A4 04 00 07 D2760000850101 00")))
        assertArrayEquals(ok, tag.process(hex("00 A4 00 0C 02 E103")))
        val cc = tag.process(hex("00 B0 00 00 0F"))
        assertEquals(17, cc.size)
        assertEquals(0x20, cc[2].toInt())
        val mle = ((cc[3].toInt() and 0xFF) shl 8) or (cc[4].toInt() and 0xFF)

        assertArrayEquals(ok, tag.process(hex("00 A4 00 0C 02 E104")))
        val nlenResp = tag.process(hex("00 B0 00 00 02"))
        val nlen = ((nlenResp[0].toInt() and 0xFF) shl 8) or (nlenResp[1].toInt() and 0xFF)
        assertEquals(ndef.size, nlen)

        val out = java.io.ByteArrayOutputStream()
        var offset = 2
        while (offset < nlen + 2) {
            val le = minOf(mle, nlen + 2 - offset)
            val resp = tag.process(byteArrayOf(0x00, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), le.toByte()))
            assertArrayEquals(ok, resp.copyOfRange(resp.size - 2, resp.size))
            out.write(resp, 0, resp.size - 2)
            offset += resp.size - 2
        }
        assertArrayEquals(ndef, out.toByteArray())
    }

    @Test
    fun inactiveWhenNoPayload() {
        val tag = Type4TagEmulator { null }
        assertArrayEquals(Type4TagEmulator.SW_FILE_NOT_FOUND, tag.process(hex("00 A4 04 00 07 D2760000850101 00")))
        assertArrayEquals(Type4TagEmulator.SW_NOT_ALLOWED, tag.process(hex("00 B0 00 00 02")))
    }
}
