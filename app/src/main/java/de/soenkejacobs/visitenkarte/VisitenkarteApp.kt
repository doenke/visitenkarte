package de.soenkejacobs.visitenkarte

import android.app.Application
import android.content.Context
import de.soenkejacobs.visitenkarte.data.Repository
import de.soenkejacobs.visitenkarte.data.SettingsStore
import de.soenkejacobs.visitenkarte.data.ShareFilter
import de.soenkejacobs.visitenkarte.launcher.QrWidgetProvider
import de.soenkejacobs.visitenkarte.launcher.Shortcuts
import de.soenkejacobs.visitenkarte.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class VisitenkarteApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var shareFilter: ShareFilter
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, SettingsStore(this))
        shareFilter = ShareFilter(this)
        SyncWorker.schedule(this)

        // Shortcuts und Widgets aktuell halten
        repository.onContentChanged = { refreshLauncher() }
        scope.launch {
            repository.reload()
            refreshLauncher()
        }
        scope.launch {
            shareFilter.excluded.drop(1).collect { QrWidgetProvider.updateAll(this@VisitenkarteApp) }
        }
    }

    private fun refreshLauncher() {
        Shortcuts.update(this, repository.content.value.cards)
        QrWidgetProvider.updateAll(this)
    }
}

val Context.app: VisitenkarteApp get() = applicationContext as VisitenkarteApp
