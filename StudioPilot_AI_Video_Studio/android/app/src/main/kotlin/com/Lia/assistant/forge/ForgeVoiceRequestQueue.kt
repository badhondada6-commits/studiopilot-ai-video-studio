package com.Lia.assistant.forge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ForgeVoiceRequestQueue {
    private val _pendingPrompt = MutableStateFlow<String?>(null)
    val pendingPrompt: StateFlow<String?> = _pendingPrompt.asStateFlow()

    fun submit(prompt: String) {
        _pendingPrompt.value = prompt
    }

    fun consume(): String? {
        val prompt = _pendingPrompt.value
        _pendingPrompt.value = null
        return prompt
    }
}
