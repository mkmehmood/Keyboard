package com.mkmehmood.everydaykeys.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.mkmehmood.everydaykeys.ime.EveryDayKeysService

/** Invisible helper: an input method cannot start for-result activities, so voice typing goes through here. */
class VoiceActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) EveryDayKeysService.instance?.commitText(t)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        } catch (e: Exception) {
            setContent {
                AppColors {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("Voice typing") },
                        text = { Text("No speech recognizer available") },
                        confirmButton = { TextButton(onClick = { finish() }) { Text("OK") } }
                    )
                }
            }
        }
    }
}
