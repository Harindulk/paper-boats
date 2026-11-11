package com.starfungames.paperboats.party

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostAddressTest {
    @Test
    fun aBareIpGetsTheDefaultPort() {
        assertEquals("ws://192.168.1.24:8642/ws", controllerSocketUrl("192.168.1.24"))
    }

    @Test
    fun theAddressUnderTheQrIsUsedAsShown() {
        assertEquals("ws://192.168.1.24:9000/ws", controllerSocketUrl("192.168.1.24:9000"))
    }

    @Test
    fun aPastedLinkAndStraySpacesAreFine() {
        assertEquals("ws://192.168.1.24:8642/ws", controllerSocketUrl("  http://192.168.1.24:8642/ "))
        assertEquals("ws://tv.local:8642/ws", controllerSocketUrl("HTTP://tv.local"))
    }

    @Test
    fun garbageIsRefused() {
        assertNull(controllerSocketUrl(""))
        assertNull(controllerSocketUrl("   "))
        assertNull(controllerSocketUrl("hello world"))
        assertNull(controllerSocketUrl("192.168.1.24:"))
        assertNull(controllerSocketUrl("192.168.1.24:port"))
        assertNull(controllerSocketUrl("192.168.1.24:70000"))
        assertNull(controllerSocketUrl("192.168.1.24:0"))
        assertNull(controllerSocketUrl(":8642"))
        assertNull(controllerSocketUrl("http://"))
    }
}
