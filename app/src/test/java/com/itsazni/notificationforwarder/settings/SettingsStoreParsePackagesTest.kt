package com.itsazni.notificationforwarder.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreParsePackagesTest {
    @Test
    fun parsingTrimsSupportsExistingDelimitersAndRemovesDuplicates() {
        val result = SettingsStore.parsePackages(
            "  com.whatsapp,com.example.bank\ncom.gmail; com.whatsapp ;  "
        )

        assertEquals(
            setOf("com.whatsapp", "com.example.bank", "com.gmail"),
            result
        )
    }
}
