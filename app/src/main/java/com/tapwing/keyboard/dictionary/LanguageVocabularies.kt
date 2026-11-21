package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.data.KeyboardLocales

/**
 * Multi-language offline vocabularies, typo correction tables, casing guards, and emoji triggers
 * supporting German, English, French, Spanish, Italian, Portuguese, Dutch, Polish, and Turkish.
 */
object LanguageVocabularies {

    val germanNeverAutoCapMidSentence = setOf(
        "und", "oder", "aber", "denn", "doch", "sondern", "weil", "dass", "ob", "wenn",
        "falls", "während", "obwohl", "damit", "bevor", "nachdem", "seit", "bis", "als", "wie",
        "ich", "du", "er", "sie", "es", "wir", "ihr", "mich", "mir", "dich", "dir", "sich",
        "uns", "euch", "ihm", "ihn", "ihnen", "man", "wer", "was", "wo", "wann", "warum",
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "eines", "einem", "einen",
        "kein", "keine", "keinen", "keinem", "keiner", "mein", "meine", "dein", "deine",
        "sein", "seine", "unser", "unsere", "euer", "eure", "dieser", "diese", "dieses",
        "in", "im", "an", "am", "auf", "aus", "bei", "beim", "für", "gegen", "mit", "nach",
        "ohne", "über", "um", "unter", "von", "vom", "vor", "zu", "zum", "zur", "zwischen",
        "nicht", "auch", "noch", "nur", "schon", "sehr", "mehr", "viel", "gut", "mal", "so",
        "ist", "sind", "war", "waren", "bin", "bist", "hat", "haben", "hatte", "wird", "werden",
        "kann", "können", "muss", "müssen", "soll", "sollen", "will", "wollen", "darf", "möchte",
        "hier", "dort", "da", "dann", "danach", "jetzt", "heute", "morgen", "gestern", "immer",
        "nie", "oft", "gerne", "gern", "bitte", "danke", "ja", "nein", "okay", "ok"
    )

    val germanCapitalNouns = setOf(
        "zeit", "tag", "jahr", "woche", "monat", "stunde", "minute", "uhr", "termin",
        "nachricht", "text", "bild", "foto", "handy", "telefon", "antwort", "frage",
        "problem", "lösung", "projekt", "arbeit", "büro", "haus", "auto",
        "freund", "familie", "kollege", "kunde", "meeting", "kaffee", "wasser",
        "idee", "grund", "beispiel", "dokument", "tastatur", "sprache", "einstellung",
        "rechnung", "adresse", "name", "nummer", "datum", "montag", "dienstag",
        "mittwoch", "donnerstag", "freitag", "samstag", "sonntag", "januar", "februar",
        "märz", "april", "mai", "juni", "juli", "august", "september", "oktober",
        "november", "dezember", "wochenende", "urlaub", "flug", "zug", "bahnhof",
        "stadt", "land", "welt", "abend", "nacht", "mittag", "grüße", "danke"
    )

    private val germanTypoMap = mapOf(
        "udn" to "und",
        "unds" to "und",
        "nciht" to "nicht",
        "niht" to "nicht",
        "nicth" to "nicht",
        "ihc" to "ich",
        "ichh" to "ich",
        "siht" to "sieht",
        "vieleicht" to "vielleicht",
        "vilicht" to "vielleicht",
        "vllt" to "vielleicht",
        "warumm" to "warum",
        "morgn" to "morgen",
        "heut" to "heute",
        "wiedre" to "wieder",
        "weider" to "wieder",
        "danek" to "danke",
        "dakne" to "danke",
        "bitet" to "bitte",
        "halol" to "hallo",
        "gutenmorgen" to "guten Morgen",
        "schon" to "schön",
        "konnen" to "können",
        "konnte" to "könnte",
        "mochte" to "möchte",
        "moechte" to "möchte",
        "fuer" to "für",
        "ueber" to "über",
        "groesse" to "Größe",
        "strasse" to "Straße",
        "eignentlich" to "eigentlich",
        "natuerlich" to "natürlich",
        "naechste" to "nächste",
        "spaeter" to "später",
        "frueher" to "früher",
        "gluecklich" to "glücklich",
        "wuensche" to "wünsche",
        "gruesse" to "Grüße",
        "geschaeft" to "Geschäft",
        "ubrigens" to "übrigens",
        "uebrigens" to "übrigens",
        "standard" to "Standard",
        "standart" to "Standard",
        "eigendlich" to "eigentlich",
        "entgültig" to "endgültig",
        "maschiene" to "Maschine",
        "seperat" to "separat",
        "vorraus" to "voraus",
        "rythmus" to "Rhythmus",
        "bishen" to "bisschen",
        "bischen" to "bisschen"
    )

