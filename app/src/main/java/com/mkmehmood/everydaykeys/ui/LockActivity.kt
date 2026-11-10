package com.mkmehmood.everydaykeys.ui

import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.mkmehmood.everydaykeys.ime.EverydayKeysService

/** Shows the biometric prompt that unlocks clipboard history for one minute. */
class LockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                EverydayKeysService.unlockedUntil = SystemClock.elapsedRealtime() + 60_000
                EverydayKeysService.instance?.view?.refreshClip()
                finish()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock clipboard history")
            .setSubtitle("Everyday Keys")
            .setAllowedAuthenticators(auth)
            .build()
        prompt.authenticate(info)
    }
}
