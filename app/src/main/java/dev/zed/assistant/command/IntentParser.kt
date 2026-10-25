package dev.zed.assistant.command

import dev.zed.assistant.command.ParsedCommand.Kind

/**
 * Offline, deterministic command parser for English, Hindi and Hinglish.
 *
 * Pure Kotlin on purpose: no Android imports, so it is fully unit tested. Everything
 * it cannot understand becomes UNKNOWN and is handed to Gemini (when a key is set).
 */
object IntentParser {

    fun parse(rawInput: String): ParsedCommand {
        val text = normalize(rawInput)
        if (text.isBlank()) return ParsedCommand(Kind.UNKNOWN, rawInput)
        val raw = rawInput

        // ---------------------------------------------------------- wake chat
        if (text == "help" || text.contains("kya kar sakte ho") || text.contains("what can you do")) {
            return ParsedCommand(Kind.HELP, raw)
        }

        // ------------------------------------------------------ WhatsApp first
        parseWhatsApp(text, raw)?.let { return it }

        // ------------------------------------------------------------ siren
        if (text.contains("siren") || text.contains("सायरन") ||
            text.contains("chor") || text.contains("चोर")
        ) {
            if (hasOff(text)) return ParsedCommand(Kind.SIREN_OFF, raw)
            if (hasOn(text) || text.contains("bajao") || text.contains("बजाओ")) {
                return ParsedCommand(Kind.SIREN_ON, raw)
            }
        }

        // ------------------------------------------------------ anti-theft
        if (text.contains("guard") || text.contains("anti theft") || text.contains("antitheft") ||
            text.contains("चोरी") || text.contains("security mode")
        ) {
            return if (hasOff(text)) ParsedCommand(Kind.DISARM_GUARD, raw)
            else ParsedCommand(Kind.ARM_GUARD, raw)
        }

        // ------------------------------------------------------- screenshots
        if (text.contains("screenshot") || text.contains("स्क्रीनशॉट") ||
            text.contains("screen shot")
        ) {
            return ParsedCommand(Kind.SCREENSHOT, raw)
        }

        // ------------------------------------------------------------- torch
        if (text.contains("torch") || text.contains("flashlight") || text.contains("टॉर्च") ||
            text.contains("टार्च") || text.contains("फ्लैशलाइट")
        ) {
            return when {
                hasOff(text) -> ParsedCommand(Kind.TORCH_OFF, raw)
                hasOn(text) -> ParsedCommand(Kind.TORCH_ON, raw)
                else -> ParsedCommand(Kind.TORCH_TOGGLE, raw)
            }
        }

        // -------------------------------------------------------------- wifi
        if (text.contains("wifi") || text.contains("wi fi") || text.contains("वाईफाई") ||
            text.contains("वाई-फाई")
        ) {
            return if (hasOff(text)) ParsedCommand(Kind.WIFI_OFF, raw)
            else ParsedCommand(Kind.WIFI_ON, raw)
        }

        // ---------------------------------------------------------- bluetooth
        if (text.contains("bluetooth") || text.contains("ब्लूटूथ") || text.contains("ब्लुटूथ")) {
            return if (hasOff(text)) ParsedCommand(Kind.BT_OFF, raw)
            else ParsedCommand(Kind.BT_ON, raw)
        }

        // ------------------------------------------------------------- mute
        if (text.contains("unmute") || text.contains("un mute")) {
            return ParsedCommand(Kind.UNMUTE, raw)
        }
        if (text.contains("mute") || text.contains("silent") || text.contains("साइलेंट")) {
            return ParsedCommand(Kind.MUTE, raw)
        }

        // ----------------------------------------------------------- volume
        if (text.contains("volume") || text.contains("आवाज़") || text.contains("आवाज") ||
            text.contains("awaz") || text.contains("awaaz") ||
            (text.contains("sound") && (text.contains("up") || text.contains("down")))
        ) {
            val level = firstNumber(text)
            return when {
                level != null -> ParsedCommand(Kind.VOLUME_SET, raw, number = level)
                text.contains("up") || text.contains("badha") || text.contains("बढ़ा") ||
                    text.contains("tez") || text.contains("तेज") ->
                    ParsedCommand(Kind.VOLUME_UP, raw)
                else -> ParsedCommand(Kind.VOLUME_DOWN, raw)
            }
        }

        // ------------------------------------------------------- brightness
        if (text.contains("brightness") || text.contains("चमक") || text.contains("bright")) {
            val level = firstNumber(text)
            return when {
                text.contains("auto") -> ParsedCommand(Kind.BRIGHTNESS_AUTO, raw)
                level != null -> ParsedCommand(Kind.BRIGHTNESS_SET, raw, number = level)
                text.contains("up") || text.contains("badha") || text.contains("बढ़ा") ->
                    ParsedCommand(Kind.BRIGHTNESS_UP, raw)
                else -> ParsedCommand(Kind.BRIGHTNESS_DOWN, raw)
            }
        }

        // ------------------------------------------------------------ battery
        if (text.contains("battery") || text.contains("बैटरी") || text.contains("charge")) {
            return ParsedCommand(Kind.BATTERY, raw)
        }

        // ------------------------------------------------------------- alarm
        if (text.contains("alarm") || text.contains("अलार्म")) {
            if (text.contains("show") || text.contains("dikhao") || text.contains("दिखाओ")) {
                return ParsedCommand(Kind.ALARM_SHOW, raw)
            }
            val time = parseTime(text)
            if (time != null) {
                return ParsedCommand(Kind.ALARM_SET, raw, hour = time.first, minute = time.second)
            }
            return ParsedCommand(Kind.ALARM_SHOW, raw)
        }

        // ------------------------------------------------------------- timer
        if (text.contains("timer") || text.contains("टाइमर")) {
            val seconds = parseDurationSeconds(text)
            if (seconds != null) return ParsedCommand(Kind.TIMER_SET, raw, number = seconds)
        }

        // --------------------------------------------------- read / translate
        if (text.contains("translate") || text.contains("अनुवाद") || text.contains("anuvad")) {
            val toEnglish = text.contains("english") || text.contains("अंग्रेजी")
            return ParsedCommand(
                Kind.TRANSLATE_SCREEN,
                raw,
                target = if (toEnglish) "en" else "hi"
            )
        }

        if ((text.contains("notification") || text.contains("unread") ||
                text.contains("अनदेखे") || text.contains("notifications")) &&
            (text.contains("read") || text.contains("padho") || text.contains("पढ़ो") ||
                text.contains("sunao") || text.contains("सुनाओ"))
        ) {
            return ParsedCommand(Kind.READ_NOTIFICATIONS, raw)
        }

        if (text.contains("read screen") || text.contains("screen padho") ||
            text.contains("padh kar sunao") || text.contains("screen pe kya") ||
            text.contains("what is on my screen") || text.contains("what's on my screen") ||
            text.contains("स्क्रीन पढ़ो")
        ) {
            return ParsedCommand(Kind.READ_SCREEN, raw)
        }

        // ------------------------------------------------------------- scroll
        if (text.contains("scroll") || text.contains("स्क्रॉल")) {
            return if (text.contains("up") || text.contains("upar") || text.contains("ऊपर")) {
                ParsedCommand(Kind.SCROLL_UP, raw)
            } else {
                ParsedCommand(Kind.SCROLL_DOWN, raw)
            }
        }

        if (text.contains("auto scroll") || text.contains("reels chalao") ||
            text.contains("reel chalao") || text.contains("reels play")
        ) {
            return if (hasOff(text)) ParsedCommand(Kind.AUTO_SCROLL_STOP, raw)
            else ParsedCommand(Kind.AUTO_SCROLL_START, raw)
        }

        // ---------------------------------------------------------- tap/click
        if (text.contains("tap ") || text.contains("click ") || text.contains("press ") ||
            text.contains("daba") || text.contains("दबा") || text.contains("par tap")
        ) {
            val target = extractAfter(
                text,
                listOf("tap on ", "tap ", "click on ", "click ", "press ", "dabao ", "daba ")
            )?.trim(',', '.', ' ')
            if (!target.isNullOrBlank()) {
                return ParsedCommand(Kind.TAP_TEXT, raw, target = cleanTarget(target))
            }
        }

        // -------------------------------------------------------- navigation
        if (text == "back" || text.contains("go back") || text.contains("peeche") ||
            text.contains("पीछे") || text.contains("wapas jao")
        ) {
            return ParsedCommand(Kind.BACK, raw)
        }
        if (text == "home" || text.contains("go home") || text.contains("home jao") ||
            text.contains("होम") || text.contains("home screen")
        ) {
            return ParsedCommand(Kind.HOME, raw)
        }
        if (text.contains("recent") || text.contains("recents") || text.contains("recent apps")) {
            return ParsedCommand(Kind.RECENTS, raw)
        }
        if (text.contains("unlock")) {
            return ParsedCommand(Kind.UNKNOWN, raw)
        }
        if (text.contains("lock") || text.contains("लॉक")) {
            return ParsedCommand(Kind.LOCK_SCREEN, raw)
        }
        if (text.contains("quick setting") || text.contains("control panel") ||
            text.contains("notification shade") || text.contains("quick panel")
        ) {
            return ParsedCommand(Kind.QUICK_SETTINGS, raw)
        }
        if (text.contains("notification panel") || text.contains("shade kholo")) {
            return ParsedCommand(Kind.NOTIFICATIONS_PANEL, raw)
        }
        if (text.contains("power menu") || text.contains("shutdown menu") ||
            text.contains("power dialog")
        ) {
            return ParsedCommand(Kind.POWER_MENU, raw)
        }

        // ------------------------------------------------------------- media
        if (text.contains("pause") || text.contains("रोको") || text.contains("band karo gana")) {
            return ParsedCommand(Kind.MEDIA_TOGGLE, raw)
        }
        if (text.contains("next song") || text.contains("next track") || text.contains("agla gana") ||
            text.contains("अगला गाना")
        ) {
            return ParsedCommand(Kind.MEDIA_NEXT, raw)
        }
        if (text.contains("previous song") || text.contains("prev song") ||
            text.contains("pichla gana") || text.contains("पिछला गाना")
        ) {
            return ParsedCommand(Kind.MEDIA_PREV, raw)
        }
        if (text.contains("play music") || text.contains("play song") || text.contains("gana chalao") ||
            text.contains("गाना चलाओ")
        ) {
            return ParsedCommand(Kind.MEDIA_TOGGLE, raw)
        }

        // -------------------------------------------------------------- call
        if (text.contains("call ") || text.startsWith("call") || text.contains("dial") ||
            text.contains("phone karo") || text.contains("फोन करो") || text.contains("milao")) {
            val target = extractAfter(
                text,
                listOf("call ", "dial ", "call to ", "phone karo ", "milao ")
            )?.let { cleanTarget(it) }
            if (!target.isNullOrBlank()) {
                val digits = target.filter { it.isDigit() }
                return if (digits.length >= 6 && digits.length >= target.count { it.isDigit() }) {
                    ParsedCommand(Kind.CALL_NUMBER, raw, target = digits)
                } else {
                    ParsedCommand(Kind.CALL_CONTACT, raw, target = target)
                }
            }
        }

        // --------------------------------------------------------- open app
        val appName = extractAppName(text)
        if (appName != null) {
            return ParsedCommand(Kind.OPEN_APP, raw, target = appName)
        }

        // ------------------------------------------------------------ search
        if (text.startsWith("search ") || text.contains("google karo") || text.contains("search karo")) {
            val query = extractAfter(text, listOf("search ", "google karo ", "search karo "))
            if (!query.isNullOrBlank()) {
                return ParsedCommand(Kind.WEB_SEARCH, raw, target = query.trim())
            }
        }

        if (text.startsWith("open ") && text.contains(".")) {
            return ParsedCommand(Kind.OPEN_URL, raw, target = text.removePrefix("open ").trim())
        }

        // ------------------------------------------------------------- clock
        if (text.contains("time") || text.contains("kitne baje") || text.contains("kitna baja") ||
            text.contains("समय") || text.contains("कितने बजे")
        ) {
            return ParsedCommand(Kind.TIME, raw)
        }
        if (text.contains("date") || text.contains("tarikh") || text.contains("तारीख") ||
            text.contains("aaj kaunsa din")
        ) {
            return ParsedCommand(Kind.DATE, raw)
        }

        return ParsedCommand(Kind.UNKNOWN, raw)
    }

