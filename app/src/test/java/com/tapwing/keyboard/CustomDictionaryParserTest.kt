package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CustomDictionaryParserTest {

    @Test
    fun parsesPlainWordsCsvGboardTsvArrowsAndJson() {
        val mixed = """
            # Kommentarzeile
            Tapwing
            Quantencomputer,42
            mfg => Mit freundlichen Grüßen
            addr	Musterstraße 12	de
            	Kubernetes	en
        """.trimIndent()

        val parsed = CustomDictionaryParser.parse(mixed)
        assertEquals(10, parsed.words["Tapwing"])
        assertEquals(42, parsed.words["Quantencomputer"])
        assertEquals(10, parsed.words["Kubernetes"])
        assertEquals("Mit freundlichen Grüßen", parsed.shortcuts["mfg"])
        assertEquals("Musterstraße 12", parsed.shortcuts["addr"])

        val json = CustomDictionaryParser.exportToJson(parsed.words, parsed.shortcuts)
        val roundTrip = CustomDictionaryParser.parse(json)
        assertEquals(parsed.words, roundTrip.words)
        assertEquals(parsed.shortcuts, roundTrip.shortcuts)
    }

    @Test
    fun importsPixelBoardGboardZipWithBomLocalesPosTagAndMacJunkIgnored() {
        val gboardDict = "\uFEFF# Gboard Dictionary version:1\n" +
            "\tPixelBoardWort\tde-DE\tNOUN\n" +
            "twzip\tTapwing Zip Gruß\tde-DE\t\n" +
            "brb\tBe right back\ten-US\n"

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("__MACOSX/"))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("__MACOSX/._dictionary.txt"))
                zos.write("JunkWordShouldBeIgnored\n".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("dictionary.txt"))
                zos.write(gboardDict.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        assertTrue(CustomDictionaryParser.isZipMagic(zipBytes))
        val parsed = CustomDictionaryParser.parseBytes(zipBytes)
        assertEquals("gboard_zip", parsed.sourceFormat)
        assertEquals(1, parsed.processedEntries)
        assertEquals(10, parsed.words["PixelBoardWort"])
        assertEquals("Tapwing Zip Gruß", parsed.shortcuts["twzip"])
        assertEquals("Be right back", parsed.shortcuts["brb"])
        assertEquals("de-DE", parsed.locales["pixelboardwort"])
        assertEquals("de-DE", parsed.locales["twzip"])
        assertEquals("en-US", parsed.locales["brb"])
        assertFalse(parsed.words.containsKey("JunkWordShouldBeIgnored"))
    }

    @Test
    fun exportsAndReimportsPixelBoardGboardZipRoundTrip() {
        val store = AdaptiveDictionaryStore(storageFile = null)
        store.addCustomWord("TapwingKeyboard", weight = 55)
        store.addShortcut("gutenmorgen", "Guten Morgen zusammen!")

        val zipBytes = ByteArrayOutputStream().use { out ->
            val count = store.exportGboardZip(out, defaultLocaleTag = "de-DE")
            assertTrue(count >= 2)
            out.toByteArray()
        }

        // Verify ZIP contains dictionary.txt with exact Gboard Dictionary version:1 header
        var foundDictionaryTxt = false
        var exportedText = ""
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            val entry = zis.nextEntry
            if (entry != null && entry.name == "dictionary.txt") {
                foundDictionaryTxt = true
                exportedText = zis.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(foundDictionaryTxt)
        assertTrue(exportedText.startsWith("# Gboard Dictionary version:1\n"))
        assertTrue(exportedText.contains("\tTapwingKeyboard\tde-DE"))
        assertTrue(exportedText.contains("gutenmorgen\tGuten Morgen zusammen!\tde-DE"))

        // Re-import into a fresh store via importStream
        val importedStore = AdaptiveDictionaryStore(storageFile = null)
        val summary = importedStore.importStream(ByteArrayInputStream(zipBytes))
        assertEquals("gboard_zip", summary.sourceFormat)
        assertEquals(1, summary.addedWords)
        assertEquals(1, summary.addedShortcuts)
        assertTrue(importedStore.isKnownWord("TapwingKeyboard"))
        assertEquals("Guten Morgen zusammen!", importedStore.lookupShortcut("gutenmorgen", "de"))
    }
}
