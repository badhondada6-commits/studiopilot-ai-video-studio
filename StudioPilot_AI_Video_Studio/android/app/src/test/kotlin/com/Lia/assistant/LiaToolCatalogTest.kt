package com.Lia.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiaToolCatalogTest {
    @Test
    fun onlyDeclaredToolsAreAccepted() {
        assertTrue(LiaToolCatalog.isDeclared("build_website"))
        assertFalse(LiaToolCatalog.isDeclared("open_app"))
        assertFalse(LiaToolCatalog.isDeclared(""))
    }
}
