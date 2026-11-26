package com.Lia.assistant

object ActionExecutor {
    fun execute(action: LiaAction): LiaActionResult =
        LiaActionResult(false, "Action execution is not initialized: ${action.name}")
}
