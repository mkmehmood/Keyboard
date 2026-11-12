package com.mkmehmood.everydaykeys.ui

import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.mkmehmood.everydaykeys.ime.EveryDayKeysService

/** Shows the system fingerprint / screen-lock prompt, then tells the keyboard the clipboard history may be opened. */
class UnlockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowed = if (Build.VERSION.SDK_INT >= 30)
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK

        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            setContent {
                AppColors {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("Clipboard lock") },
                        text = { Text("Set up a fingerprint or screen lock in Android settings to use the clipboard lock.") },
                        confirmButton = { TextButton(onClick = { finish() }) { Text("OK") } }
                    )
                }
            }
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
