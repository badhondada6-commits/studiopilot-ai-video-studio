package com.Lia.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.voice.ConversationRole
import com.Lia.assistant.voice.ConversationTurn
import com.Lia.assistant.voice.NovaOrbState
import com.Lia.assistant.voice.VoiceForegroundService
import com.Lia.assistant.voice.VoiceSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ScreenBackground = Color(0xFF090D17)
private val CardBackground = Color(0xFF131A28)
private val MutedText = Color(0xFFA8B2C4)
private val AccentMint = Color(0xFF8DE4D3)
private val AccentViolet = Color(0xFFB8AAFF)

@Composable
fun VoiceDashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var isKeySaved by remember { mutableStateOf(false) }
    var isKeyLoaded by remember { mutableStateOf(false) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val sessionState by VoiceSessionManager.state.collectAsStateWithLifecycle()
    val isActive by VoiceSessionManager.isActive.collectAsStateWithLifecycle()
    val isMuted by VoiceSessionManager.muted.collectAsStateWithLifecycle()
    val statusText by VoiceSessionManager.statusText.collectAsStateWithLifecycle()
    val lastError by VoiceSessionManager.lastError.collectAsStateWithLifecycle()
    val turns by VoiceSessionManager.conversationTurns.collectAsStateWithLifecycle()

    LaunchedEffect(context) {
        val savedKey = withContext(Dispatchers.IO) { ApiKeyStore.getKey(context) }
        apiKey = savedKey.orEmpty()
        isKeySaved = !savedKey.isNullOrBlank()
        isSettingsExpanded = !isKeySaved
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
            .background(ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DashboardHeader(isActive = isActive)

        VoiceHeroCard(
            state = sessionState,
            isActive = isActive,
            isMuted = isMuted,
            statusText = lastError ?: statusText,
            canStart = isKeyLoaded && isKeySaved,
            onStart = ::requestPermissionsAndStart,
            onToggleMute = VoiceSessionManager::toggleMute,
            onStop = {
                context.startService(
                    Intent(context, VoiceForegroundService::class.java)
                        .setAction(VoiceForegroundService.ACTION_STOP),
                )
            },
        )

        ConversationCard(turns = turns)

        ConnectionSettingsCard(
            expanded = isSettingsExpanded,
            onExpand = { isSettingsExpanded = !isSettingsExpanded },
            apiKey = apiKey,
            onApiKeyChange = {
                apiKey = it
                isKeySaved = false
                message = null
            },
            isKeyLoaded = isKeyLoaded,
            isKeySaved = isKeySaved,
            isActive = isActive,
            message = message,
            onSave = {
                val keyToSave = apiKey.trim()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ApiKeyStore.saveKey(context, keyToSave)
                        }
                        apiKey = keyToSave
                        isKeySaved = true
                        message = "Gemini connection key saved on this device."
                    } catch (_: RuntimeException) {
                        message = "Could not save the key. Please try again."
                    }
                }
            },
        )

        Text(
            text = "Lia AI  ·  Your voice stays in control",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MutedText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DashboardHeader(isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "LIA",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Text(
                text = "YOUR VOICE COMPANION",
                color = MutedText,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
            )
        }
        Surface(
            shape = CircleShape,
            color = if (isActive) AccentMint.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.06f),
            contentColor = if (isActive) AccentMint else MutedText,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isActive) AccentMint else MutedText, CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (isActive) "VOICE ON" else "READY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun VoiceHeroCard(
    state: NovaOrbState,
    isActive: Boolean,
    isMuted: Boolean,
    statusText: String,
    canStart: Boolean,
    onStart: () -> Unit,
    onToggleMute: () -> Unit,
    onStop: () -> Unit,
) {
    val accent = when (state) {
        NovaOrbState.ERROR -> MaterialTheme.colorScheme.error
        NovaOrbState.SPEAKING -> AccentViolet
        NovaOrbState.LISTENING -> AccentMint
        else -> AccentMint
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        color = CardBackground,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1A2537),
                            Color(0xFF151B2B),
                            Color(0xFF111827),
                        ),
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isActive) "I'M HERE WITH YOU" else "A MOMENT FOR YOU",
                color = MutedText,
                fontSize = 10.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (state) {
                    NovaOrbState.CONNECTING -> "Connecting to Lia"
                    NovaOrbState.LISTENING -> if (isMuted) "Microphone paused" else "Listening"
                    NovaOrbState.THINKING -> "Let me think"
                    NovaOrbState.SPEAKING -> "Lia is speaking"
                    NovaOrbState.ERROR -> "Connection needs attention"
                    NovaOrbState.IDLE -> "What’s on your mind?"
                },
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = statusText,
                color = if (state == NovaOrbState.ERROR) MaterialTheme.colorScheme.error else MutedText,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            VoiceOrb(state = state, isMuted = isMuted, accent = accent)
            Spacer(Modifier.height(4.dp))
            if (isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onToggleMute,
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.09f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isMuted) "Unmute" else "Mute")
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B2630),
                            contentColor = Color(0xFFFFB8BE),
                        ),
                    ) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("End session")
                    }
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentMint,
                        contentColor = Color(0xFF06251F),
                        disabledContainerColor = AccentMint.copy(alpha = 0.28f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f),
                    ),
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = if (canStart) "Start a voice session" else "Add your Gemini key to start",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isActive) {
                    "You can pause the microphone or end the session at any time."
                } else {
                    "Voice starts only when you tap the button."
                },
                color = MutedText,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun VoiceOrb(
    state: NovaOrbState,
    isMuted: Boolean,
    accent: Color,
) {
    val amplitude by VoiceSessionManager.amplitude.collectAsStateWithLifecycle()
    val transition = rememberInfiniteTransition(label = "voice-orb")
    val idlePulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )
    val scale = if (state == NovaOrbState.IDLE) 1f else idlePulse + amplitude.coerceIn(0f, 0.3f) * 0.12f

    Box(
        modifier = Modifier
            .size(244.dp)
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.28f),
                        AccentViolet.copy(alpha = 0.09f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.2f),
                radius = size.minDimension * 0.41f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.minDimension * 0.34f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .size(122.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF34445C), Color(0xFF1A263A), Color(0xFF121A29)),
                    ),
                    CircleShape,
                )
                .border(1.dp, accent.copy(alpha = 0.62f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Outlined.MicOff else Icons.Outlined.AutoAwesome,
                contentDescription = if (isMuted) "Microphone muted" else "Lia voice orb",
                tint = accent,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun ConversationCard(turns: List<ConversationTurn>) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Conversation",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "This session only",
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.size(20.dp),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = CardBackground,
        ) {
            if (turns.isEmpty()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Your conversation will appear here.",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Start a session whenever you’re ready.",
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    turns.takeLast(4).forEach { turn -> TranscriptBubble(turn) }
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(turn: ConversationTurn) {
    val isUser = turn.role == ConversationRole.USER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isUser) Color(0xFF1B2936) else Color(0xFF1B1F31))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text(
            text = if (isUser) "YOU" else "LIA",
            color = if (isUser) AccentMint else AccentViolet,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = turn.text,
            color = Color(0xFFE7EBF3),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun ConnectionSettingsCard(
    expanded: Boolean,
    onExpand: () -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isKeyLoaded: Boolean,
    isKeySaved: Boolean,
    isActive: Boolean,
    message: String?,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = CardBackground,
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExpand)
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isKeySaved) Icons.Outlined.CheckCircle else Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = if (isKeySaved) AccentMint else AccentViolet,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gemini connection",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isKeySaved) "API key saved on this device" else "Connect your own API key",
                        color = MutedText,
                        fontSize = 11.sp,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse connection settings" else "Expand connection settings",
                    tint = MutedText,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API key") },
                    supportingText = {
                        Text("Stored privately on this device. Never logged or bundled.")
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                    },
                    singleLine = true,
                    enabled = !isActive && isKeyLoaded,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = onSave,
                    enabled = isKeyLoaded && !isActive && apiKey.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF28394A),
                        contentColor = AccentMint,
                    ),
                ) {
                    Text(if (isKeySaved) "Update API key" else "Save API key")
                }
                if (message != null) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = message,
                        color = if (isKeySaved) AccentMint else MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
