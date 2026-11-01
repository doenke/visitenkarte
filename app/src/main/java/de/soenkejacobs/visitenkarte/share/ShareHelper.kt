package de.soenkejacobs.visitenkarte.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import de.soenkejacobs.visitenkarte.data.CardItem
import de.soenkejacobs.visitenkarte.data.DocumentItem
import java.io.File

object ShareHelper {
    private const val VCARD_MIME = "text/x-vcard"

    /** Pakete, die Quick Share bereitstellen (Google/Android bzw. Samsung). */
    private val QUICK_SHARE_PACKAGES = listOf("com.google.android.gms", "com.samsung.android.app.sharelive")

    fun shareCard(context: Context, card: CardItem) =
        context.startActivity(Intent.createChooser(cardIntent(context, card), "Visitenkarte teilen"))

    fun shareDocument(context: Context, doc: DocumentItem) {
        val uri = exportFile(context, doc.file, doc.file.name)
        context.startActivity(Intent.createChooser(sendIntent(uri, doc.mimeType, doc.title), "${doc.title} teilen"))
    }

    fun openDocumentExternally(context: Context, doc: DocumentItem) {
        val uri = exportFile(context, doc.file, doc.file.name)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, doc.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, "Öffnen mit"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Öffnet direkt Quick Share. Gibt es kein Quick Share auf dem Gerät, erscheint der
     * normale Teilen-Dialog, in dem Quick Share ggf. ebenfalls angeboten wird.
     */
    fun quickShareCard(context: Context, card: CardItem) {
        val base = cardIntent(context, card)
        val target = findQuickShare(context, base)
        if (target != null) {
            try {
                context.startActivity(Intent(base).setComponent(target))
                return
            } catch (_: Exception) {
                // Fallback unten
            }
        }
        Toast.makeText(context, "Quick Share nicht direkt verfügbar – bitte im Teilen-Dialog wählen", Toast.LENGTH_LONG).show()
        shareCard(context, card)
    }

    fun isQuickShareAvailable(context: Context): Boolean =
        findQuickShare(context, Intent(Intent.ACTION_SEND).setType(VCARD_MIME)) != null

    private fun findQuickShare(context: Context, intent: Intent): ComponentName? {
        val pm = context.packageManager
        for (pkg in QUICK_SHARE_PACKAGES) {
            val matches = pm.queryIntentActivities(Intent(intent).setPackage(pkg), PackageManager.MATCH_DEFAULT_ONLY)
            val best = matches.firstOrNull { it.activityInfo.name.contains("sharing", true) || it.activityInfo.name.contains("nearby", true) }
                ?: matches.firstOrNull().takeIf { pkg != "com.google.android.gms" }
            if (best != null) return ComponentName(best.activityInfo.packageName, best.activityInfo.name)
        }
        return null
    }

    private fun cardIntent(context: Context, card: CardItem): Intent {
        val name = card.vcard.formattedName.ifBlank { "Visitenkarte" }
        val fileName = "${name}_${card.title}".replace(Regex("[^\\p{L}\\p{N}_-]+"), "_") + ".vcf"
        val uri = exportFile(context, card.vcfFile, fileName)
        return sendIntent(uri, VCARD_MIME, "Visitenkarte $name")
    }

    private fun sendIntent(uri: Uri, mime: String, subject: String) = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TITLE, subject)
        clipData = ClipData.newRawUri(subject, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Kopiert die Datei in den freigegebenen Cache-Ordner, damit Empfänger-Apps sie lesen können. */
    private fun exportFile(context: Context, source: File, name: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000 }?.forEach { it.delete() }
        val safeName = name.replace('/', '_').ifBlank { "datei" }
        val target = File(dir, safeName)
        source.copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }
}
