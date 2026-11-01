package de.soenkejacobs.visitenkarte.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.soenkejacobs.visitenkarte.VisitenkarteApp
import de.soenkejacobs.visitenkarte.data.ServerConfig
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repository = (app as VisitenkarteApp).repository
    val content = repository.content
    val sync = repository.sync

    init {
        viewModelScope.launch {
            repository.reload()
            // Beim Start still aktualisieren, wenn der letzte Abgleich älter als eine Stunde ist
            val cfg = repository.settings.load()
            if (cfg.isConfigured && System.currentTimeMillis() - repository.settings.lastSync > 60 * 60 * 1000) {
                repository.syncNow()
            }
        }
    }

    fun config(): ServerConfig = repository.settings.load()

    fun syncNow() {
        viewModelScope.launch { repository.syncNow() }
    }

    fun saveAndSync(config: ServerConfig) {
        repository.settings.save(config)
        syncNow()
    }

    suspend fun test(config: ServerConfig): Result<String> = repository.testConnection(config)
}
