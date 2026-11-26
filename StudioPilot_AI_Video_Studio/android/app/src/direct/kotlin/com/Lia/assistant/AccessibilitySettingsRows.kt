package com.Lia.assistant

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

object AccessibilitySettingsRows {
    @Composable
    fun Content(onOpenSettings: () -> Unit) {
        Button(onClick = onOpenSettings) {
            Text("Configure accessibility service")
        }
    }
}
