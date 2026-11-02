package de.soenkejacobs.visitenkarte.ui

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Entsperren per Fingerabdruck/Gesicht oder Geräte-PIN. */
object DocumentLock {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit) {
        when (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Keine Displaysperre eingerichtet: Schutz nicht möglich, Zugriff trotzdem erlauben
                Toast.makeText(activity, "Keine Displaysperre eingerichtet – Dokumente sind nicht geschützt", Toast.LENGTH_LONG).show()
                onSuccess()
                return
            }
            else -> {
                Toast.makeText(activity, "Entsperren auf diesem Gerät nicht möglich", Toast.LENGTH_LONG).show()
                onSuccess()
                return
            }
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Dokumente entsperren")
                .setSubtitle("Private Dokumente sind geschützt")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
