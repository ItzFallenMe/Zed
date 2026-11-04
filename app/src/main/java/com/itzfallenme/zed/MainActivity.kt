package com.itzfallenme.zed

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var assistant: ZedAssistant
    private var pendingApiKey = ""

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) lifecycleScope.launch { assistant.start(pendingApiKey) }
        else assistant.setError("Microphone permission is required for voice mode.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistant = ZedAssistant(applicationContext)

        setContent {
            val state by assistant.state.collectAsState()
            var apiKey by remember { mutableStateOf("") }

            MaterialTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ZED", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(state.label)
                        Spacer(Modifier.height(20.dp))
                        if (!state.isActive) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("Gemini API key") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (state.isActive) {
                                    assistant.stop()
                                } else {
                                    pendingApiKey = apiKey.trim()
                                    if (pendingApiKey.isBlank()) {
                                        assistant.setError("Enter a Gemini API key to start.")
                                    } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        lifecycleScope.launch { assistant.start(pendingApiKey) }
                                    } else {
                                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            shape = CircleShape
                        ) {
                            Text(if (state.isActive) "Stop Zed" else "Start talking")
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { assistant.testSpeaker() }, enabled = state.isActive) {
                            Text("Test speaker")
                        }
                        state.error?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Voice-first · Hindi · English · Hinglish")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::assistant.isInitialized) assistant.close()
        super.onDestroy()
    }
}
