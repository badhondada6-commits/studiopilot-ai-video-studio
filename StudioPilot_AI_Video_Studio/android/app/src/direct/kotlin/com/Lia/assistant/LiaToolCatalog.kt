package com.Lia.assistant

import org.json.JSONArray
import org.json.JSONObject

object LiaToolCatalog {
    val tools: List<LiaTool> = listOf(
        LiaTool(
            name = "build_website",
            description = "Start building a website from the supplied prompt.",
            parameters = mapOf("prompt" to "Description of the website to build."),
        ),
    )

    fun isDeclared(name: String): Boolean = tools.any { it.name == name }

    fun declarations(): JSONArray = JSONArray().apply {
        tools.forEach { tool ->
            val properties = JSONObject().apply {
                tool.parameters.forEach { (name, description) ->
                    put(
                        name,
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", description),
                    )
                }
            }
            put(
                JSONObject()
                    .put("name", tool.name)
                    .put("description", tool.description)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put("properties", properties),
                    ),
            )
        }
    }
}