    // ------------------------------------------------------------- helpers

    private fun parseWhatsApp(text: String, raw: String): ParsedCommand? {
        val mentionsWhatsApp = text.contains("whatsapp") || text.contains("व्हाट्सएप") ||
            text.contains("whats app") || text.contains("wa par") || text.contains("wa pe")
        if (!mentionsWhatsApp) return null

        if (text.contains("search") || text.contains("khojo") || text.contains("ढूंढो")) {
            val query = extractAfter(text, listOf("search ", "khojo ", "ढूंढो ")) ?: ""
            return ParsedCommand(Kind.WHATSAPP_SEARCH, raw, target = cleanTarget(query))
        }

        val video = text.contains("video call") || text.contains("video")
        if (text.contains("call") || text.contains("कॉल") || text.contains("milao")) {
            val target = extractWhatsAppTarget(text)
            return ParsedCommand(
                if (video) Kind.WHATSAPP_VIDEO_CALL else Kind.WHATSAPP_CALL,
                raw,
                target = cleanTarget(target)
            )
        }

        if (text.contains("voice note") || text.contains("voice message") ||
            text.contains("voice msg") || text.contains("वॉइस नोट")
        ) {
            val target = extractWhatsAppTarget(text)
            return ParsedCommand(Kind.WHATSAPP_VOICE_NOTE, raw, target = cleanTarget(target))
        }

        if (text.contains("message") || text.contains("msg") || text.contains("bhejo") ||
            text.contains("send") || text.contains("likho") || text.contains("भेजो") ||
            text.contains("लिखो")
        ) {
            val target = extractWhatsAppTarget(text)
            val message = extractMessage(text)
            if (target.isNotBlank()) {
                return ParsedCommand(Kind.WHATSAPP_SEND, raw, target = cleanTarget(target), message = message)
            }
        }
        return null
    }

