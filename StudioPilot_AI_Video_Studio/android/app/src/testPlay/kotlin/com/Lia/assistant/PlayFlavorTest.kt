package com.Lia.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayFlavorTest {
    @Test
    fun playFlavorReportsActionUnavailable() {
        assertTrue(ActionExecutor.execute(LiaAction("test")).message.contains("unavailable"))
    }
}
