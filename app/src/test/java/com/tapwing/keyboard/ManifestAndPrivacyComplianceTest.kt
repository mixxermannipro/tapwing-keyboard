// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard

import com.tapwing.keyboard.ai.CloudProviderPolicy
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.StorageMigration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ManifestAndPrivacyComplianceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun manifestDoesNotDeclareBroadMediaOrStoragePermissions() {
        val manifestFile = locateFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")
        assertTrue("Could not locate AndroidManifest.xml", manifestFile != null)

        val xml = manifestFile!!.readText()
        assertFalse("READ_MEDIA_IMAGES must not be declared", "READ_MEDIA_IMAGES" in xml)
        assertFalse("READ_EXTERNAL_STORAGE must not be declared", "READ_EXTERNAL_STORAGE" in xml)
        assertFalse("READ_MEDIA_VISUAL_USER_SELECTED must not be declared", "READ_MEDIA_VISUAL_USER_SELECTED" in xml)
        assertTrue(
            "Manifest must declare android:dataExtractionRules",
            """android:dataExtractionRules="@xml/data_extraction_rules"""" in xml
        )
    }

    @Test
    fun dataExtractionRulesExcludeCloudBackupAndDeviceTransfer() {
        val rulesFile = locateFile(
            "app/src/main/res/xml/data_extraction_rules.xml",
            "src/main/res/xml/data_extraction_rules.xml"
        )
        assertTrue("Could not locate data_extraction_rules.xml", rulesFile != null)
        val xml = rulesFile!!.readText()
        assertTrue("Expected <cloud-backup> section", "<cloud-backup>" in xml)
        assertTrue("Expected <device-transfer> section", "<device-transfer>" in xml)
        assertTrue("Expected file domain exclusion", """<exclude domain="file" path="." />""" in xml)
        assertTrue("Expected sharedpref domain exclusion", """<exclude domain="sharedpref" path="." />""" in xml)
        assertTrue("Expected database domain exclusion", """<exclude domain="database" path="." />""" in xml)
    }

    @Test
    fun appBundleDisablesLanguageSplitsForInAppLocaleSwitching() {
        val gradleFile = locateFile("app/build.gradle.kts", "build.gradle.kts")
        assertTrue("Could not locate app/build.gradle.kts", gradleFile != null)
        val kts = gradleFile!!.readText()
        assertTrue("Expected enableSplit = false for AAB languages", "enableSplit = false" in kts)
    }

    @Test
    fun networkSecurityConfigAllowsIpv6Loopback() {
        val netSecFile = locateFile(
            "app/src/main/res/xml/network_security_config.xml",
            "src/main/res/xml/network_security_config.xml"
        )
        assertTrue("Could not locate network_security_config.xml", netSecFile != null)
        val xml = netSecFile!!.readText()
        assertTrue("Expected ::1 loopback entry", ">::1<" in xml)
    }

    @Test
    fun secureDeleteAlsoWipesSiblingTmpFile() {
        val target = File(tempFolder.root, "sensitive.json").apply { writeText("secret") }
        val siblingTmp = File(tempFolder.root, "sensitive.json.tmp").apply { writeText("temp-secret") }

        assertTrue(StorageMigration.secureDelete(target))
        assertFalse(target.exists())
        assertFalse(siblingTmp.exists())
    }

    private fun locateFile(vararg candidates: String): File? =
        candidates.map(::File).firstOrNull { it.isFile }

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
