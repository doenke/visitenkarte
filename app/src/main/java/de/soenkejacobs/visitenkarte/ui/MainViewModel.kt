package de.soenkejacobs.visitenkarte.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.soenkejacobs.visitenkarte.VisitenkarteApp
import de.soenkejacobs.visitenkarte.data.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repository = (app as VisitenkarteApp).repository
    val shareFilter = (app as VisitenkarteApp).shareFilter
    val content = repository.content
    val sync = repository.sync
    val lastSync = repository.lastSync

    /** Dokumente sind bis zum Verlassen der App entsperrt. */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _lockDocuments = MutableStateFlow(repository.settings.lockDocuments)
    val lockDocuments: StateFlow<Boolean> = _lockDocuments.asStateFlow()

    private val _secureScreens = MutableStateFlow(repository.settings.secureScreens)
    val secureScreens: StateFlow<Boolean> = _secureScreens.asStateFlow()

    /** Von Shortcut oder Widget angeforderte Ansicht, z. B. "qr:business". */
    private val _pendingOverlay = MutableStateFlow<String?>(null)
    val pendingOverlay: StateFlow<String?> = _pendingOverlay.asStateFlow()

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

    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }

    fun setLockDocuments(v: Boolean) {
        repository.settings.lockDocuments = v
        _lockDocuments.value = v
    }

    fun setSecureScreens(v: Boolean) {
        repository.settings.secureScreens = v
        _secureScreens.value = v
    }

    fun requestOverlay(key: String) { _pendingOverlay.value = key }
    fun consumeOverlay() { _pendingOverlay.value = null }
}
