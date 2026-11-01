package de.soenkejacobs.visitenkarte.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Gibt die aktuell gewählte Visitenkarte als NFC-Tag aus (Host Card Emulation).
 * Antwortet nur, solange in der App die NFC-Freigabe aktiv ist.
 */
class CardHceService : HostApduService() {
    private val emulator = Type4TagEmulator { NfcPayload.current }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray =
        emulator.process(commandApdu).also { response ->
            if (commandApdu.size > 1 && commandApdu[1] == 0xB0.toByte() && response.size > 2) {
                NfcPayload.onRead?.invoke()
            }
        }

    override fun onDeactivated(reason: Int) {
        emulator.reset()
    }
}

/** Prozessweiter Zustand: welche Karte wird gerade per NFC angeboten? */
object NfcPayload {
    @Volatile
    var current: ByteArray? = null

    @Volatile
    var onRead: (() -> Unit)? = null

    fun vcardMessage(vcardText: String): ByteArray =
        Ndef.mimeMessage("text/vcard", vcardText.toByteArray(Charsets.UTF_8))
}
