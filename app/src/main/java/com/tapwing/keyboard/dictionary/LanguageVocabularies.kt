package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.data.KeyboardLocales
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-language offline vocabularies, O(1) prefix/SymSpell/diacritic indexes, typo tables,
 * German morphological noun capitalization, conversational next-word bigrams/trigrams,
 * and emoji triggers supporting DE, EN, FR, ES, IT, PT, NL, PL, and TR.
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
        "nie", "oft", "gerne", "gern", "bitte", "danke", "ja", "nein", "okay", "ok",
        "genug", "jung", "kurz", "lang", "schnell", "einfach", "richtig", "falsch", "wichtig"
    )

    val germanNounPrecursors = setOf(
        "der", "die", "das", "den", "dem", "des",
        "ein", "eine", "einen", "einem", "einer", "eines",
        "kein", "keine", "keinen", "keinem", "keiner",
        "mein", "meine", "meinen", "meinem", "meiner",
        "dein", "deine", "deinen", "deinem", "deiner",
        "sein", "seine", "unser", "unsere", "euer", "eure",
        "im", "am", "zum", "zur", "vom", "beim", "ins", "ans", "aufs"
    )

    val germanCapitalNouns = setOf(
        "zeit", "tag", "jahr", "woche", "monat", "stunde", "minute", "sekunde", "uhr", "termin",
        "nachricht", "text", "bild", "foto", "video", "handy", "telefon", "anruf", "antwort", "frage",
        "problem", "lösung", "projekt", "arbeit", "büro", "firma", "team", "chef", "haus", "wohnung",
        "zimmer", "küche", "auto", "fahrrad", "bus", "freund", "freundin", "familie", "mutter", "vater",
        "bruder", "schwester", "kind", "kinder", "kollege", "kollegin", "kunde", "meeting", "kaffee",
        "tee", "wasser", "essen", "frühstück", "mittagessen", "abendessen", "restaurant", "supermarkt",
        "idee", "grund", "beispiel", "dokument", "datei", "ordner", "link", "seite", "app", "system",
        "tastatur", "sprache", "einstellung", "einstellungen", "update", "version", "fehler", "funktion",
        "rechnung", "vertrag", "angebot", "bestellung", "lieferung", "paket", "preis", "geld", "euro",
        "bank", "konto", "karte", "adresse", "name", "vorname", "nachname", "nummer", "passwort",
        "datum", "kalender", "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag",
        "sonntag", "januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september",
        "oktober", "november", "dezember", "wochenende", "urlaub", "ferien", "reise", "hotel", "flug",
        "flughafen", "zug", "bahnhof", "straße", "platz", "stadt", "dorf", "land", "welt", "wetter",
        "sonne", "regen", "schnee", "wind", "sommer", "winter", "frühling", "herbst", "abend", "nacht",
        "mittag", "nachmittag", "vormittag", "grüße", "gruß", "dank", "hilfe", "erfolg", "glück",
        "spaß", "freude", "liebe", "herz", "kopf", "hand", "auge", "augen", "arzt", "krankenhaus",
        "apotheke", "schule", "uni", "universität", "prüfung", "kurs", "buch", "film", "musik", "spiel",
        "sport", "training", "verein", "party", "geburtstag", "geschenk", "plan", "ziel", "weg",
        "richtung", "anfang", "ende", "mitte", "pause", "chance", "meinung", "erfahrung", "gefühl",
        "gedanke", "entscheidung", "überraschung", "möglichkeit", "situation", "moment", "augenblick",
        "danke", "ahnung", "stress", "ding", "sache", "sachen", "thema", "punkt", "liste", "aufgabe",
        "aufgaben", "notiz", "notizen", "erinnerung", "vorschlag", "status", "details", "info", "infos",
        "informationen", "kontakt", "profil", "e-mail", "email", "telefonnummer", "handynummer",
        "postleitzahl", "geburtsdatum", "alter", "wohnort", "anschrift", "suche", "ergebnis",
        "ergebnisse", "browser", "internet", "wlan", "akku", "speicher", "kamera", "mikrofon"
    )

    private val germanNounSuffixExceptions = setOf(
        "genug", "jung", "eng", "lang", "streng", "gesund", "bunt", "rund",
        "machen", "lachen", "wachen", "suchen", "buchen", "kochen", "sprechen", "stechen",
        "brechen", "rechnen", "riechen", "kriechen", "weichen", "reichen", "gleichen", "streichen",
        "brauchen", "rauchen", "tauchen", "hauchen", "fluchen", "versuchen", "besuchen",
        "welchen", "solchen", "manchen", "zwischen", "bisschen", "ein bisschen", "geschehen"
    )

    /**
     * German morphological noun detector (like Gboard): words ending in unambiguous noun-forming
     * suffixes (-ung, -heit, -keit, -schaft, -tion, -tät, -ismus, -ment, -nis, -lein) are nouns.
     */
    fun isLikelyGermanNounBySuffix(lowerWord: String): Boolean {
        if (lowerWord.length < 5) return false
        if (lowerWord in germanNeverAutoCapMidSentence || lowerWord in germanNounSuffixExceptions) return false
        return lowerWord.endsWith("ung") ||
            lowerWord.endsWith("ungen") ||
            lowerWord.endsWith("heit") ||
            lowerWord.endsWith("heiten") ||
            lowerWord.endsWith("keit") ||
            lowerWord.endsWith("keiten") ||
            lowerWord.endsWith("schaft") ||
            lowerWord.endsWith("schaften") ||
            lowerWord.endsWith("tion") ||
            lowerWord.endsWith("tionen") ||
            lowerWord.endsWith("tät") ||
            lowerWord.endsWith("täten") ||
            lowerWord.endsWith("ismus") ||
            lowerWord.endsWith("ment") ||
            lowerWord.endsWith("mente") ||
            lowerWord.endsWith("nis") ||
            lowerWord.endsWith("nisse") ||
            lowerWord.endsWith("lein")
    }

    private val germanTypoMap = mapOf(
        "udn" to "und",
        "unds" to "und",
        "nciht" to "nicht",
        "niht" to "nicht",
        "nicth" to "nicht",
        "nich" to "nicht",
        "ihc" to "ich",
        "ichh" to "ich",
        "siht" to "sieht",
        "vieleicht" to "vielleicht",
        "vilicht" to "vielleicht",
        "vllt" to "vielleicht",
        "vlt" to "vielleicht",
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
        "gutenabend" to "guten Abend",
        "gutentag" to "guten Tag",
        "gutenacht" to "gute Nacht",
        "allesklar" to "alles klar",
        "keineahnung" to "keine Ahnung",
        "keinproblem" to "kein Problem",
        "bisgleich" to "bis gleich",
        "bismorgen" to "bis morgen",
        "bisspäter" to "bis später",
        "bisspaeter" to "bis später",
        "gehts" to "geht's",
        "gibts" to "gibt's",
        "wirds" to "wird's",
        "habs" to "hab's",
        "machs" to "mach's",
        "wenns" to "wenn's",
        "weils" to "weil's",
        "wiegehts" to "wie geht's",
        "schoen" to "schön",
        "konnen" to "können",
        "koennen" to "können",
        "koennte" to "könnte",
        "moechte" to "möchte",
        "waere" to "wäre",
        "haette" to "hätte",
        "wuerde" to "würde",
        "muesste" to "müsste",
        "muessen" to "müssen",
        "fuer" to "für",
        "ueber" to "über",
        "groesse" to "Größe",
        "groesser" to "größer",
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
        "standart" to "Standard",
        "eigendlich" to "eigentlich",
        "entgültig" to "endgültig",
        "maschiene" to "Maschine",
        "seperat" to "separat",
        "vorraus" to "voraus",
        "rythmus" to "Rhythmus",
        "bishen" to "bisschen",
        "bischen" to "bisschen",
        "warscheinlich" to "wahrscheinlich",
        "wahrcheinlich" to "wahrscheinlich",
        "definitv" to "definitiv",
        "funktionert" to "funktioniert",
        "funktoniert" to "funktioniert",
        "interresant" to "interessant",
        "interesant" to "interessant",
        "komunikation" to "Kommunikation",
        "reperatur" to "Reparatur",
        "agressiv" to "aggressiv",
        "emailadresse" to "E-Mail-Adresse",
        "telfon" to "Telefon",
        "telephon" to "Telefon",
        "nähmlich" to "nämlich",
        "häckchen" to "Häkchen",
        "packete" to "Pakete",
        "reflektion" to "Reflexion",
        "spass" to "Spaß",
        "weis" to "weiß",
        "heist" to "heißt",
        "heisst" to "heißt",
        "ausserdem" to "außerdem",
        "draussen" to "draußen",
        "schliessen" to "schließen",
        "oeffnen" to "öffnen",
        "aendern" to "ändern",
        "loeschen" to "löschen",
        "pruefen" to "prüfen",
        "zurueck" to "zurück",
        "genuegend" to "genügend",
        "koenntest" to "könntest",
        "wuerdest" to "würdest",
        "haettest" to "hättest",
        "waerst" to "wärst",
        "waeren" to "wären",
        "koennen" to "können",
        "moechtest" to "möchtest",
        "moechten" to "möchten",
        "laeuft" to "läuft",
        "faehrt" to "fährt",
        "traegt" to "trägt",
        "schlaeft" to "schläft",
        "hoert" to "hört",
        "fuehlt" to "fühlt"
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
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "couldnt" to "couldn't",
        "thats" to "that's",
        "whats" to "what's",
        "lets" to "let's",
        "im" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "youre" to "you're",
        "youve" to "you've",
        "youll" to "you'll",
        "theyre" to "they're",
        "theyve" to "they've",
        "theyll" to "they'll",
        "weve" to "we've",
        "hes" to "he's",
        "shes" to "she's",
        "theres" to "there's",
        "heres" to "here's",
        "wheres" to "where's",
        "whos" to "who's",
        "itll" to "it'll",
        "couldve" to "could've",
        "wouldve" to "would've",
        "shouldve" to "should've",
        "accomodate" to "accommodate",
        "occured" to "occurred",
        "tommorow" to "tomorrow",
        "tomorow" to "tomorrow",
        "thx" to "thanks",
        "plz" to "please",
        "wich" to "which",
        "tset" to "test",
        "thankyou" to "thank you",
        "goodmorning" to "good morning",
        "goodnight" to "good night",
        "alot" to "a lot",
        "goverment" to "government",
        "enviroment" to "environment",
        "necesary" to "necessary",
        "neccessary" to "necessary",
        "calender" to "calendar",
        "adress" to "address",
        "begining" to "beginning",
        "bussiness" to "business",
        "comming" to "coming",
        "completly" to "completely",
        "diferent" to "different",
        "freind" to "friend",
        "happend" to "happened",
        "immediatly" to "immediately",
        "independant" to "independent",
        "knowlege" to "knowledge",
        "occurence" to "occurrence",
        "persue" to "pursue",
        "posible" to "possible",
        "probly" to "probably",
        "realy" to "really",
        "reccomend" to "recommend",
        "recomend" to "recommend",
        "succesful" to "successful",
        "suprise" to "surprise",
        "truely" to "truly",
        "wierd" to "weird"
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
        "etait" to "était",
        "numero" to "numéro",
        "telephone" to "téléphone",
        "reponse" to "réponse",
        "reunion" to "réunion",
        "cafe" to "café",
        "idee" to "idée"
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
        "perdon" to "perdón",
        "telefono" to "teléfono",
        "direccion" to "dirección",
        "reunion" to "reunión"
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

    // Ordered by real-world conversational frequency so high-probability words win prefix & fuzzy ties.
    val germanWords = listOf(
        "ich", "und", "die", "der", "das", "ist", "nicht", "ein", "es", "du", "zu", "in",
        "wir", "auf", "mit", "sie", "für", "auch", "eine", "haben", "so", "wie", "aber",
        "noch", "mir", "mich", "wenn", "was", "dann", "an", "am", "im", "von",
        "kann", "kannst", "habe", "hast", "hat", "bin", "bist", "sind", "war", "waren", "wird", "werden", "dass", "bei", "nach",
        "oder", "nur", "schon", "hier", "mal", "ja", "nein", "danke", "bitte", "hallo",
        "gut", "gute", "guten", "heute", "morgen", "gestern", "jetzt", "gleich", "gerade",
        "immer", "wieder", "mehr", "sehr", "viel", "viele", "vielleicht", "eigentlich",
        "natürlich", "klar", "genau", "super", "perfekt", "okay", "alles", "alle",
        "machen", "macht", "gemacht", "gehen", "geht", "gegangen", "kommen", "kommt", "gekommen", "sehen", "sieht", "gesehen",
        "sagen", "sagt", "gesagt", "wissen", "weiß", "gewusst", "geben", "gibt", "gegeben", "nehmen", "nimmt", "genommen",
        "finden", "findet", "gefunden", "bleiben", "bleibt", "geblieben",
        "brauchen", "brauche", "braucht", "wollen", "will", "willst", "wollte", "können", "könnt", "konnte", "könnte", "könntest",
        "müssen", "muss", "musst", "musste", "müsste", "sollen", "soll", "sollst", "sollte", "sollten",
        "dürfen", "darf", "darfst", "durfte", "mochte", "möchte", "möchtest", "möchten", "würde", "würdest", "würden",
        "hätte", "hättest", "hätten", "wäre", "wärst", "wären",
        "dein", "deine", "deinen", "deinem", "deiner", "mein", "meine", "meinen", "meinem", "meiner",
        "sein", "seine", "seinen", "seinem", "ihr", "ihre", "ihren", "unser", "unsere", "unseren", "euer", "eure",
        "dieser", "diese", "dieses", "diesen", "diesem", "jeder", "jede", "jedes", "jeden", "jemand", "niemand",
        "etwas", "nichts", "einfach", "schnell", "schneller", "kurz", "lange", "länger", "später", "früher",
        "zusammen", "allein", "alleine", "fertig", "möglich", "wichtig", "wichtiger", "richtig", "falsch",
        "schön", "schöne", "schönen", "groß", "große", "größer", "klein", "kleine", "neu", "neue", "neuen", "alt", "alte", "letzte", "nächste", "nächsten",
        "über", "unter", "vor", "hinter", "zwischen", "durch", "gegen", "ohne", "wegen", "während", "seit", "bis", "beim", "zum", "zur", "vom", "ins", "ans", "aufs",
        "weil", "obwohl", "damit", "deshalb", "deswegen", "trotzdem", "übrigens", "außerdem", "allerdings", "jedoch", "sondern",
        "hoffentlich", "leider", "wahrscheinlich", "bestimmt", "wirklich", "total", "absolut", "definitiv", "sicher", "sicherlich",
        "gerne", "gern", "sofort", "zuerst", "danach", "vorbei", "zurück", "unterwegs", "dabei", "dafür", "dagegen", "damit", "darüber", "darauf", "daran", "davon",
        "warum", "wieso", "weshalb", "wann", "wo", "wohin", "woher", "wer", "wen", "wem", "welche", "welcher", "welches",
        "Zeit", "Tag", "Tage", "Jahr", "Jahre", "Woche", "Wochen", "Monat", "Monate", "Stunde", "Stunden", "Minute", "Minuten", "Sekunde", "Uhr", "Termin", "Termine",
        "Nachricht", "Nachrichten", "Text", "Bild", "Bilder", "Foto", "Fotos", "Video", "Handy", "Telefon", "Telefonnummer", "Handynummer", "Anruf", "Antwort", "Frage", "Fragen",
        "Problem", "Probleme", "Lösung", "Projekt", "Arbeit", "Büro", "Firma", "Team", "Chef", "Haus", "Wohnung", "Zimmer", "Küche", "Auto", "Fahrrad", "Bus", "Bahn", "Zug", "Weg",
        "Freund", "Freundin", "Freunde", "Familie", "Mutter", "Vater", "Bruder", "Schwester", "Kind", "Kinder", "Kollege", "Kollegin", "Kollegen", "Kunde", "Kunden", "Meeting",
        "Kaffee", "Tee", "Wasser", "Essen", "Frühstück", "Mittagessen", "Abendessen", "Restaurant", "Supermarkt", "Einkauf",
        "Idee", "Ideen", "Grund", "Gründe", "Beispiel", "Dokument", "Datei", "Ordner", "Link", "Seite", "App", "System",
        "Tastatur", "Sprache", "Einstellung", "Einstellungen", "Update", "Version", "Fehler", "Funktion", "Vorschlag", "Vorschläge",
        "Rechnung", "Vertrag", "Angebot", "Bestellung", "Lieferung", "Paket", "Preis", "Geld", "Euro", "Bank", "Konto", "Karte",
        "Adresse", "Anschrift", "Straße", "Postleitzahl", "Stadt", "Ort", "Land", "Name", "Vorname", "Nachname", "Nummer", "Passwort", "Profil", "E-Mail",
        "Datum", "Geburtsdatum", "Alter", "Kalender", "Wochenende", "Urlaub", "Ferien", "Reise", "Hotel", "Flug", "Flughafen", "Bahnhof", "Wetter", "Sonne", "Regen", "Schnee",
        "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag",
        "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember",
        "Morgen", "Vormittag", "Mittag", "Nachmittag", "Abend", "Nacht", "Grüße", "Gruß", "Liebe", "Viele", "Dank", "Hilfe", "Erfolg", "Glück", "Spaß", "Freude",
        "Ahnung", "Stress", "Sache", "Thema", "Punkt", "Liste", "Aufgabe", "Aufgaben", "Notiz", "Erinnerung", "Status", "Details", "Kontakt", "Information", "Informationen",
        "Entscheidung", "Möglichkeit", "Wahrscheinlichkeit", "Meinung", "Erfahrung", "Gefühl", "Gedanke", "Situation", "Moment", "Überraschung", "Besserung",
        "schicken", "schicke", "geschickt", "schreiben", "schreibe", "geschrieben", "lesen", "lese", "gelesen", "schauen", "schaue", "geschaut",
        "telefonieren", "anrufen", "rufe", "angerufen", "treffen", "treffe", "getroffen", "sprechen", "spreche", "gesprochen", "reden",
        "denken", "denke", "gedacht", "glauben", "glaube", "geglaubt", "hoffen", "hoffe", "gehofft", "freuen", "freue", "gefreut", "wünschen", "wünsche",
        "verstehen", "verstehe", "verstanden", "erklären", "helfen", "helfe", "geholfen", "arbeiten", "arbeite", "gearbeitet", "lernen", "lerne", "gelernt",
        "testen", "teste", "getestet", "läuft", "laufen", "fahren", "fahre", "gefahren", "kaufen", "kaufe", "gekauft", "bestellen", "bestellt",
        "bezahlen", "bezahlt", "bringen", "bringe", "gebracht", "holen", "hole", "geholt", "zeigen", "zeige", "gezeigt", "fragen", "frage", "gefragt",
        "antworten", "antworte", "geantwortet", "warten", "warte", "gewartet", "suchen", "suche", "gesucht", "prüfen", "prüfe", "geprüft",
        "ändern", "geändert", "löschen", "gelöscht", "speichern", "gespeichert", "öffnen", "geöffnet", "schließen", "geschlossen", "starten", "gestartet",
        "senden", "gesendet", "teilen", "geteilt", "nutzen", "nutze", "genutzt", "verwenden", "probieren", "versuchen", "versuche", "versucht",
        "vereinbaren", "vereinbart", "planen", "geplant", "erledigen", "erledigt", "klappen", "klappt", "geklappt", "passen", "passt", "gepasst",
        "stimmen", "stimmt", "gestimmt", "funktioniert", "funktionieren", "melde", "melden", "gemeldet", "bescheid", "Bescheid",
        "bisschen", "einmal", "zweimal", "manchmal", "selten", "häufig", "oft", "meistens", "normalerweise", "tatsächlich", "besonders", "genauso",
        "ebenfalls", "eben", "halt", "irgendwie", "irgendwann", "irgendwo", "überall", "nirgendwo", "draußen", "drinnen", "oben", "unten", "links", "rechts",
        "interessant", "spannend", "lustig", "nett", "freundlich", "herzlich", "pünktlich", "direkt", "lokal", "privat", "aktuell", "besser", "beste", "besten",
        "schlecht", "schwer", "leicht", "teuer", "günstig", "kostenlos", "frei", "voll", "leer", "warm", "kalt", "müde", "wach", "gesund", "krank",
        "geht's", "gibt's", "wird's", "hab's", "mach's", "wenn's", "weil's", "tschüss", "servus", "moin", "hi", "hey", "super", "cool", "klasse", "prima", "toll"
    )

    val englishWords = listOf(
        "the", "and", "to", "you", "I", "that", "it", "is", "in", "for", "of", "on",
        "with", "this", "we", "are", "be", "have", "not", "but", "what", "so", "can",
        "do", "if", "just", "my", "your", "me", "at", "from", "will", "about", "all",
        "they", "as", "or", "an", "would", "there", "like", "out", "up", "when", "know",
        "how", "get", "go", "time", "good", "great", "now", "here", "make", "see",
        "think", "also", "well", "want", "need", "back", "one", "could", "should",
        "hello", "hey", "hi", "thanks", "thank", "please", "sorry", "okay", "yes", "no",
        "today", "tomorrow", "yesterday", "morning", "afternoon", "evening", "night", "week", "weekend", "month",
        "year", "hour", "minute", "second", "meeting", "message", "text", "email", "phone", "number", "call",
        "work", "office", "company", "project", "team", "home", "house", "address", "street", "city", "country",
        "friend", "friends", "family", "coffee", "water", "lunch", "dinner", "breakfast", "restaurant",
        "really", "actually", "probably", "definitely", "maybe", "always", "never", "often",
        "sometimes", "usually", "already", "still", "soon", "later", "right", "sure", "certainly", "absolutely",
        "perfect", "awesome", "amazing", "nice", "happy", "glad", "helpful", "important", "possible", "available",
        "ready", "done", "finished", "check", "checked", "send", "sent", "share", "shared", "update", "updated",
        "review", "reply", "confirm", "confirmed", "schedule", "scheduled", "build", "create", "change", "improve",
        "keyboard", "voice", "language", "settings", "search", "browser", "website", "link", "file", "document",
        "because", "although", "however", "between", "around", "before", "after", "during", "through", "without", "together",
        "everyone", "everything", "someone", "something", "anything", "nothing", "anywhere", "everywhere",
        "don't", "can't", "won't", "didn't", "doesn't", "isn't", "aren't", "wasn't", "weren't", "haven't", "hasn't",
        "I'm", "I've", "I'll", "I'd", "you're", "you've", "you'll", "it's", "that's", "what's", "let's",
        "we're", "we've", "we'll", "they're", "they've", "they'll", "he's", "she's", "there's", "here's",
        "question", "questions", "answer", "problem", "issue", "solution", "idea", "ideas", "plan", "note", "notes",
        "list", "task", "tasks", "reminder", "calendar", "date", "birthday", "name", "first", "last", "age", "profile",
        "been", "being", "was", "were", "has", "had", "does", "did", "doing", "going", "coming", "looking", "working",
        "trying", "waiting", "talking", "asking", "telling", "saying", "getting", "making", "taking", "giving", "using",
        "find", "found", "keep", "kept", "leave", "left", "feel", "felt", "bring", "brought", "start", "started",
        "stop", "stopped", "open", "close", "save", "saved", "delete", "edit", "copy", "paste", "help", "try", "tried",
        "hear", "heard", "talk", "speak", "read", "write", "wrote", "written", "watch", "play", "run", "running",
        "drive", "walk", "travel", "flight", "hotel", "train", "car", "weather", "rain", "sun", "cold", "warm",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "best", "better", "worse", "most", "more", "less", "many", "much", "some", "any", "every", "other", "another",
        "same", "different", "new", "old", "big", "small", "long", "short", "fast", "quick", "quickly", "slow", "easy", "hard",
        "free", "busy", "late", "early", "next", "previous", "current", "local", "private", "secure", "smart",
        "sounds", "looks", "seems", "means", "works", "happened", "remember", "forget", "understand", "agree", "hope", "wish",
        "love", "miss", "appreciate", "welcome", "congrats", "cheers", "regards", "sincerely", "bye", "worries", "way", "lot"
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
        "beaucoup", "peu", "trop", "tout", "tous", "toute", "rien", "quelque", "chose",
        "numéro", "téléphone", "adresse", "prénom", "nom", "français"
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
        "pregunta", "respuesta", "idea", "teclado", "idioma", "ajustes", "aquí", "allí",
        "número", "teléfono", "dirección", "nombre", "apellido", "español"
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
        "gün", "hafta", "ay", "yıl", "saat", "iş", "ev", "mesaj", "kahve"
    )

    val emojiTriggers = mapOf(
        "danke" to "🙏", "thanks" to "🙏", "thank" to "🙏", "merci" to "🙏", "gracias" to "🙏",
        "grazie" to "🙏", "obrigado" to "🙏", "bedankt" to "🙏", "dziękuję" to "🙏", "teşekkürler" to "🙏",
        "herz" to "❤️", "liebe" to "❤️", "love" to "❤️", "amour" to "❤️", "amor" to "❤️",
        "amore" to "❤️", "liefde" to "❤️", "miłość" to "❤️", "aşk" to "❤️",
        "lachen" to "😂", "haha" to "😂", "lustig" to "😂", "funny" to "😂", "lol" to "😂",
        "feuer" to "🔥", "fire" to "🔥", "krass" to "🔥", "genial" to "🔥", "awesome" to "🔥",
        "ok" to "👍", "okay" to "👍", "top" to "👍", "gut" to "👍", "good" to "👍", "nice" to "👍", "cool" to "😎",
        "super" to "✨", "perfekt" to "✨", "perfect" to "✨", "parfait" to "✨", "perfecto" to "✨", "amazing" to "✨",
        "stern" to "⭐", "star" to "⭐",
        "kaffee" to "☕", "coffee" to "☕", "café" to "☕", "caffè" to "☕", "koffie" to "☕", "kawa" to "☕", "kahve" to "☕",
        "sonne" to "☀️", "sun" to "☀️", "soleil" to "☀️", "sol" to "☀️",
        "party" to "🎉", "glückwunsch" to "🎉", "congrats" to "🎉", "félicitations" to "🎉", "erfolg" to "🎉",
        "musik" to "🎵", "music" to "🎵", "musique" to "🎵", "música" to "🎵",
        "idee" to "💡", "idea" to "💡", "idée" to "💡",
        "zeit" to "⏰", "time" to "⏰", "uhr" to "⏰", "termin" to "📅", "meeting" to "📅",
        "geld" to "💶", "money" to "💵", "euro" to "💶",
        "hallo" to "👋", "hello" to "👋", "hey" to "👋", "bonjour" to "👋", "hola" to "👋", "ciao" to "👋", "merhaba" to "👋",
        "tschüss" to "👋", "bye" to "👋",
        "traurig" to "😢", "sad" to "😢", "sorry" to "🙏", "entschuldigung" to "🙏",
        "check" to "✅", "erledigt" to "✅", "done" to "✅", "fertig" to "✅",
        "geburtstag" to "🎂", "birthday" to "🎂", "geschenk" to "🎁", "gift" to "🎁",
        "urlaub" to "🏖️", "vacation" to "🏖️", "strand" to "🏖️",
        "flug" to "✈️", "flight" to "✈️", "reise" to "✈️", "travel" to "✈️",
        "pizza" to "🍕", "essen" to "🍽️", "hunger" to "😋", "lecker" to "😋",
        "bier" to "🍺", "beer" to "🍺", "wein" to "🍷", "wine" to "🍷", "prost" to "🍻",
        "regen" to "🌧️", "rain" to "🌧️", "schnee" to "❄️", "snow" to "❄️",
        "sport" to "🏃", "gym" to "💪", "stark" to "💪", "strong" to "💪",
        "auto" to "🚗", "car" to "🚗", "zug" to "🚆", "train" to "🚆", "fahrrad" to "🚲",
        "hund" to "🐶", "dog" to "🐶", "katze" to "🐱", "cat" to "🐱",
        "kuss" to "😘", "kiss" to "😘", "umarmung" to "🤗", "hug" to "🤗",
        "müde" to "😴", "tired" to "😴", "schlafen" to "😴", "sleep" to "😴",
        "wichtig" to "❗", "achtung" to "⚠️", "warnung" to "⚠️", "rakete" to "🚀", "rocket" to "🚀",
        "telefon" to "📞", "anruf" to "📞", "call" to "📞", "email" to "✉️", "paket" to "📦", "haus" to "🏠", "home" to "🏠"
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
        "er" to listOf("ist", "hat", "kann"),
        "sie" to listOf("ist", "hat", "sind"),
        "es" to listOf("ist", "wird", "gibt"),
        "wir" to listOf("sind", "haben", "können"),
        "ihr" to listOf("seid", "habt", "könnt"),
        "bin" to listOf("gleich", "schon", "noch"),
        "bist" to listOf("du", "schon", "noch"),
        "sind" to listOf("wir", "schon", "noch"),
        "ist" to listOf("das", "es", "mir"),
        "war" to listOf("echt", "sehr", "super"),
        "wird" to listOf("schon", "es", "das"),
        "gleich" to listOf("da", "wieder", "fertig"),
        "schon" to listOf("da", "unterwegs", "fertig"),
        "habe" to listOf("noch", "gerade", "Zeit"),
        "hast" to listOf("du", "schon", "Zeit"),
        "hat" to listOf("sich", "mir", "noch"),
        "haben" to listOf("wir", "noch", "Zeit"),
        "kann" to listOf("dir", "das", "noch"),
        "kannst" to listOf("du", "mir", "das"),
        "können" to listOf("wir", "gerne", "das"),
        "könnte" to listOf("ich", "man", "es"),
        "muss" to listOf("noch", "gleich", "mal"),
        "müssen" to listOf("wir", "noch", "mal"),
        "will" to listOf("ich", "noch", "nur"),
        "möchte" to listOf("gerne", "noch", "dir"),
        "würde" to listOf("ich", "gerne", "mich"),
        "hätte" to listOf("ich", "gerne", "noch"),
        "wäre" to listOf("super", "perfekt", "mir"),
        "danke" to listOf("dir", "für", "vielmals"),
        "vielen" to listOf("Dank", "Grüßen"),
        "bitte" to listOf("schön", "kurz", "noch"),
        "hallo" to listOf("zusammen", "wie", "ich"),
        "hey" to listOf("wie", "hast", "kannst"),
        "guten" to listOf("Morgen", "Tag", "Abend"),
        "gute" to listOf("Nacht", "Idee", "Besserung"),
        "liebe" to listOf("Grüße", "dich", "das"),
        "viele" to listOf("Grüße", "Dank", "Sachen"),
        "schöne" to listOf("Grüße", "Woche", "Zeit"),
        "schönen" to listOf("Tag", "Abend", "Sonntag"),
        "schönes" to listOf("Wochenende", "Wetter"),
        "bis" to listOf("gleich", "morgen", "später"),
        "alles" to listOf("klar", "Gute", "gut"),
        "morgen" to listOf("früh", "wieder", "um"),
        "heute" to listOf("Abend", "Nachmittag", "noch"),
        "gestern" to listOf("Abend", "war", "habe"),
        "jetzt" to listOf("gleich", "schon", "noch"),
        "gerade" to listOf("unterwegs", "noch", "am"),
        "wie" to listOf("geht", "spät", "lange"),
        "was" to listOf("machst", "ist", "gibt's"),
        "wo" to listOf("bist", "ist", "sind"),
        "wann" to listOf("hast", "kommst", "wollen"),
        "warum" to listOf("ist", "nicht", "hast"),
        "kein" to listOf("Problem", "Stress", "Ding"),
        "keine" to listOf("Ahnung", "Sorge", "Zeit"),
        "nicht" to listOf("mehr", "so", "ganz"),
        "noch" to listOf("nicht", "mal", "ein"),
        "sehr" to listOf("gut", "gerne", "schön"),
        "ganz" to listOf("gut", "genau", "kurz"),
        "echt" to listOf("gut", "super", "cool"),
        "total" to listOf("gut", "super", "lieb"),
        "einfach" to listOf("mal", "nur", "so"),
        "immer" to listOf("noch", "wieder", "gerne"),
        "wieder" to listOf("da", "gut", "zurück"),
        "und" to listOf("dann", "ich", "so"),
        "aber" to listOf("ich", "das", "nicht"),
        "oder" to listOf("so", "doch", "nicht"),
        "weil" to listOf("ich", "es", "das"),
        "dass" to listOf("ich", "du", "wir"),
        "wenn" to listOf("du", "ich", "wir"),
        "mit" to listOf("dir", "dem", "freundlichen"),
        "für" to listOf("dich", "die", "das"),
        "auf" to listOf("jeden", "dem", "dich"),
        "jeden" to listOf("Fall", "Tag"),
        "zum" to listOf("Beispiel", "Glück", "Geburtstag"),
        "zur" to listOf("Zeit", "Arbeit", "Info"),
        "im" to listOf("Moment", "Büro", "Anhang"),
        "am" to listOf("besten", "Wochenende", "Montag"),
        "nach" to listOf("Hause", "dem", "der"),
        "zu" to listOf("Hause", "dir", "tun"),
        "von" to listOf("mir", "dir", "dem"),
        "über" to listOf("das", "die", "den"),
        "freue" to listOf("mich", "ich"),
        "melde" to listOf("mich", "dich"),
        "sag" to listOf("Bescheid", "mir", "mal"),
        "gib" to listOf("mir", "Bescheid", "mal"),
        "schick" to listOf("mir", "es", "dir"),
        "schicke" to listOf("dir", "es", "gleich"),
        "komme" to listOf("gleich", "später", "morgen"),
        "fahre" to listOf("gleich", "jetzt", "morgen"),
        "gehe" to listOf("gleich", "jetzt", "noch"),
        "brauche" to listOf("noch", "kurz", "Hilfe"),
        "glaube" to listOf("schon", "nicht", "das"),
        "denke" to listOf("schon", "ich", "mal"),
        "hoffe" to listOf("es", "dir", "das"),
        "klingt" to listOf("gut", "super", "perfekt"),
        "passt" to listOf("mir", "super", "perfekt"),
        "klappt" to listOf("super", "perfekt", "leider"),
        "stimmt" to listOf("auch", "genau", "schon"),
        "leider" to listOf("nicht", "keine", "schon"),
        "vielleicht" to listOf("morgen", "später", "können"),
        "natürlich" to listOf("gerne", "nicht", "auch"),
        "übrigens" to listOf("habe", "ist", "noch"),
        "bescheid" to listOf("wenn", "ob", "geben")
    )

    private val englishNextWords = mapOf(
        "i" to listOf("am", "have", "can"),
        "you" to listOf("are", "can", "have"),
        "we" to listOf("are", "can", "will"),
        "they" to listOf("are", "have", "will"),
        "he" to listOf("is", "was", "has"),
        "she" to listOf("is", "was", "has"),
        "it" to listOf("is", "was", "will"),
        "am" to listOf("on", "going", "still"),
        "are" to listOf("you", "we", "still"),
        "is" to listOf("it", "that", "there"),
        "was" to listOf("just", "great", "about"),
        "will" to listOf("be", "do", "send"),
        "would" to listOf("be", "like", "love"),
        "could" to listOf("you", "be", "we"),
        "should" to listOf("be", "we", "have"),
        "on" to listOf("my", "the", "your"),
        "in" to listOf("the", "a", "touch"),
        "at" to listOf("the", "home", "work"),
        "for" to listOf("the", "your", "me"),
        "to" to listOf("be", "do", "see"),
        "of" to listOf("the", "course", "this"),
        "with" to listOf("you", "the", "me"),
        "from" to listOf("the", "me", "my"),
        "about" to listOf("the", "it", "that"),
        "my" to listOf("way", "phone", "own"),
        "your" to listOf("time", "help", "email"),
        "going" to listOf("to", "home", "out"),
        "looking" to listOf("forward", "for", "at"),
        "waiting" to listOf("for", "on"),
        "working" to listOf("on", "from", "with"),
        "trying" to listOf("to", "get", "find"),
        "thank" to listOf("you", "goodness"),
        "thanks" to listOf("for", "again", "so"),
        "see" to listOf("you", "the", "if"),
        "talk" to listOf("to", "soon", "later"),
        "let" to listOf("me", "us", "you"),
        "good" to listOf("morning", "night", "luck"),
        "great" to listOf("idea", "thanks", "work"),
        "happy" to listOf("to", "birthday", "help"),
        "sorry" to listOf("for", "about", "to"),
        "please" to listOf("let", "send", "check"),
        "have" to listOf("a", "to", "been"),
        "has" to listOf("been", "to", "a"),
        "can" to listOf("you", "we", "I"),
        "do" to listOf("you", "it", "that"),
        "did" to listOf("you", "not", "it"),
        "don't" to listOf("know", "worry", "think"),
        "can't" to listOf("wait", "make", "believe"),
        "won't" to listOf("be", "have", "work"),
        "i'm" to listOf("on", "going", "not"),
        "i'll" to listOf("be", "send", "check"),
        "i've" to listOf("been", "got", "just"),
        "it's" to listOf("a", "not", "been"),
        "that's" to listOf("great", "awesome", "fine"),
        "what's" to listOf("up", "going", "your"),
        "let's" to listOf("do", "meet", "go"),
        "how" to listOf("are", "is", "about"),
        "what" to listOf("do", "is", "time"),
        "when" to listOf("are", "do", "can"),
        "where" to listOf("are", "is", "do"),
        "why" to listOf("not", "is", "did"),
        "no" to listOf("problem", "worries", "way"),
        "not" to listOf("sure", "yet", "at"),
        "sounds" to listOf("good", "great", "like"),
        "makes" to listOf("sense", "it", "me"),
        "works" to listOf("for", "great", "well"),
        "just" to listOf("a", "wanted", "got"),
        "really" to listOf("good", "appreciate", "like"),
        "very" to listOf("much", "good", "nice"),
        "so" to listOf("much", "good", "far"),
        "too" to listOf("much", "late", "bad"),
        "the" to listOf("same", "next", "first"),
        "and" to listOf("then", "the", "I"),
        "but" to listOf("I", "it", "we"),
        "if" to listOf("you", "we", "it"),
        "this" to listOf("is", "week", "morning"),
        "next" to listOf("week", "time", "month"),
        "last" to listOf("week", "night", "time"),
        "best" to listOf("regards", "wishes", "way"),
        "kind" to listOf("regards", "of")
    )

    private val nextWordPhrases = mapOf(
        "de" to mapOf(
            "ich bin" to listOf("gleich", "unterwegs", "fertig"),
            "ich habe" to listOf("verstanden", "noch", "Zeit"),
            "wir haben" to listOf("vereinbart", "noch", "Zeit"),
            "wir sind" to listOf("gleich", "unterwegs", "fertig"),
            "ich kann" to listOf("dir", "morgen", "gerne"),
            "kannst du" to listOf("mir", "bitte", "kurz"),
            "können wir" to listOf("gerne", "morgen", "kurz"),
            "hast du" to listOf("Zeit", "schon", "Lust"),
            "bist du" to listOf("schon", "noch", "gleich"),
            "ich melde" to listOf("mich"),
            "vielen dank" to listOf("für", "nochmal", "im"),
            "dank im" to listOf("Voraus"),
            "ich freue" to listOf("mich"),
            "freue mich" to listOf("auf", "schon", "sehr"),
            "bis morgen" to listOf("früh", "dann"),
            "bis gleich" to listOf("dann"),
            "ich komme" to listOf("gleich", "später", "morgen"),
            "ich würde" to listOf("gerne", "vorschlagen", "das"),
            "ich möchte" to listOf("gerne", "dich", "noch"),
            "ich muss" to listOf("noch", "gleich", "leider"),
            "ich brauche" to listOf("noch", "kurz", "Hilfe"),
            "ich glaube" to listOf("schon", "nicht", "das"),
            "ich denke" to listOf("schon", "das", "wir"),
            "ich hoffe" to listOf("dir", "es", "das"),
            "wir sehen" to listOf("uns", "dann"),
            "sehen uns" to listOf("gleich", "morgen", "später"),
            "ich wünsche" to listOf("dir", "euch", "Ihnen"),
            "einen schönen" to listOf("Tag", "Abend", "Sonntag"),
            "ein schönes" to listOf("Wochenende"),
            "im anhang" to listOf("findest", "finden", "ist"),
            "danke für" to listOf("deine", "Ihre", "die"),
            "wie geht" to listOf("es", "das"),
            "wie geht's" to listOf("dir", "euch"),
            "geht es" to listOf("dir", "euch", "Ihnen"),
            "mit freundlichen" to listOf("Grüßen"),
            "keine sorge" to listOf("ich", "das"),
            "kein problem" to listOf("gerne", "ich"),
            "alles gute" to listOf("zum", "für"),
            "gute zum" to listOf("Geburtstag"),
            "alles klar" to listOf("danke", "dann", "bis"),
            "auf jeden" to listOf("Fall"),
            "jeden fall" to listOf("danke", "gerne", "machen"),
            "tut mir" to listOf("leid"),
            "es tut" to listOf("mir"),
            "klingt super" to listOf("bis", "danke"),
            "klingt gut" to listOf("bis", "danke", "machen"),
            "passt mir" to listOf("gut", "super", "perfekt"),
            "sag mir" to listOf("Bescheid", "gerne", "einfach"),
            "gib mir" to listOf("Bescheid", "kurz"),
            "schick mir" to listOf("gerne", "bitte", "kurz"),
            "ich schicke" to listOf("dir", "es", "gleich"),
            "war nett" to listOf("mit", "dich"),
            "zu hause" to listOf("bin", "und", "an"),
            "nach hause" to listOf("und", "fahren", "gehen")
        ),
        "en" to mapOf(
            "i am" to listOf("ready", "on", "going"),
            "on my" to listOf("way", "phone", "own"),
            "my way" to listOf("now", "home", "there"),
            "i have" to listOf("a", "to", "been"),
            "we have" to listOf("time", "to", "been"),
            "let me" to listOf("know", "check", "see"),
            "me know" to listOf("if", "when", "what"),
            "can you" to listOf("please", "send", "help"),
            "could you" to listOf("please", "send", "check"),
            "thank you" to listOf("for", "so", "again"),
            "you so" to listOf("much"),
            "thanks for" to listOf("your", "the", "letting"),
            "see you" to listOf("soon", "tomorrow", "later"),
            "i will" to listOf("be", "send", "check"),
            "looking forward" to listOf("to"),
            "forward to" to listOf("it", "seeing", "hearing"),
            "i would" to listOf("like", "love", "suggest"),
            "would like" to listOf("to"),
            "please let" to listOf("me", "us"),
            "how are" to listOf("you", "things"),
            "are you" to listOf("doing", "free", "available"),
            "sounds good" to listOf("to", "thanks", "see"),
            "talk to" to listOf("you"),
            "to you" to listOf("soon", "later", "tomorrow"),
            "works for" to listOf("me"),
            "have a" to listOf("great", "good", "nice"),
            "a great" to listOf("day", "weekend", "time"),
            "a good" to listOf("day", "one", "idea"),
            "no worries" to listOf("at", "thanks"),
            "no problem" to listOf("at", "happy"),
            "at all" to listOf("thanks"),
            "i don't" to listOf("know", "think", "mind"),
            "don't know" to listOf("if", "yet", "what"),
            "don't worry" to listOf("about", "it's"),
            "just wanted" to listOf("to"),
            "wanted to" to listOf("check", "let", "ask"),
            "be right" to listOf("back", "there"),
            "in a" to listOf("minute", "few", "bit"),
            "by the" to listOf("way"),
            "as soon" to listOf("as"),
            "soon as" to listOf("possible", "I")
        ),
        "fr" to mapOf(
            "je suis" to listOf("en", "prêt", "désolé"),
            "merci pour" to listOf("votre", "ton", "tout"),
            "à tout" to listOf("à", "de"),
            "pas de" to listOf("problème", "souci")
        ),
        "es" to mapOf(
            "muchas gracias" to listOf("por", "de"),
            "yo estoy" to listOf("bien", "en", "listo"),
            "buenos días" to listOf("cómo", "a"),
            "hasta luego" to listOf("nos")
        ),
        "it" to mapOf(
            "grazie per" to listOf("il", "la", "tutto"),
            "ci vediamo" to listOf("domani", "presto", "dopo"),
            "come stai" to listOf("oggi", "tutto")
        ),
        "pt" to mapOf(
            "até logo" to listOf("então"),
            "eu estou" to listOf("bem", "em", "pronto"),
            "muito obrigado" to listOf("por", "pela")
        ),
        "nl" to mapOf(
            "ik ben" to listOf("er", "onderweg", "klaar"),
            "dank je" to listOf("wel", "voor"),
            "tot snel" to listOf("dan")
        ),
        "pl" to mapOf(
            "dziękuję za" to listOf("pomoc", "wiadomość", "wszystko"),
            "do zobaczenia" to listOf("jutro", "wkrótce")
        ),
        "tr" to mapOf(
            "teşekkür ederim" to listOf("yardımınız", "her"),
            "iyi ki" to listOf("varsın", "geldin")
        )
    )

    fun commonNextWords(locale: String, previousWord: String, previousTwoWord: String = ""): List<String> {
        val language = KeyboardLocales.baseLang(locale)
        val word = previousWord.lowercase()
        if (previousTwoWord.isNotEmpty()) {
            val phrase = "${previousTwoWord.lowercase()} $word"
            nextWordPhrases[language]?.get(phrase)?.let { return it }
        }
        return when (language) {
            "de" -> germanNextWords[word]
            "en" -> englishNextWords[word]
            else -> null
        }.orEmpty()
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

    /**
     * Pre-computed O(1) lookup index for a language's built-in vocabulary:
     * - 1-char & 2-char prefix buckets (instant completions)
     * - lowercase -> canonical casing & frequency rank
     * - diacritic-folded map (instant umlaut/accent restoration across all 9 languages)
     * - SymSpell 1-delete hash table (O(1) fuzzy typo candidate lookup)
     * - (firstChar, lastChar) swipe buckets (30x faster glide decoding)
     */
    class VocabIndex(
        val words: List<String>,
        val lowerToCanonical: Map<String, String>,
        val frequencyRank: Map<String, Int>,
        val prefix1: Map<Char, List<String>>,
        val prefix2: Map<String, List<String>>,
        val foldedExact: Map<String, List<String>>,
        val foldedPrefix2: Map<String, List<String>>,
        val symSpell1Deletes: Map<String, List<String>>,
        val swipeBuckets: Map<Int, List<String>>
    ) {
        fun prefixMatches(lowerPrefix: String, limit: Int = 6): List<String> {
            if (lowerPrefix.isEmpty()) return emptyList()
            val bucket = if (lowerPrefix.length == 1) {
                prefix1[lowerPrefix[0]]
            } else {
                prefix2[lowerPrefix.substring(0, 2)]
            } ?: return emptyList()
            val out = ArrayList<String>(limit)
            for (word in bucket) {
                if (word.length > lowerPrefix.length && word.startsWith(lowerPrefix, ignoreCase = true)) {
                    out += word
                    if (out.size >= limit) break
                }
            }
            return out
        }

        fun symSpellCandidates(lowerQuery: String): Set<String> {
            val out = LinkedHashSet<String>(32)
            symSpell1Deletes[lowerQuery]?.let { out.addAll(it) }
            if (lowerQuery.length >= 3) {
                val sb = StringBuilder(lowerQuery.length - 1)
                for (i in lowerQuery.indices) {
                    sb.setLength(0)
                    sb.append(lowerQuery, 0, i)
                    sb.append(lowerQuery, i + 1, lowerQuery.length)
                    val del = sb.toString()
                    lowerToCanonical[del]?.let { out += it }
                    symSpell1Deletes[del]?.let { out.addAll(it) }
                }
            }
            return out
        }
    }

    private val vocabIndexCache = ConcurrentHashMap<String, VocabIndex>()

    fun indexFor(locale: String): VocabIndex {
        val base = KeyboardLocales.baseLang(locale)
        return vocabIndexCache.getOrPut(base) { buildVocabIndex(base, vocabularyFor(base)) }
    }

    fun foldDiacritics(raw: String, isGerman: Boolean = false): String {
        val sb = StringBuilder(raw.length + 2)
        for (i in raw.indices) {
            val ch = raw[i].lowercaseChar()
            when (ch) {
                'ä' -> sb.append('a')
                'ö' -> sb.append('o')
                'ü' -> sb.append('u')
                'ß' -> sb.append("ss")
                'á', 'à', 'â', 'ã', 'ą' -> sb.append('a')
                'é', 'è', 'ê', 'ë', 'ę' -> sb.append('e')
                'í', 'ì', 'î', 'ï', 'ı' -> sb.append('i')
                'ó', 'ò', 'ô', 'õ' -> sb.append('o')
                'ú', 'ù', 'û' -> sb.append('u')
                'ç', 'ć' -> sb.append('c')
                'ñ', 'ń' -> sb.append('n')
                'ś', 'ş', 'š' -> sb.append('s')
                'ğ' -> sb.append('g')
                'ł' -> sb.append('l')
                'ż', 'ź' -> sb.append('z')
                else -> sb.append(ch)
            }
        }
        val folded = sb.toString()
        if (!isGerman) return folded
        return folded
            .replace("ae", "a")
            .replace("oe", "o")
            .replace("ue", "u")
    }

    fun hasSpecialDiacritic(s: String): Boolean {
        for (i in s.indices) {
            if (s[i].code > 127) return true
        }
        return false
    }

    private fun buildVocabIndex(baseLang: String, rawWords: List<String>): VocabIndex {
        val isGerman = baseLang == "de"
        val lowerToCanonical = HashMap<String, String>(rawWords.size * 2)
        val frequencyRank = HashMap<String, Int>(rawWords.size * 2)
        val prefix1 = HashMap<Char, ArrayList<String>>(32)
        val prefix2 = HashMap<String, ArrayList<String>>(256)
        val foldedExact = HashMap<String, ArrayList<String>>(128)
        val foldedPrefix2 = HashMap<String, ArrayList<String>>(128)
        val symSpell1Deletes = HashMap<String, ArrayList<String>>(rawWords.size * 4)
        val swipeBuckets = HashMap<Int, ArrayList<String>>(256)

        val uniqueWords = ArrayList<String>(rawWords.size)
        rawWords.forEachIndexed { idx, word ->
            val clean = word.trim()
            if (clean.isEmpty()) return@forEachIndexed
            val lower = clean.lowercase()
            if (lowerToCanonical.putIfAbsent(lower, clean) != null) return@forEachIndexed
            uniqueWords += clean
            frequencyRank[lower] = idx

            prefix1.getOrPut(lower[0]) { ArrayList(16) }.add(clean)
            if (lower.length >= 2) {
                prefix2.getOrPut(lower.substring(0, 2)) { ArrayList(8) }.add(clean)
                val firstBase = foldCharBase(lower[0])
                val lastBase = foldCharBase(lower[lower.length - 1])
                val bucketKey = (firstBase.code shl 16) or lastBase.code
                swipeBuckets.getOrPut(bucketKey) { ArrayList(8) }.add(clean)
            }

            if (hasSpecialDiacritic(lower)) {
                val folded = foldDiacritics(lower, isGerman)
                foldedExact.getOrPut(folded) { ArrayList(2) }.add(clean)
                if (folded.length >= 2) {
                    foldedPrefix2.getOrPut(folded.substring(0, 2)) { ArrayList(4) }.add(clean)
                }
            }

            if (lower.length in 3..20) {
                val sb = StringBuilder(lower.length - 1)
                for (i in lower.indices) {
                    sb.setLength(0)
                    sb.append(lower, 0, i)
                    sb.append(lower, i + 1, lower.length)
                    val del = sb.toString()
                    val list = symSpell1Deletes.getOrPut(del) { ArrayList(2) }
                    if (list.size < 8) list += clean
                }
            }
        }

        return VocabIndex(
            words = uniqueWords,
            lowerToCanonical = lowerToCanonical,
            frequencyRank = frequencyRank,
            prefix1 = prefix1,
            prefix2 = prefix2,
            foldedExact = foldedExact,
            foldedPrefix2 = foldedPrefix2,
            symSpell1Deletes = symSpell1Deletes,
            swipeBuckets = swipeBuckets
        )
    }

    internal fun foldCharBase(ch: Char): Char = when (ch.lowercaseChar()) {
        'ä', 'á', 'à', 'â', 'ã', 'ą' -> 'a'
        'ö', 'ó', 'ò', 'ô', 'õ' -> 'o'
        'ü', 'ú', 'ù', 'û' -> 'u'
        'é', 'è', 'ê', 'ë', 'ę' -> 'e'
        'í', 'ì', 'î', 'ï', 'ı' -> 'i'
        'ß', 'ś', 'ş', 'š' -> 's'
        'ç', 'ć' -> 'c'
        'ñ', 'ń' -> 'n'
        'ğ' -> 'g'
        'ł' -> 'l'
        'ż', 'ź' -> 'z'
        else -> ch.lowercaseChar()
    }

    fun containsStandardWord(word: String, locale: String): Boolean {
        val lower = word.trim().lowercase()
        if (lower.isEmpty()) return false
        val base = KeyboardLocales.baseLang(locale)
        if (indexFor(base).lowerToCanonical.containsKey(lower)) return true
        if (base != "en" && indexFor("en").lowerToCanonical.containsKey(lower)) return true
        return false
    }

    fun isNeverAutoCapitalizeMidSentence(word: String, locale: String): Boolean {
        val base = KeyboardLocales.baseLang(locale)
        val lower = word.lowercase()
        return if (base == "de") lower in germanNeverAutoCapMidSentence else false
    }
}
