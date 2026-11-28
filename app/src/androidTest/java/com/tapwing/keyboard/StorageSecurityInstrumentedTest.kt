// SPDX-License-Identifier: GPL-3.0-or-later

package com.tapwing.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.PersonalProfileStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageSecurityInstrumentedTest {

    @Test
    fun personalProfileStorageRoundTripAndClearDoNotResurrect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = PersonalProfileStorage(context)
        val profile = PersonalProfile(
            firstName = "Ada",
            lastName = "Lovelace",
            emails = listOf("ada@example.org")
        )
        storage.save(profile)
        assertEquals("Ada", storage.load().firstName)
        assertEquals("ada@example.org", storage.load().primaryEmail)

        storage.clear()
        assertTrue(storage.load().isEmpty)
    }
}
