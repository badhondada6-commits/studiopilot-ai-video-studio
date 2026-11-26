package com.Lia.assistant

object LiaCapabilityPrompts {
    fun toolInstruction(name: String): String =
        "You are $name. Use only the phone actions currently available to this app, and be clear when an action is unavailable."
}
