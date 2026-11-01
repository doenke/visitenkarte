package de.soenkejacobs.visitenkarte.nfc

import java.io.ByteArrayOutputStream

/** Baut NDEF-Nachrichten ohne Android-Abhängigkeit (testbar). */
object Ndef {
    private const val TNF_MIME = 0x02

    /** Eine NDEF-Nachricht mit genau einem MIME-Record. */
    fun mimeMessage(mimeType: String, payload: ByteArray): ByteArray {
        val type = mimeType.toByteArray(Charsets.US_ASCII)
        val short = payload.size < 256
        val out = ByteArrayOutputStream()
        var header = 0x80 or 0x40 or TNF_MIME // MB | ME
        if (short) header = header or 0x10 // SR
        out.write(header)
        out.write(type.size)
        if (short) {
            out.write(payload.size)
        } else {
            out.write(payload.size ushr 24 and 0xFF)
            out.write(payload.size ushr 16 and 0xFF)
            out.write(payload.size ushr 8 and 0xFF)
            out.write(payload.size and 0xFF)
        }
        out.write(type)
        out.write(payload)
        return out.toByteArray()
    }
}

/**
 * Emuliert ein NFC Forum Type 4 Tag (NDEF Mapping 2.0), das nur gelesen werden kann.
 * Wird vom [CardHceService] verwendet; Lesegeräte (andere Smartphones) sehen eine
 * ganz normale NFC-Visitenkarte.
 */
class Type4TagEmulator(private val ndefMessage: () -> ByteArray?) {
    private enum class Selected { NONE, APP, CC, NDEF }

    private var selected = Selected.NONE
    private var ndefFile: ByteArray = ByteArray(0)

    fun reset() {
        selected = Selected.NONE
    }

    fun process(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_WRONG_LENGTH
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        return when (ins) {
            0xA4 -> select(apdu, p1)
            0xB0 -> readBinary(apdu, p1, p2)
            0xD6 -> SW_SECURITY // Schreiben nicht erlaubt
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(apdu: ByteArray, p1: Int): ByteArray {
        if (apdu.size < 5) return SW_WRONG_LENGTH
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return SW_WRONG_LENGTH
        val data = apdu.copyOfRange(5, 5 + lc)
        return when {
            p1 == 0x04 && data.contentEquals(NDEF_AID) -> {
                val msg = ndefMessage() ?: return SW_FILE_NOT_FOUND
                ndefFile = byteArrayOf((msg.size ushr 8).toByte(), msg.size.toByte()) + msg
                selected = Selected.APP
                SW_OK
            }
            p1 == 0x00 && selected != Selected.NONE && data.contentEquals(CC_FILE_ID) -> { selected = Selected.CC; SW_OK }
            p1 == 0x00 && selected != Selected.NONE && data.contentEquals(NDEF_FILE_ID) -> { selected = Selected.NDEF; SW_OK }
            else -> SW_FILE_NOT_FOUND.also { if (p1 == 0x04) selected = Selected.NONE }
        }
    }

    private fun readBinary(apdu: ByteArray, p1: Int, p2: Int): ByteArray {
        val file = when (selected) {
            Selected.CC -> capabilityContainer()
            Selected.NDEF -> ndefFile
            else -> return SW_NOT_ALLOWED
        }
        val offset = (p1 shl 8) or p2
        val le = if (apdu.size >= 5) (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        if (offset > file.size) return SW_WRONG_P1P2
        val end = minOf(file.size, offset + le, offset + MAX_LE)
        return file.copyOfRange(offset, end) + SW_OK
    }

    private fun capabilityContainer(): ByteArray {
        val max = maxOf(ndefFile.size, 2)
        return byteArrayOf(
            0x00, 0x0F, // CCLEN
            0x20, // Mapping-Version 2.0
            (MAX_LE ushr 8).toByte(), MAX_LE.toByte(), // MLe
            0x00, 0xFF.toByte(), // MLc
            0x04, 0x06, // NDEF File Control TLV
            0xE1.toByte(), 0x04, // File-ID
            (max ushr 8).toByte(), max.toByte(), // max. NDEF-Größe
            0x00, // Lesen erlaubt
            0xFF.toByte(), // Schreiben verboten
        )
    }

    companion object {
        const val MAX_LE = 0xF6
        /** Grenze für NDEF-Nachrichten (2-Byte-Längenfeld). */
        const val MAX_NDEF_SIZE = 0xFFFD

        val NDEF_AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
        private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        val SW_NOT_ALLOWED = byteArrayOf(0x69, 0x86.toByte())
        val SW_SECURITY = byteArrayOf(0x69, 0x82.toByte())
    }
}
