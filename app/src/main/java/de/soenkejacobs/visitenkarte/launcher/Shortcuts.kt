package de.soenkejacobs.visitenkarte.launcher

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import de.soenkejacobs.visitenkarte.R
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.ui.MainActivity

/** App-Shortcuts (lange auf das App-Icon tippen): QR-Code bzw. NFC je Karte. */
object Shortcuts {
    const val ACTION_SHOW_QR = "de.soenkejacobs.visitenkarte.SHOW_QR"
    const val ACTION_SHOW_NFC = "de.soenkejacobs.visitenkarte.SHOW_NFC"
    const val EXTRA_CARD_ID = "card_id"

    fun update(context: Context, cards: List<CardItem>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(2)
        val qr = cards.map { shortcut(context, it, ACTION_SHOW_QR, "qr", "QR ${it.title}", R.drawable.ic_shortcut_qr) }
        val nfc = cards.map { shortcut(context, it, ACTION_SHOW_NFC, "nfc", "NFC ${it.title}", R.drawable.ic_shortcut_nfc) }
        // Zuerst alle QR-Shortcuts, dann NFC – so viele, wie der Launcher zeigt
        val list = (qr + nfc).take(max).mapIndexed { i, s -> s.setRank(i).build() }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }

    private fun shortcut(context: Context, card: CardItem, action: String, prefix: String, label: String, icon: Int) =
        ShortcutInfoCompat.Builder(context, "$prefix-${card.id}")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_CARD_ID, card.id),
            )
}
