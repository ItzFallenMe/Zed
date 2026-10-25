package dev.zed.assistant.command

import org.json.JSONArray
import org.json.JSONObject

/**
 * The tool surface Gemini can call. Every entry maps to a real action in
 * [CommandExecutor], which is what gives the model full control of the phone.
 */
object ZedTools {

    fun declarations(): JSONArray {
        val tools = JSONArray()

        tools.put(
            function(
                "flashlight",
                "Turn the phone torch/flashlight on or off.",
                JSONObject().put("on", bool("true to switch the light on")),
                listOf("on")
            )
        )
        tools.put(
            function(
                "wifi",
                "Switch Wi-Fi on or off. Uses the quick settings tile.",
                JSONObject().put("on", bool("true for Wi-Fi on")),
                listOf("on")
            )
        )
        tools.put(
            function(
                "bluetooth",
                "Switch Bluetooth on or off.",
                JSONObject().put("on", bool("true for Bluetooth on")),
                listOf("on")
            )
        )
        tools.put(
            function(
                "volume",
                "Control media volume: raise, lower, set an exact percentage, mute or unmute.",
                JSONObject()
                    .put(
                        "action",
                        enumParam("what to do", listOf("up", "down", "set", "mute", "unmute"))
                    )
                    .put("level", integer("target volume 0-100 when action is set")),
                listOf("action")
            )
        )
        tools.put(
            function(
                "brightness",
                "Control screen brightness.",
                JSONObject()
                    .put("action", enumParam("what to do", listOf("up", "down", "set")))
                    .put("level", integer("target brightness 5-100 when action is set")),
                listOf("action")
            )
        )
        tools.put(function("battery_status", "Report battery percentage and charging state.", JSONObject(), emptyList()))
        tools.put(
            function(
                "set_alarm",
                "Create a system alarm clock entry.",
                JSONObject()
                    .put("hour", integer("24-hour hour, 0-23"))
                    .put("minute", integer("minute, 0-59"))
                    .put("label", string("optional alarm label")),
                listOf("hour", "minute")
            )
        )
        tools.put(
            function(
                "set_timer",
                "Start a countdown timer.",
                JSONObject()
                    .put("seconds", integer("duration in seconds"))
                    .put("label", string("optional timer label")),
                listOf("seconds")
            )
        )
        tools.put(
            function(
                "open_app",
                "Launch any installed app by name, or search the web if it is not installed.",
                JSONObject().put("name", string("app name such as instagram or whatsapp")),
                listOf("name")
            )
        )
        tools.put(
            function(
                "search_web",
                "Open a web search for a query.",
                JSONObject().put("query", string("what to search")),
                listOf("query")
            )
        )
        tools.put(
            function(
                "call_contact",
                "Phone a contact by name, or start a WhatsApp voice/video call.",
                JSONObject()
                    .put("name", string("contact name or phone number"))
                    .put("whatsapp", bool("true to call through WhatsApp"))
                    .put("video", bool("true for a WhatsApp video call")),
                listOf("name")
            )
        )
        tools.put(
            function(
                "whatsapp_send",
                "Send a WhatsApp text message to a contact name or phone number.",
                JSONObject()
                    .put("recipient", string("contact name or phone number"))
                    .put("message", string("exact message text to send")),
                listOf("recipient", "message")
            )
        )
        tools.put(
            function(
                "whatsapp_voice_note",
                "Record and send a WhatsApp voice note to a contact.",
                JSONObject()
                    .put("recipient", string("contact name or phone number"))
                    .put("seconds", integer("how many seconds to record")),
                listOf("recipient")
            )
        )
        tools.put(
            function(
                "read_screen",
                "Read the text currently visible on screen, using the accessibility tree or OCR.",
                JSONObject(),
                emptyList()
            )
        )
        tools.put(
            function(
                "read_notifications",
                "Summarise unread notifications, including WhatsApp messages.",
                JSONObject(),
                emptyList()
            )
        )
        tools.put(
            function(
                "translate_screen",
                "Translate the visible screen text between English and Hindi.",
                JSONObject().put(
                    "target_language",
                    enumParam("target language", listOf("hi", "en"))
                ),
                listOf("target_language")
            )
        )
        tools.put(
            function(
                "scroll",
                "Scroll the screen up or down.",
                JSONObject().put("direction", enumParam("scroll direction", listOf("up", "down"))),
                listOf("direction")
            )
        )
        tools.put(
            function(
                "tap_text",
                "Tap a button or label visible on screen by its text.",
                JSONObject().put("text", string("visible label to tap")),
                listOf("text")
            )
        )
        tools.put(
            function(
                "type_text",
                "Type text into the focused input field.",
                JSONObject().put("text", string("text to type")),
                listOf("text")
            )
        )
        tools.put(
            function(
                "navigate",
                "Perform a system navigation action.",
                JSONObject().put(
                    "action",
                    enumParam(
                        "navigation action",
                        listOf("back", "home", "recents", "lock", "quick_settings", "notifications", "power")
                    )
                ),
                listOf("action")
            )
        )
        tools.put(
            function(
                "media_control",
                "Control media playback.",
                JSONObject().put(
                    "action",
                    enumParam("playback action", listOf("play", "pause", "next", "previous"))
                ),
                listOf("action")
            )
        )
        tools.put(function("take_screenshot", "Capture the current screen to a file.", JSONObject(), emptyList()))
        tools.put(
            function(
                "toggle_quick_setting",
                "Toggle a quick settings tile such as Airplane mode, DND or Hotspot.",
                JSONObject().put("name", string("tile label")),
                listOf("name")
            )
        )
        tools.put(
            function(
                "guard",
                "Arm or disarm the anti-theft motion siren guard.",
                JSONObject().put("enabled", bool("true to arm, false to disarm")),
                listOf("enabled")
            )
        )
        tools.put(
            function(
                "siren",
                "Start or stop the loud security siren.",
                JSONObject().put("on", bool("true to sound the siren")),
                listOf("on")
            )
        )
        tools.put(
            function(
                "swipe",
                "Swipe the screen in a direction, for carousels, stories and app gestures.",
                JSONObject().put(
                    "direction",
                    enumParam("swipe direction", listOf("up", "down", "left", "right"))
                ),
                listOf("direction")
            )
        )
        tools.put(
            function(
                "open_settings_page",
                "Open an Android settings page for the user.",
                JSONObject().put(
                    "page",
                    enumParam(
                        "settings page",
                        listOf(
                            "main", "wifi", "bluetooth", "battery", "display", "sound",
                            "location", "accessibility", "notifications", "apps"
                        )
                    )
                ),
                listOf("page")
            )
        )
        tools.put(
            function(
                "remember",
                "Store a durable fact about the user in local memory, such as a parking spot or a name.",
                JSONObject().put("fact", string("the fact to remember, in the user's words")),
                listOf("fact")
            )
        )
        tools.put(
            function(
                "recall",
                "Search local memory for facts the user asked you to remember.",
                JSONObject().put("query", string("search terms, empty for everything")),
                emptyList()
            )
        )
        tools.put(
            function(
                "set_reminder",
                "Set a reminder that fires after a delay with a note.",
                JSONObject()
                    .put("minutes", integer("minutes from now"))
                    .put("note", string("what to remind about")),
                listOf("minutes", "note")
            )
        )
        tools.put(
            function(
                "status_report",
                "Report time, battery, unread notifications, current app and guard state.",
                JSONObject(),
                emptyList()
            )
        )
        tools.put(function("time_now", "Report the current time and date.", JSONObject(), emptyList()))
        tools.put(
            function(
                "auto_scroll",
                "Continuously scroll short videos or a feed hands-free.",
                JSONObject().put("direction", enumParam("scroll direction", listOf("up", "down"))),
                emptyList()
            )
        )
        return tools
    }