    private val englishTypoMap = mapOf(
        "teh" to "the",
        "hte" to "the",
        "adn" to "and",
        "nad" to "and",
        "recieve" to "receive",
        "beleive" to "believe",
        "seperate" to "separate",
        "definately" to "definitely",
        "definatly" to "definitely",
        "untill" to "until",
        "becuase" to "because",
        "beacuse" to "because",
        "thier" to "their",
        "wehn" to "when",
        "wiht" to "with",
        "wont" to "won't",
        "dont" to "don't",
        "cant" to "can't",
        "didnt" to "didn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "couldnt" to "couldn't",
        "im" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "youre" to "you're",
        "theyre" to "they're",
        "accomodate" to "accommodate",
        "occured" to "occurred",
        "tommorow" to "tomorrow",
        "tomorow" to "tomorrow",
        "thx" to "thanks",
        "plz" to "please",
        "wich" to "which",
        "tset" to "test"
    )

    private val frenchTypoMap = mapOf(
        "ca" to "ça",
        "etre" to "être",
        "tres" to "très",
        "apres" to "après",
        "deja" to "déjà",
        "voila" to "voilà",
        "aujourdhui" to "aujourd'hui",
        "bientot" to "bientôt",
        "toujours" to "toujours",
        "peut etre" to "peut-être",
        "desole" to "désolé",
        "francais" to "français",
        "problemes" to "problèmes",
        "etait" to "était"
    )

    private val spanishTypoMap = mapOf(
        "tambien" to "también",
        "mas" to "más",
        "manana" to "mañana",
        "espanol" to "español",
        "despues" to "después",
        "aqui" to "aquí",
        "esta" to "está",
        "asi" to "así",
        "adonde" to "adónde",
        "numero" to "número",
        "rapido" to "rápido",
        "facil" to "fácil",
        "dificil" to "difícil",
        "perdon" to "perdón"
    )

    private val italianTypoMap = mapOf(
        "perche" to "perché",
        "piu" to "più",
        "cosi" to "così",
        "puo" to "può",
        "cioe" to "cioè",
        "gia" to "già",
        "lunedi" to "lunedì",
        "martedi" to "martedì",
        "mercoledi" to "mercoledì",
        "giovedi" to "giovedì",
        "venerdi" to "venerdì",
        "citta" to "città"
    )

    private val portugueseTypoMap = mapOf(
        "nao" to "não",
        "entao" to "então",
        "voce" to "você",
        "tambem" to "também",
        "ate" to "até",
        "amanha" to "amanhã",
        "coracao" to "coração",
        "informacao" to "informação",
        "obrigado" to "obrigado",
        "portugues" to "português",
        "ja" to "já"
    )

    private val dutchTypoMap = mapOf(
        "misschien" to "misschien",
        "eigelijk" to "eigenlijk",
        "natuurlijk" to "natuurlijk",
        "alstublieft" to "alstublieft",
        "dankjewel" to "dankjewel",
        "alsjeblieft" to "alsjeblieft",
        "waarschijnlijk" to "waarschijnlijk",
        "gefeliciteerd" to "gefeliciteerd"
    )

