package de.soenkejacobs.visitenkarte.launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import de.soenkejacobs.visitenkarte.R
import de.soenkejacobs.visitenkarte.VisitenkarteApp
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.QrCode
import de.soenkejacobs.visitenkarte.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Startbildschirm-Widget mit dem QR-Code einer Visitenkarte.
 * Tippen auf den Titel wechselt die Karte, Tippen auf den Code öffnet die App.
 */
class QrWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAsync(context, ids)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val cards = (context.applicationContext as VisitenkarteApp).repository.snapshot().cards
                    if (cards.isNotEmpty()) {
                        val current = cards.indexOfFirst { it.id == selectedCardId(context, id) }.coerceAtLeast(0)
                        prefs(context).edit().putString(key(id), cards[(current + 1) % cards.size].id).apply()
                    }
                    render(context, id)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        ids.forEach { edit.remove(key(it)) }
        edit.apply()
    }

    private fun updateAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { render(context, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NEXT = "de.soenkejacobs.visitenkarte.widget.NEXT"

        /** Alle Widgets neu zeichnen (nach Sync oder geänderter Teilen-Auswahl). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QrWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, QrWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        private fun key(id: Int) = "card_$id"
        private fun selectedCardId(context: Context, id: Int) = prefs(context).getString(key(id), null)

        private fun pickCard(context: Context, id: Int, cards: List<CardItem>): CardItem? =
            cards.firstOrNull { it.id == selectedCardId(context, id) }
                ?: cards.firstOrNull { it.id.contains("business", true) || it.title.contains("gesch", true) }
                ?: cards.firstOrNull()

        private suspend fun render(context: Context, widgetId: Int) {
            val app = context.applicationContext as VisitenkarteApp
            val cards = app.repository.snapshot().cards
            val card = pickCard(context, widgetId, cards)
            val views = RemoteViews(context.packageName, R.layout.widget_qr)

            if (card == null) {
                views.setTextViewText(R.id.widget_title, "Keine Visitenkarte – App öffnen")
                views.setViewVisibility(R.id.widget_qr, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_title, if (cards.size > 1) "${card.title}  ⇄" else card.title)
                views.setViewVisibility(R.id.widget_qr, View.VISIBLE)
                val bitmap = runCatching { QrCode.render(app.shareFilter.compactText(card), 600) }.getOrNull()
                views.setImageViewBitmap(R.id.widget_qr, bitmap)
                views.setContentDescription(R.id.widget_qr, "QR-Code ${card.title}")
            }

            val open = Intent(context, MainActivity::class.java)
                .setAction(if (card != null) Shortcuts.ACTION_SHOW_QR else Intent.ACTION_MAIN)
                .putExtra(Shortcuts.EXTRA_CARD_ID, card?.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_qr,
                PendingIntent.getActivity(context, widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            val next = Intent(context, QrWidgetProvider::class.java)
                .setAction(ACTION_NEXT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            views.setOnClickPendingIntent(
                R.id.widget_title,
                if (cards.size > 1) {
                    PendingIntent.getBroadcast(context, widgetId, next, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                } else {
                    PendingIntent.getActivity(context, widgetId + 100_000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                },
            )
            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
        }
    }
}
