package com.Lia.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

class DirectFlavorTest {
    @Test
    fun directActionExecutorIsPresent() {
        assertTrue(ActionExecutor.execute(LiaAction("test")).message.isNotBlank())
    }
}
