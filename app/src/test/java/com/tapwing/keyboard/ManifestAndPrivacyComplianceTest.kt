// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard

import com.tapwing.keyboard.ai.CloudProviderPolicy
import com.tapwing.keyboard.data.AiProviderType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestAndPrivacyComplianceTest {

    @Test
    fun manifestDoesNotDeclareBroadMediaOrStoragePermissions() {
        val manifestFile = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).firstOrNull { it.isFile }
        assertTrue("Could not locate AndroidManifest.xml", manifestFile != null)

        val xml = manifestFile!!.readText()
        assertFalse("READ_MEDIA_IMAGES must not be declared", "READ_MEDIA_IMAGES" in xml)
        assertFalse("READ_EXTERNAL_STORAGE must not be declared", "READ_EXTERNAL_STORAGE" in xml)
        assertFalse("READ_MEDIA_VISUAL_USER_SELECTED must not be declared", "READ_MEDIA_VISUAL_USER_SELECTED" in xml)
    }

    @Test
    fun cloudProvidersRequireExplicitPerProviderConsent() {
        val noConsent = emptySet<String>()
        assertTrue(CloudProviderPolicy.hasConsent(noConsent, AiProviderType.LOCAL_RULES))
        assertTrue(CloudProviderPolicy.hasConsent(noConsent, AiProviderType.LOCAL_LLM))
        assertTrue(CloudProviderPolicy.hasConsent(noConsent, AiProviderType.GEMINI_NANO))

        val cloudProviders = listOf(
            AiProviderType.GEMINI_API,
            AiProviderType.GROQ_API,
            AiProviderType.OPENROUTER_API,
            AiProviderType.XAI_API,
            AiProviderType.OPENAI_COMPATIBLE
        )
        cloudProviders.forEach { provider ->
            assertFalse("$provider should require consent", CloudProviderPolicy.hasConsent(noConsent, provider))
            assertTrue(
                "$provider should allow after consent",
                CloudProviderPolicy.hasConsent(setOf(provider.name), provider)
            )
        }
    }
}