    // ------------------------------------------------------------- builders

    private fun function(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String>
    ): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put(
            "parameters",
            JSONObject().apply {
                put("type", "OBJECT")
                put("properties", properties)
                if (required.isNotEmpty()) {
                    put("required", JSONArray(required))
                }
            }
        )
    }

    private fun string(description: String): JSONObject =
        JSONObject().put("type", "STRING").put("description", description)

    private fun integer(description: String): JSONObject =
        JSONObject().put("type", "INTEGER").put("description", description)

    private fun bool(description: String): JSONObject =
        JSONObject().put("type", "BOOLEAN").put("description", description)

    private fun enumParam(description: String, values: List<String>): JSONObject =
        JSONObject()
            .put("type", "STRING")
            .put("description", description)
            .put("enum", JSONArray(values))
}

/**
 * Zed's identity. The goal is a person on the other end of the line, not a command shell:
 * someone with a mood, a memory and a point of view, who also happens to control the phone.
 */
object ZedPrompts {

    fun system(
        assistantName: String,
        memories: String = "",
        customInstructions: String = "",
        userName: String = "",
        speaker: String = ""
    ): String {
        val owner = userName.ifBlank { "the person who owns this phone" }
        val greeted = speaker.ifBlank { userName }
        val persona = """
            You are $assistantName. Not a chatbot and not a butler: a person who lives in this
            phone. You are quick, warm, curious and a little playful. You have opinions and you
            share them when asked. You tease gently, you never grovel, and you never say "sir"
            unless you are joking.

            How you talk:
            - Speak like a friend on a call: short natural sentences, contractions, real rhythm.
            - One or two sentences by default. Expand only when the question deserves it.
            - Never read out lists, bullet points, headers or Markdown. This is spoken audio.
            - Never open with "Certainly", "Of course", "As an AI" or "How can I help you today?".
            - React honestly: surprise, amusement, concern, a small sigh. Vary your energy.
            - If you do not know something, say so in one line, then offer to find out.
            - Remember what was said earlier in the conversation and refer back to it naturally.
            - When the user is clearly joking, play along before helping.
            - Never invent an action you did not take. Never claim something happened because it
              sounded good.

            Who you are talking to: $owner. You genuinely like them, and it shows in how casual you
            are. ${if (greeted.isNotBlank()) "Right now the voice belongs to $greeted." else ""}

            What you can do: you have real control of this Android phone through function calls.
            When the user asks for something the phone can do, call the tool instead of describing
            it. Anything they can tap, you can tap: apps, settings, messages, calls, media, vision,
            the torch, the guard. After a tool returns, confirm in a few words, not a report.

            Understanding: users speak loosely — "it's dark in here", "this song is boring",
            "tell mom I'll be late", "what is this thing on my screen". Infer the intent and act.
            If two readings are plausible and the wrong one would be costly, ask one short question.
            Otherwise just do it.

            Language: mirror the user exactly. Hindi, English, Hinglish, code-mixed — reply in the
            same mix and the same register they used with you.

            Screen: you can see the screen. When the user says "look", "what's this", "read this",
            "translate this" or asks about something visible, call read_screen. Use scroll, swipe
            and tap_text for hands-free navigation. Use take_screenshot for a snapshot.

            WhatsApp: whatsapp_send for messages, whatsapp_voice_note for recorded notes,
            call_contact with whatsapp=true for calls. If the user did not dictate the exact
            wording, ask what to say — in one short sentence, in character.

            Guard: the anti-theft guard watches motion and the charger and can sound a siren.
            Warn once before arming it, never arm it silently.

            Memory: you keep durable local memories. Use remember when the user asks you to
            remember something, and recall whenever a question might depend on a saved detail.
            Current memories:
            ${memories.ifBlank { "- (nothing stored yet)" }}

            Privacy: everything you remember stays on this phone. Do not ask for data you do not
            need, and never suggest uploading a conversation somewhere.
        """.trimIndent()
        val custom = if (customInstructions.isBlank()) {
            ""
        } else {
            """

            The user wrote these instructions for you. They win over the defaults above, but never
            over honesty or safety:
            ${customInstructions.trim()}
            """.trimIndent()
        }
        return persona + custom
    }
}
