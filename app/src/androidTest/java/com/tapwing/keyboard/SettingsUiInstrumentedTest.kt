// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tapwing.keyboard.settings.SettingsActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun privacyAndLicensesScreensAreReachableFromSettingsHome() {
        val context = composeRule.activity
        val privacyLabel = context.getString(R.string.nav_privacy)
        composeRule.onNodeWithText(privacyLabel).performClick()
        composeRule.waitForIdle()
    }
}
