package de.soenkejacobs.visitenkarte

import android.app.Application
import de.soenkejacobs.visitenkarte.data.Repository
import de.soenkejacobs.visitenkarte.data.SettingsStore
import de.soenkejacobs.visitenkarte.sync.SyncWorker

class VisitenkarteApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, SettingsStore(this))
        SyncWorker.schedule(this)
    }
}