    private val polishTypoMap = mapOf(
        "dziekuje" to "dziękuję",
        "prosze" to "proszę",
        "czesc" to "cześć",
        "zolty" to "żółty",
        "dzien" to "dzień",
        "przepraszam" to "przepraszam",
        "mozna" to "można",
        "bedzie" to "będzie",
        "wiecej" to "więcej"
    )

    private val turkishTypoMap = mapOf(
        "tesekkur" to "teşekkür",
        "tesekkurler" to "teşekkürler",
        "lutfen" to "lütfen",
        "gunaydin" to "günaydın",
        "gorusuruz" to "görüşürüz",
        "iyiyim" to "iyiyim",
        "nasilsin" to "nasılsın",
        "simdi" to "şimdi",
        "cunku" to "çünkü",
        "turkce" to "Türkçe"
    )

    // Ordered roughly by real-world conversational frequency so common words win prefix ties.
    val germanWords = listOf(
        "ich", "und", "die", "der", "das", "ist", "nicht", "ein", "es", "du", "zu", "in",
        "wir", "auf", "mit", "sie", "für", "auch", "eine", "haben", "so", "wie", "aber",
        "noch", "mir", "mich", "wenn", "was", "dann", "an", "am", "im", "von", "zu",
        "kann", "habe", "hat", "sind", "war", "wird", "werden", "dass", "bei", "nach",
        "oder", "nur", "schon", "hier", "mal", "ja", "nein", "danke", "bitte", "hallo",
        "gut", "gute", "guten", "heute", "morgen", "gestern", "jetzt", "gleich", "gerade",
        "immer", "wieder", "mehr", "sehr", "viel", "viele", "vielleicht", "eigentlich",
        "natürlich", "klar", "genau", "super", "perfekt", "okay", "alles", "alle",
        "machen", "macht", "gemacht", "gehen", "geht", "kommen", "kommt", "sehen",
        "sagen", "sagt", "wissen", "weiß", "geben", "gibt", "nehmen", "finden", "bleiben",
        "brauchen", "brauche", "wollen", "will", "wollte", "können", "könnte", "müssen",
        "muss", "sollen", "soll", "sollte", "möchte", "würde", "hätte", "wäre",
        "dein", "deine", "mein", "meine", "meinen", "unser", "unsere", "euer",
        "dieser", "diese", "dieses", "jeder", "jede", "jedes", "jemand", "niemand",
        "etwas", "nichts", "einfach", "schnell", "kurz", "lange", "später", "früher",
        "zusammen", "allein", "fertig", "möglich", "wichtig", "richtig", "falsch",
        "schön", "schöne", "groß", "klein", "neu", "neue", "alt", "letzte", "nächste",
        "über", "unter", "vor", "hinter", "zwischen", "durch", "gegen", "ohne", "wegen",
        "weil", "obwohl", "damit", "deshalb", "trotzdem", "übrigens", "außerdem",
        "hoffentlich", "leider", "wahrscheinlich", "bestimmt", "wirklich", "total",
        "gerne", "gern", "sofort", "zuerst", "danach", "vorbei", "zurück",
        "Zeit", "Tag", "Jahr", "Woche", "Monat", "Stunde", "Minute", "Uhr", "Termin",
        "Nachricht", "Text", "Bild", "Foto", "Handy", "Telefon", "Antwort", "Frage",
        "Problem", "Lösung", "Projekt", "Arbeit", "Büro", "Haus", "Auto", "Weg",
        "Freund", "Familie", "Kollege", "Kunde", "Meeting", "Kaffee", "Wasser",
        "Idee", "Grund", "Beispiel", "Dokument", "Tastatur", "Sprache", "Einstellung",
        "Wochenende", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag",
        "Samstag", "Sonntag", "Abend", "Mittag", "Grüße", "Liebe", "Viele",
        "schicken", "schicke", "schreiben", "schreibe", "lesen", "schauen", "telefonieren",
        "treffen", "sprechen", "denken", "glauben", "hoffen", "freuen", "wünschen",
        "verstehen", "erklären", "helfen", "Hilfe", "arbeiten", "lernen", "testen", "läuft"
    )

