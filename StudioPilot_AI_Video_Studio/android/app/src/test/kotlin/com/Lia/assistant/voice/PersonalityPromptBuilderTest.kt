package com.Lia.assistant.voice

import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.Personality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityPromptBuilderTest {
    @Test
    fun everyPersonalityAndLanguageBuildsAnInstruction() {
        Personality.entries.forEach { personality ->
            LanguagePreference.entries.forEach { language ->
                val prompt = PersonalityPromptBuilder.build(
                    personality = personality,
                    language = language,
                    detectedLanguage = DetectedLanguage.UNKNOWN,
                    recap = null,
                    assistantName = "Lia",
                )

                assertTrue(prompt.contains("You are Lia, a persistent personal voice assistant"))
                assertTrue(prompt.contains(personality.systemInstruction))
                assertTrue(prompt.contains("Language policy:"))
                assertTrue(prompt.contains("Only use a listed tool when the user clearly and specifically requests that action."))
            }
        }
    }

    @Test
    fun recapAppearsOnlyWhenProvided() {
        val withoutRecap = buildPrompt(recap = null)
        assertFalse(withoutRecap.contains("This is a continuing conversation"))

        val withRecap = buildPrompt(recap = "User: Hello\nYou: Hi")
        assertTrue(withRecap.contains("This is a continuing conversation — do not greet again. Recap: User: Hello\nYou: Hi"))
    }

    private fun buildPrompt(recap: String?) = PersonalityPromptBuilder.build(
        personality = Personality.NORMAL,
        language = LanguagePreference.AUTO,
        detectedLanguage = DetectedLanguage.UNKNOWN,
        recap = recap,
        assistantName = "Lia",
    )
}
