package de.soenkejacobs.visitenkarte.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ServerConfig(
    val url: String,
    val username: String,
    val password: String,
) {
    val isConfigured: Boolean get() = url.isNotBlank()

    /** URL der manifest.json – entweder direkt angegeben oder `<url>/manifest.json`. */
    val manifestUrl: String
        get() {
            val u = url.trim()
            return if (u.endsWith(".json", ignoreCase = true)) u else u.trimEnd('/') + "/manifest.json"
        }
}

/**
 * Speichert die Server-Konfiguration. Das Passwort wird mit einem Schlüssel aus dem
 * Android Keystore (AES/GCM) verschlüsselt abgelegt.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): ServerConfig = ServerConfig(
        url = prefs.getString(KEY_URL, "").orEmpty(),
        username = prefs.getString(KEY_USER, "").orEmpty(),
        password = prefs.getString(KEY_PASS, null)?.let { runCatching { decrypt(it) }.getOrNull() }.orEmpty(),
    )

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_URL, config.url.trim())
            .putString(KEY_USER, config.username.trim())
            .putString(KEY_PASS, if (config.password.isEmpty()) null else encrypt(config.password))
            .apply()
    }

    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_SYNC, v).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(v) = prefs.edit().putString(KEY_LAST_ERROR, v).apply()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_LEN))
        return String(cipher.doFinal(data, IV_LEN, data.size - IV_LEN), Charsets.UTF_8)
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_USER = "user"
        const val KEY_PASS = "pass"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_ALIAS = "visitenkarte_server_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
