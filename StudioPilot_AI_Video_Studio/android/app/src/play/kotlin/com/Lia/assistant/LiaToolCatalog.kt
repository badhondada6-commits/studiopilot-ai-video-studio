package com.Lia.assistant

import org.json.JSONArray
import org.json.JSONObject

object LiaToolCatalog {
    val tools: List<LiaTool> = emptyList()

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