    private fun extractWhatsAppTarget(text: String): String {
        // "whatsapp par rahul ko message bhejo" -> rahul
        val patterns = listOf(
            Regex("(?:whatsapp|व्हाट्सएप)\\s+(?:par|pe|pr|पर|पे)\\s+(.+?)\\s+(?:ko|को|se|से)\\s"),
            Regex("(.+?)\\s+(?:ko|को)\\s+(?:whatsapp|व्हाट्सएप)"),
            Regex("(?:whatsapp|व्हाट्सएप)\\s+(?:par|pe|pr|पर|पे)\\s+(.+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val value = match.groupValues[1]
                    .replace(Regex("\\b(message|msg|likho|bhejo|send|voice note|call)\\b"), "")
                    .trim()
                if (value.isNotBlank()) return value
            }
        }
        return ""
    }

    private fun extractMessage(text: String): String {
        val markers = listOf(
            " bolo ki ", " ki ", " that ", " message likho ", " bhejo ki ",
            " कि ", " bol do ", " saying ", ":"
        )
        for (marker in markers) {
            val index = text.indexOf(marker)
            if (index >= 0) {
                val value = text.substring(index + marker.length).trim()
                if (value.isNotBlank()) return value.trim('"', '\'', ' ')
            }
        }
        // "message bhejo hello" -> last word group after bhejo/likho/send
        val trailing = Regex("(?:bhejo|likho|send|भेजो|लिखो)\\s+(.+)").find(text)
        return trailing?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun extractAppName(text: String): String? {
        val forward = Regex(
            "(?:open|launch|start|run)\\s+(?:the\\s+)?([a-z0-9 ._\\u0900-\\u097F]+?)(?:\\s+app)?$"
        ).find(text)
        if (forward != null) {
            val value = forward.groupValues[1].trim()
            if (value.isNotBlank()) return value
        }
        val hindi = Regex(
            "([a-z0-9 ._\\u0900-\\u097F]+?)\\s+(?:kholo|khol do|chalao|chalu karo|khol)"
        ).find(text)
        if (hindi != null) {
            val value = hindi.groupValues[1].trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun extractAfter(text: String, markers: List<String>): String? {
        for (marker in markers) {
            val index = text.indexOf(marker)
            if (index >= 0) {
                return text.substring(index + marker.length).trim()
            }
        }
        return null
    }

    private fun cleanTarget(value: String): String {
        return value
            .replace(Regex("\\b(ko|को|se|से|par|pe|पर|पे|app|kholo|khol|do|karo|please|zara)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '!', '?')
    }

    private fun firstNumber(text: String): Int? {
        Regex("\\b(\\d{1,3})\\b").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    private fun parseDurationSeconds(text: String): Int? {
        val amount = firstNumber(text) ?: return null
        return when {
            text.contains("second") || text.contains("sec") || text.contains("सेकंड") -> amount
            text.contains("minute") || text.contains("min") || text.contains("मिनट") -> amount * 60
            else -> amount * 60
        }
    }

    fun parseTime(text: String): Pair<Int, Int>? {
        val colon = Regex("(\\d{1,2})[:.](\\d{2})").find(text)
        if (colon != null) {
            val hour = colon.groupValues[1].toIntOrNull() ?: return null
            val minute = colon.groupValues[2].toIntOrNull() ?: 0
            return adjustMeridiem(text, hour, minute)
        }
        val plain = Regex("(\\d{1,2})\\s*(?:baje|baj|o'clock|oclock|बजे|am|pm|a m|p m)").find(text)
        if (plain != null) {
            val hour = plain.groupValues[1].toIntOrNull() ?: return null
            return adjustMeridiem(text, hour, 0)
        }
        val word = Regex(
            "\\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|" +
                "ek|do|teen|char|chaar|panch|paanch|chhe|chhah|saat|aath|nau|das|gyarah|barah)\\b"
        ).find(text)
        if (word != null) {
            val hour = wordToNumber(word.groupValues[1]) ?: return null
            return adjustMeridiem(text, hour, 0)
        }
        return null
    }

    private fun adjustMeridiem(text: String, hour: Int, minute: Int): Pair<Int, Int>? {
        if (hour !in 0..23 || minute !in 0..59) return null
        val evening = text.contains("pm") || text.contains("shaam") || text.contains("sham") ||
            text.contains("evening") || text.contains("raat") || text.contains("रात") ||
            text.contains("शाम") || text.contains("dopahar") || text.contains("दोपहर")
        val morning = text.contains("am") || text.contains("subah") || text.contains("सुबह") ||
            text.contains("morning")
        var adjusted = hour
        if (evening && hour in 1..11) adjusted = hour + 12
        if (morning && hour == 12) adjusted = 0
        return adjusted to minute
    }

    private fun wordToNumber(word: String): Int? = when (word) {
        "one", "ek" -> 1
        "two", "do" -> 2
        "three", "teen", "char", "chaar" -> 3
        "four" -> 4
        "five", "panch", "paanch" -> 5
        "six", "chhe", "chhah" -> 6
        "seven", "saat" -> 7
        "eight", "aath" -> 8
        "nine", "nau" -> 9
        "ten", "das" -> 10
        "eleven", "gyarah" -> 11
        "twelve", "barah" -> 12
        else -> null
    }

    private fun hasOn(text: String): Boolean =
        text.contains(" on") || text.contains("chalu") || text.contains("चालू") ||
            text.contains("jalao") || text.contains("जलाओ") || text.contains("start") ||
            text.contains("kholo") || text.contains("enable")

    private fun hasOff(text: String): Boolean =
        text.contains(" off") || text.contains("off ") || text.contains("band") ||
            text.contains("बंद") || text.contains("bujhao") || text.contains("बुझाओ") ||
            text.contains("disable") || text.contains("stop") || text.contains("disarm") ||
            text.contains("deactivate")

    fun normalize(input: String): String {
        val lowered = input.lowercase().trim()
        return lowered.replace(Regex("\\s+"), " ")
    }
}
