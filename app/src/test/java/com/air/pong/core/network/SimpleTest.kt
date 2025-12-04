package com.air.pong.core.network

import org.junit.Test
import org.junit.Assert.*

class SimpleTest {
    @Test
    fun testSettingsInstantiation() {
        val list = List(27) { 0 }
        val settings = GameMessage.Settings(1000L, 1, list)
        assertNotNull(settings)
    }
}
