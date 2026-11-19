package com.tapwing.keyboard.emoji

data class EmojiCategory(
    val id: String,
    val icon: String,
    val titleDe: String,
    val titleEn: String,
    val emojis: List<Pair<String, String>> // emoji to search keywords
)

object EmojiCatalog {

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory(
            id = "smileys",
            icon = "😀",
            titleDe = "Smileys",
            titleEn = "Smileys",
            emojis = listOf(
                "😀" to "lachen smile happy grinsen",
                "😃" to "froh smile",
                "😄" to "lachen happy",
                "😁" to "grinsen grin",
                "😅" to "schwitzen nervous laugh",
                "😂" to "lachen tränen funny lol",
                "🤣" to "rofl lachen funny",
                "😊" to "lächeln blush warm",
                "😇" to "engel innocent",
                "🙂" to "lächeln ok",
                "😉" to "zwinkern wink",
                "😍" to "liebe herzaugen love",
                "🥰" to "verliebt liebe love",
                "😘" to "kuss kiss love",
                "😋" to "lecker yummy",
                "😎" to "cool sonnenbrille",
                "🤩" to "stern wow star",
                "🥳" to "party feiern celebrate",
                "🤔" to "denken hmm think",
                "🤗" to "umarmen hug",
                "🤫" to "psst leise quiet",
                "🫡" to "salut ok respect",
                "😐" to "neutral",
                "🙄" to "augenrollen eyeroll",
                "😬" to "oops uff",
                "😌" to "entspannt relieved",
                "😴" to "schlafen sleep",
                "😷" to "krank maske sick",
                "🤯" to "wow mindblown",
                "🥺" to "bitte please puppy",
                "😢" to "traurig weinen sad",
                "😭" to "weinen heulen cry",
                "😤" to "genervt frustrated",
                "😡" to "wütend angry",
                "😱" to "schock omg shock"
            )
        ),
        EmojiCategory(
            id = "gestures",
            icon = "👍",
            titleDe = "Gesten",
            titleEn = "Gestures",
            emojis = listOf(
                "👍" to "daumen hoch ok gut top",
                "👎" to "daumen runter schlecht",
                "👌" to "perfekt ok",
                "✌️" to "peace sieg",
                "🤞" to "daumen drücken luck",
                "🤟" to "liebe rock",
                "👏" to "applaus klatschen clap",
                "🙌" to "hurra yay",
                "🙏" to "danke bitte pray thanks",
                "🤝" to "handshake deal abgemacht",
                "👋" to "hallo tschüss winken wave",
                "💪" to "stark kraft power",
                "✍️" to "schreiben write",
                "👀" to "schauen augen look",
                "🧠" to "gehirn idee smart",
                "🫀" to "herz"
            )
        ),
        EmojiCategory(
            id = "hearts",
            icon = "❤️",
            titleDe = "Herzen",
            titleEn = "Hearts",
            emojis = listOf(
                "❤️" to "herz liebe love red",
                "🧡" to "herz orange",
                "💛" to "herz gelb",
                "💚" to "herz grün",
                "💙" to "herz blau",
                "💜" to "herz lila violet",
                "🩷" to "herz pink",
                "🤍" to "herz weiß",
                "🖤" to "herz schwarz",
                "💖" to "funkel herz",
                "💘" to "amor herz",
                "🔥" to "feuer krass hot fire",
                "✨" to "funken magie ki sparkle",
                "⭐" to "stern star",
                "🌟" to "stern glanz",
                "💯" to "100 perfekt prozent"
            )
        ),
        EmojiCategory(
            id = "food",
            icon = "☕",
            titleDe = "Essen",
            titleEn = "Food",
            emojis = listOf(
                "☕" to "kaffee coffee tee",
                "🍵" to "tee matchatee",
                "🥐" to "croissant frühstück",
                "🍕" to "pizza essen",
                "🍔" to "burger essen",
                "🍟" to "pommes fries",
                "🥗" to "salat gesund",
                "🍣" to "sushi",
                "🍰" to "kuchen cake",
                "🎂" to "geburtstag torte birthday",
                "🍫" to "schokolade",
                "🍎" to "apfel obst",
                "🍓" to "erdbeere",
                "🥑" to "avocado",
                "🍺" to "bier prost beer",
                "🥂" to "sekt anstoßen cheers"
            )
        ),
        EmojiCategory(
            id = "nature",
            icon = "🌿",
            titleDe = "Natur",
            titleEn = "Nature",
            emojis = listOf(
                "☀️" to "sonne wetter sun",
                "🌤️" to "wolke sonne",
                "🌧️" to "regen rain",
                "⛈️" to "gewitter storm",
                "❄️" to "schnee kalt snow",
                "🌈" to "regenbogen rainbow",
                "🌸" to "blume frühling flower",
                "🌻" to "sonnenblume",
                "🌿" to "blatt natur pflanze",
                "🌳" to "baum wald",
                "🐶" to "hund dog",
                "🐱" to "katze cat",
                "🦊" to "fuchs fox",
                "🐝" to "biene bee",
                "🦋" to "schmetterling"
            )
        ),
        EmojiCategory(
            id = "work",
            icon = "💻",
            titleDe = "Arbeit & Tech",
            titleEn = "Work & Tech",
            emojis = listOf(
                "💻" to "laptop computer arbeit code",
                "📱" to "handy smartphone phone",
                "⌨️" to "tastatur keyboard",
                "📷" to "kamera foto scan ocr",
                "🎙️" to "mikrofon sprache voice",
                "📝" to "notiz text schreiben note",
                "📋" to "clipboard zwischenablage",
                "📅" to "kalender termin date",
                "⏰" to "wecker uhr zeit alarm",
                "📌" to "pin anheften",
                "🔒" to "schloss sicher privat lock",
                "🔑" to "schlüssel key",
                "💡" to "idee lampe idea",
                "🚀" to "rakete launch start rocket",
                "⚙️" to "einstellungen zahnrad settings"
            )
        ),
        EmojiCategory(
            id = "symbols",
            icon = "✅",
            titleDe = "Symbole",
            titleEn = "Symbols",
            emojis = listOf(
                "✅" to "haken erledigt ok done",
                "❌" to "fehler kreuz nein wrong",
                "⚠️" to "warnung achtung",
                "ℹ️" to "info",
                "❓" to "frage question",
                "❗" to "ausrufezeichen wichtig",
                "➕" to "plus",
                "➖" to "minus",
                "➡️" to "pfeil rechts",
                "⬅️" to "pfeil links",
                "🔄" to "aktualisieren reload",
                "🎉" to "konfetti party",
                "🏆" to "pokal gewinner",
                "🎵" to "musik song",
                "💶" to "euro geld money",
                "💵" to "dollar geld money"
            )
        )
    )

    fun search(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return categories.flatMap { cat -> cat.emojis.map { it.first } }
        return categories.flatMap { cat ->
            cat.emojis.filter { (emoji, keywords) ->
                emoji == q || keywords.contains(q)
            }.map { it.first }
        }.distinct()
    }
}