    val englishWords = listOf(
        "the", "and", "to", "you", "I", "that", "it", "is", "in", "for", "of", "on",
        "with", "this", "we", "are", "be", "have", "not", "but", "what", "so", "can",
        "do", "if", "just", "my", "your", "me", "at", "from", "will", "about", "all",
        "they", "as", "or", "an", "would", "there", "like", "out", "up", "when", "know",
        "how", "get", "go", "time", "good", "great", "now", "here", "make", "see",
        "think", "also", "well", "want", "need", "back", "one", "could", "should",
        "hello", "hey", "hi", "thanks", "thank", "please", "sorry", "okay", "yes", "no",
        "today", "tomorrow", "yesterday", "morning", "evening", "night", "week", "month",
        "year", "hour", "minute", "meeting", "message", "text", "email", "phone", "call",
        "work", "project", "team", "home", "friend", "family", "coffee", "water",
        "really", "actually", "probably", "definitely", "maybe", "always", "never",
        "sometimes", "usually", "already", "still", "soon", "later", "right", "sure",
        "perfect", "awesome", "nice", "happy", "helpful", "important", "possible",
        "ready", "done", "check", "send", "sent", "share", "update", "review", "reply",
        "confirm", "schedule", "build", "create", "change", "improve", "keyboard",
        "voice", "language", "settings", "because", "although", "however", "between",
        "around", "before", "after", "during", "through", "without", "together",
        "everyone", "everything", "someone", "something", "anything", "nothing",
        "don't", "can't", "won't", "didn't", "isn't", "I'm", "I've", "I'll", "you're",
        "it's", "that's", "we're", "they're", "question", "answer", "problem", "idea"
    )

    val frenchWords = listOf(
        "je", "de", "est", "pas", "le", "la", "et", "à", "un", "une", "que", "pour",
        "il", "elle", "nous", "vous", "ils", "dans", "en", "du", "des", "au", "aux",
        "ce", "cette", "ces", "mon", "ma", "mes", "ton", "ta", "son", "sa", "notre",
        "votre", "mais", "ou", "donc", "avec", "sans", "sur", "sous", "par", "plus",
        "très", "bien", "bon", "bonne", "bonjour", "salut", "merci", "oui", "non",
        "pardon", "désolé", "d'accord", "aujourd'hui", "demain", "hier", "maintenant",
        "toujours", "jamais", "souvent", "peut-être", "vrai", "faire", "fait", "aller",
        "vais", "venir", "voir", "dire", "savoir", "pouvoir", "vouloir", "devoir",
        "être", "avoir", "suis", "es", "sommes", "êtes", "sont", "ai", "as", "avons",
        "avez", "ont", "temps", "jour", "semaine", "mois", "année", "heure", "minute",
        "message", "travail", "projet", "réunion", "café", "ami", "famille", "maison",
        "problème", "question", "réponse", "idée", "clavier", "langue", "paramètres",
        "parce", "quand", "comment", "pourquoi", "où", "qui", "quoi", "quel", "quelle",
        "encore", "déjà", "après", "avant", "pendant", "chez", "vers", "entre", "aussi",
        "beaucoup", "peu", "trop", "tout", "tous", "toute", "rien", "quelque", "chose"
    )

