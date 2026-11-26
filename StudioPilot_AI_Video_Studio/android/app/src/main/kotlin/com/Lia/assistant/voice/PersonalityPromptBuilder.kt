package com.Lia.assistant.voice

import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.Personality
import com.Lia.assistant.LiaCapabilityPrompts

object PersonalityPromptBuilder {
    fun build(
        personality: Personality,
        language: LanguagePreference,
        detectedLanguage: DetectedLanguage,
        recap: String?,
        assistantName: String,
    ): String {
        val name = assistantName.trim().ifEmpty { AssistantBrand.NAME }
        val sections = mutableListOf(
            "You are $name, a persistent personal voice assistant living on the user's phone. Keep replies short and conversational, like spoken conversation.",
            LiaCapabilityPrompts.toolInstruction(name),
            personality.systemInstruction,
            languagePolicy(language, detectedLanguage),
            "Safety: Never claim to be human or have a body. Do not produce sexually explicit content. Stop a topic immediately when asked. Vary phrasing and avoid repetitive replies.",
        )
        if (recap != null) {
            sections += "This is a continuing conversation — do not greet again. Recap: $recap"
        }
        return sections.joinToString(separator = "\n\n")
    }

    private fun languagePolicy(
        language: LanguagePreference,
        detectedLanguage: DetectedLanguage,
    ): String = when (language) {
        LanguagePreference.AUTO -> {
            val detected = if (detectedLanguage == DetectedLanguage.UNKNOWN) {
                ""
            } else {
                " The latest detected language is ${detectedLanguage.name.lowercase()}."
            }
            "Language policy: Match the user's language turn by turn and never translate what they said.$detected"
        }
        LanguagePreference.HINDI ->
            "Language policy: Reply in Hindi using Devanagari script, regardless of the user's language."
        LanguagePreference.HINGLISH ->
            "Language policy: Reply in natural Hinglish, using Roman-script Hindi with natural English code-switching."
        LanguagePreference.ENGLISH ->
            "Language policy: Reply in English, regardless of the user's language."
    }
}
