package com.Lia.assistant

data class LiaAction(val name: String)

data class LiaActionResult(
    val available: Boolean,
    val message: String,
)

data class LiaTool(
    val name: String,
    val description: String,
    val parameters: Map<String, String> = emptyMap(),
)
