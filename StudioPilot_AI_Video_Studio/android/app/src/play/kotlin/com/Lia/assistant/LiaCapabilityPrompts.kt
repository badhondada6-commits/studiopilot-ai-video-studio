package com.Lia.assistant

object LiaCapabilityPrompts {
    fun toolInstruction(name: String): String =
        "You are $name. This Play flavor cannot read or control the screen or post to social apps. Be transparent about those limits."
}
