package com.tapwing.keyboard.emoji

import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {

    @Test
    fun searchesGermanAndEnglishKeywordsWithUmlautFolding() {
        val laugh = EmojiCatalog.search("lachen")
        assertTrue("Expected 😂 in $laugh", "😂" in laugh)

        val coffee = EmojiCatalog.search("kaffee")
        assertTrue("Expected ☕ in $coffee", "☕" in coffee)

        // Umlaut-insensitive folding: "gluck" finds "glück"
        val luck = EmojiCatalog.search("gluck")
        assertTrue("Expected non-empty luck results", luck.isNotEmpty())

        val love = EmojiCatalog.search("love")
        assertTrue("Expected ❤️ or 😍 in $love", "❤️" in love || "😍" in love)
    }

    @Test
    fun quickSearchTagsAllReturnMatches() {
        (EmojiCatalog.quickSearchTagsDe + EmojiCatalog.quickSearchTagsEn).forEach { (_, tag) ->
            assertTrue("Expected matches for tag '$tag'", EmojiCatalog.search(tag).isNotEmpty())
        }
    }
}
