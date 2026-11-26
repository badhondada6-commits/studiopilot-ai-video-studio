package com.Lia.assistant

import org.json.JSONObject

data class LiaAction(
    val name: String,
    val args: JSONObject = JSONObject(),
)

data class LiaActionResult(
    val available: Boolean,
    val message: String,
)

data class LiaTool(
    val name: String,
    val description: String,
    val parameters: Map<String, String> = emptyMap(),
)