    val spanishWords = listOf(
        "de", "la", "que", "el", "en", "y", "a", "los", "se", "del", "las", "un", "por",
        "con", "no", "una", "su", "para", "es", "al", "lo", "como", "más", "pero",
        "sus", "le", "ya", "o", "este", "sí", "porque", "esta", "entre", "cuando",
        "muy", "sin", "sobre", "también", "me", "hasta", "hay", "donde", "quien",
        "desde", "todo", "nos", "durante", "todos", "uno", "les", "ni", "contra",
        "hola", "gracias", "por favor", "perdón", "buenos", "días", "tardes", "noches",
        "hoy", "mañana", "ayer", "ahora", "siempre", "nunca", "después", "antes",
        "bien", "bueno", "buena", "mejor", "grande", "pequeño", "nuevo", "nueva",
        "hacer", "tener", "estar", "ser", "ir", "ver", "dar", "saber", "querer", "poder",
        "tengo", "tienes", "tiene", "estoy", "está", "estamos", "soy", "eres", "somos",
        "tiempo", "día", "semana", "mes", "año", "hora", "minuto", "trabajo", "casa",
        "mensaje", "proyecto", "reunión", "café", "amigo", "familia", "problema",
        "pregunta", "respuesta", "idea", "teclado", "idioma", "ajustes", "aquí", "allí"
    )

    val italianWords = listOf(
        "di", "che", "è", "e", "la", "il", "un", "a", "per", "in", "non", "una", "sono",
        "con", "si", "da", "mi", "le", "ti", "lo", "ha", "ho", "ma", "come", "più",
        "anche", "questo", "questa", "quello", "ciao", "salve", "grazie", "prego",
        "scusa", "buongiorno", "buonasera", "oggi", "domani", "ieri", "adesso", "sempre",
        "mai", "bene", "buono", "buona", "molto", "poco", "tutto", "tutti", "niente",
        "fare", "stare", "essere", "avere", "andare", "venire", "dire", "sapere",
        "potere", "volere", "vedere", "posso", "può", "voglio", "tempo", "giorno",
        "settimana", "mese", "anno", "ora", "minuto", "lavoro", "casa", "messaggio",
        "progetto", "riunione", "caffè", "amico", "famiglia", "problema", "domanda",
        "risposta", "idea", "tastiera", "lingua", "impostazioni", "perché", "quando", "dove"
    )

    val portugueseWords = listOf(
        "de", "a", "o", "que", "e", "do", "da", "em", "um", "para", "é", "com", "não",
        "uma", "os", "no", "se", "na", "por", "mais", "as", "dos", "como", "mas",
        "foi", "ao", "ele", "das", "tem", "à", "seu", "sua", "ou", "ser", "quando",
        "muito", "há", "nos", "já", "está", "eu", "também", "só", "pelo", "pela", "até",
        "olá", "oi", "obrigado", "obrigada", "por favor", "desculpa", "bom", "boa",
        "hoje", "amanhã", "ontem", "agora", "sempre", "nunca", "depois", "antes",
        "bem", "melhor", "grande", "novo", "nova", "fazer", "ter", "ir", "ver", "dar",
        "saber", "querer", "poder", "tempo", "dia", "semana", "mês", "ano", "hora",
        "trabalho", "casa", "mensagem", "projeto", "reunião", "café", "amigo", "família"
    )

    val dutchWords = listOf(
        "de", "van", "een", "het", "en", "in", "ik", "dat", "is", "te", "op", "voor",
        "met", "zijn", "niet", "aan", "er", "maar", "om", "ook", "als", "dan", "nog",
        "wel", "bij", "uit", "naar", "wat", "wie", "waar", "wanneer", "waarom", "hoe",
        "hallo", "hoi", "bedankt", "dank", "alsjeblieft", "alstublieft", "sorry",
        "ja", "nee", "goed", "beter", "vandaag", "morgen", "gisteren", "nu", "altijd",
        "nooit", "misschien", "eigenlijk", "natuurlijk", "hebben", "kunnen", "willen",
        "moeten", "gaan", "komen", "doen", "maken", "zien", "zeggen", "weten", "tijd",
        "dag", "week", "maand", "jaar", "uur", "werk", "huis", "bericht", "koffie"
    )

