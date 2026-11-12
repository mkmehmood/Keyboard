package com.mkmehmood.everydaykeys.ui

import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mkmehmood.everydaykeys.ime.EveryDayKeysService

/** Shows the system fingerprint / screen-lock prompt, then tells the keyboard the clipboard history may be opened. */
class UnlockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowed = if (Build.VERSION.SDK_INT >= 30)
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK

        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // a Material 3 dialog instead of a toast
            MaterialAlertDialogBuilder(this)
                .setTitle("Clipboard lock")
                .setMessage("Set up a fingerprint or screen lock in Android settings to use the clipboard lock.")
                .setPositiveButton("OK", null)
                .setOnDismissListener { finish() }
                .show()
            return
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock clipboard history")
            .setAllowedAuthenticators(allowed)
            .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Cancel") }
            .build()

        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                EveryDayKeysService.instance?.unlockClips()
                finish()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() }
        }).authenticate(info)
    }
}
