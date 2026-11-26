package com.Lia.assistant

object ActionExecutor {
    fun execute(action: LiaAction): LiaActionResult =
        LiaActionResult(false, "Action execution is unavailable in the Play flavor: ${action.name}")
}