    val polishWords = listOf(
        "w", "i", "nie", "na", "z", "do", "to", "się", "jest", "że", "o", "jak", "a",
        "co", "za", "od", "po", "ale", "tak", "czy", "już", "tylko", "może", "bardzo",
        "cześć", "dzień", "dobry", "dziękuję", "proszę", "przepraszam", "dobrze",
        "dzisiaj", "jutro", "wczoraj", "teraz", "zawsze", "nigdy", "kiedy", "gdzie",
        "dlaczego", "czas", "tydzień", "miesiąc", "rok", "godzina", "praca", "dom",
        "wiadomość", "projekt", "spotkanie", "kawa", "klawiatura", "język", "ustawienia"
    )

    val turkishWords = listOf(
        "ve", "bir", "bu", "da", "de", "ne", "için", "ile", "çok", "ben", "sen", "o",
        "biz", "siz", "var", "yok", "ama", "fakat", "çünkü", "nasıl", "neden", "nerede",
        "merhaba", "selam", "teşekkürler", "teşekkür", "lütfen", "özür", "evet", "hayır",
        "günaydın", "iyi", "güzel", "bugün", "yarın", "dün", "şimdi", "her", "zaman",
        "asla", "belki", "tamam", "yapmak", "gitmek", "gelmek", "görmek", "bilmek",
        "zaman", "gün", "hafta", "ay", "yıl", "saat", "iş", "ev", "mesaj", "kahve"
    )

    val emojiTriggers = mapOf(
        "danke" to "🙏", "thanks" to "🙏", "thank" to "🙏", "merci" to "🙏", "gracias" to "🙏",
        "grazie" to "🙏", "obrigado" to "🙏", "bedankt" to "🙏", "dziękuję" to "🙏", "teşekkürler" to "🙏",
        "herz" to "❤️", "liebe" to "❤️", "love" to "❤️", "amour" to "❤️", "amor" to "❤️",
        "amore" to "❤️", "liefde" to "❤️", "miłość" to "❤️", "aşk" to "❤️",
        "lachen" to "😂", "haha" to "😂", "lustig" to "😂", "funny" to "😂", "lol" to "😂",
        "feuer" to "🔥", "fire" to "🔥", "krass" to "🔥", "genial" to "🔥",
        "ok" to "👍", "okay" to "👍", "top" to "👍", "gut" to "👍", "good" to "👍",
        "super" to "✨", "perfekt" to "✨", "perfect" to "✨", "parfait" to "✨", "perfecto" to "✨",
        "stern" to "⭐", "star" to "⭐",
        "kaffee" to "☕", "coffee" to "☕", "café" to "☕", "caffè" to "☕", "koffie" to "☕", "kawa" to "☕", "kahve" to "☕",
        "sonne" to "☀️", "sun" to "☀️", "soleil" to "☀️", "sol" to "☀️",
        "party" to "🎉", "glückwunsch" to "🎉", "congrats" to "🎉", "félicitations" to "🎉",
        "musik" to "🎵", "music" to "🎵", "musique" to "🎵", "música" to "🎵",
        "idee" to "💡", "idea" to "💡", "idée" to "💡",
        "zeit" to "⏰", "time" to "⏰", "uhr" to "⏰",
        "geld" to "💶", "money" to "💵",
        "hallo" to "👋", "hello" to "👋", "hey" to "👋", "bonjour" to "👋", "hola" to "👋", "ciao" to "👋", "merhaba" to "👋",
        "traurig" to "😢", "sad" to "😢",
        "check" to "✅", "erledigt" to "✅", "done" to "✅"
    )

    fun vocabularyFor(locale: String): List<String> = when (KeyboardLocales.baseLang(locale)) {
        "de" -> germanWords
        "en" -> englishWords
        "fr" -> frenchWords
        "es" -> spanishWords
        "it" -> italianWords
        "pt" -> portugueseWords
        "nl" -> dutchWords
        "pl" -> polishWords
        "tr" -> turkishWords
        else -> englishWords
    }

