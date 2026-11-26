package com.Lia.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.voice.VoiceForegroundService
import com.Lia.assistant.voice.VoiceSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceSessionHome()
                }
            }
        }
    }
}

@Composable
private fun VoiceSessionHome() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var isKeySaved by remember { mutableStateOf(false) }
    var isKeyLoaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val isActive by VoiceSessionManager.isActive.collectAsStateWithLifecycle()
    val statusText by VoiceSessionManager.statusText.collectAsStateWithLifecycle()
    val lastError by VoiceSessionManager.lastError.collectAsStateWithLifecycle()

    LaunchedEffect(context) {
        val savedKey = withContext(Dispatchers.IO) { ApiKeyStore.getKey(context) }
        apiKey = savedKey.orEmpty()
        isKeySaved = !savedKey.isNullOrBlank()
        isKeyLoaded = true
    }

    fun startVoiceSession() {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, VoiceForegroundService::class.java),
            )
            message = null
        } catch (_: SecurityException) {
            message = "Lia could not start. Allow microphone access and try again."
        } catch (_: IllegalStateException) {
            message = "Lia could not start in the background. Reopen the app and try again."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val microphoneGranted =
            permissions[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
        if (microphoneGranted) {
            startVoiceSession()
        } else {
            message = "Microphone permission is required to talk with Lia."
        }
    }

    fun requestPermissionsAndStart() {
        val missingPermissions = buildList {
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missingPermissions.isEmpty()) {
            startVoiceSession()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Lia AI", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = when {
                lastError != null -> lastError.orEmpty()
                !isActive -> "Ready to talk"
                else -> statusText
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                isKeySaved = false
                message = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Gemini API key") },
            supportingText = { Text("Stored privately on this device and never logged.") },
            singleLine = true,
            enabled = !isActive && isKeyLoaded,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        Button(
            onClick = {
                val keyToSave = apiKey.trim()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ApiKeyStore.saveKey(context, keyToSave)
                        }
                        apiKey = keyToSave
                        isKeySaved = true
                        message = "API key saved on this device."
                    } catch (_: RuntimeException) {
                        message = "Could not save the API key. Please try again."
                    }
                }
            },
            enabled = isKeyLoaded && !isActive && apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isKeySaved) "Save API key again" else "Save API key")
        }

        if (isActive) {
            Button(
                onClick = {
                    context.startService(
                        Intent(context, VoiceForegroundService::class.java)
                            .setAction(VoiceForegroundService.ACTION_STOP),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop voice session")
            }
        } else {
            Button(
                onClick = ::requestPermissionsAndStart,
                enabled = isKeyLoaded && isKeySaved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start voice session")
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
