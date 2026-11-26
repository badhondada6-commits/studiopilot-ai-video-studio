package com.Lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.Lia.assistant.ui.VoiceDashboardScreen

private val LiaColorScheme = darkColorScheme(
    primary = Color(0xFF8DE4D3),
    secondary = Color(0xFFB8AAFF),
    background = Color(0xFF090D17),
    surface = Color(0xFF131A28),
    surfaceVariant = Color(0xFF1C2637),
    onPrimary = Color(0xFF06251F),
    onBackground = Color(0xFFF2F5FB),
    onSurface = Color(0xFFF2F5FB),
    onSurfaceVariant = Color(0xFFA8B2C4),
    error = Color(0xFFFF8B95),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MaterialTheme(colorScheme = LiaColorScheme) {
                VoiceDashboardScreen()
            }
        }
    }
}