    fun typoMapFor(locale: String): Map<String, String> = when (KeyboardLocales.baseLang(locale)) {
        "de" -> germanTypoMap
        "en" -> englishTypoMap
        "fr" -> frenchTypoMap
        "es" -> spanishTypoMap
        "it" -> italianTypoMap
        "pt" -> portugueseTypoMap
        "nl" -> dutchTypoMap
        "pl" -> polishTypoMap
        "tr" -> turkishTypoMap
        else -> englishTypoMap
    }

    private val germanNextWords = mapOf(
        "ich" to listOf("bin", "habe", "kann"),
        "du" to listOf("bist", "hast", "kannst"),
        "wir" to listOf("sind", "haben", "können"),
        "bin" to listOf("gleich", "schon", "noch"),
        "bist" to listOf("du", "schon", "noch"),
        "gleich" to listOf("da", "wieder", "fertig"),
        "schon" to listOf("da", "unterwegs", "fertig"),
        "habe" to listOf("noch", "gerade", "Zeit"),
        "kann" to listOf("dir", "das", "noch"),
        "kannst" to listOf("du", "mir", "das"),
        "danke" to listOf("dir", "für", "vielmals"),
        "vielen" to listOf("Dank", "Grüßen"),
        "guten" to listOf("Morgen", "Tag", "Abend"),
        "bis" to listOf("gleich", "morgen", "später"),
        "alles" to listOf("klar", "Gute", "gut"),
        "morgen" to listOf("früh", "wieder", "um"),
        "und" to listOf("dann", "ich", "so")
    )
    private val englishNextWords = mapOf(
        "i" to listOf("am", "have", "can"),
        "you" to listOf("are", "can", "have"),
        "we" to listOf("are", "can", "will"),
        "am" to listOf("on", "going", "still"),
        "are" to listOf("you", "we", "still"),
        "on" to listOf("my", "the", "your"),
        "my" to listOf("way", "phone", "own"),
        "going" to listOf("to", "home", "out"),
        "thank" to listOf("you", "goodness"),
        "thanks" to listOf("for", "again", "so"),
        "see" to listOf("you", "the", "if"),
        "good" to listOf("morning", "night", "luck"),
        "have" to listOf("a", "to", "been"),
        "can" to listOf("you", "we", "I"),
        "the" to listOf("same", "next", "first"),
        "and" to listOf("then", "the", "I")
    )

    fun commonNextWords(locale: String, previousWord: String): List<String> {
        val words = when (KeyboardLocales.baseLang(locale)) {
            "de" -> germanNextWords
            "en" -> englishNextWords
            else -> return emptyList()
        }
        return words[previousWord.lowercase()].orEmpty()
    }

    fun defaultEmptyChipsFor(locale: String): List<String> = when (KeyboardLocales.baseLang(locale)) {
        "de" -> listOf("Hallo", "Danke", "Okay")
        "fr" -> listOf("Bonjour", "Merci", "D'accord")
        "es" -> listOf("Hola", "Gracias", "Vale")
        "it" -> listOf("Ciao", "Grazie", "Va bene")
        "pt" -> listOf("Olá", "Obrigado", "Tudo bem")
        "nl" -> listOf("Hallo", "Bedankt", "Oké")
        "pl" -> listOf("Cześć", "Dziękuję", "Dobrze")
        "tr" -> listOf("Merhaba", "Teşekkürler", "Tamam")
        else -> listOf("Hello", "Thanks", "Okay")
    }

    fun isNeverAutoCapitalizeMidSentence(word: String, locale: String): Boolean {
        val base = KeyboardLocales.baseLang(locale)
        val lower = word.lowercase()
        return if (base == "de") lower in germanNeverAutoCapMidSentence else false
    }
}
