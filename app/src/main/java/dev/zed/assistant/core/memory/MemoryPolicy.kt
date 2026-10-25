package dev.zed.assistant.core.memory

/**
 * Zed's judgement about what is worth remembering for good.
 *
 * A memory that keeps everything is not a memory, it is a transcript. This decides, in
 * plain Kotlin and without a model, whether a candidate belongs in permanent memory:
 *
 *  - **permanent fact** — names, people, preferences, routines, places, plans, decisions,
 *    anything the owner would be annoyed to repeat;
 *  - **episode** — worth a one-line note of what happened, but not a durable fact;
 *  - **refused** — small talk, questions, or anything that looks like a credential.
 *
 * Credentials are never stored. Not with a confirmation, not because the user asked
 * nicely, not ever. Everything here is deterministic so it can be unit tested.
 */
object MemoryPolicy {

    enum class Kind { PERMANENT, EPISODE, REFUSE }

    data class Decision(val kind: Kind, val reason: String)

    private val credentials = listOf(
        "password", "passcode", "pass word", "pin code", "pincode", "pin number",
        "cvv", "otp", "one time password", "one-time password",
        "api key", "apikey", "secret key", "private key", "access token", "auth token",
        "aadhaar", "aadhar", "social security", "ssn",
        "bank account", "account number", "card number", "debit card", "credit card",
        "upi id", "ifsc"
    )

    private val credentialShapes = listOf(
        Regex("\\bAIza[0-9A-Za-z_\\-]{12,}"),
        Regex("\\bgsk_[0-9A-Za-z]{12,}"),
        Regex("\\bsk-[0-9A-Za-z_\\-]{12,}"),
        Regex("\\bghp_[0-9A-Za-z]{12,}"),
        Regex("\\b\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{4}\\b"),
        // Six or more digits in a row is a code, an account or an OTP far more often than
        // it is something a person wants recited back to them later.
        Regex("\\b\\d{6,}\\b")
    )

    private val smallTalk = setOf(
        "ok", "okay", "k", "thanks", "thank you", "thx", "hello", "hi", "hey", "yo",
        "good morning", "good night", "good evening", "nothing", "never mind", "nevermind",
        "no", "yes", "yeah", "nope", "hmm", "cool", "nice", "by", "bye", "goodbye",
        "haan", "han", "theek hai", "thik hai", "chal", "chalo", "acha", "accha", "sahi"
    )

    /** Words that mean the sentence is about the owner rather than about the moment. */
    private val durableMarkers = listOf(
        "my name", "i am ", "i'm ", "im ", "i live", "i work", "i study", "i go to",
        "i like", "i love", "i hate", "i prefer", "i always", "i never", "i usually",
        "every day", "every morning", "every night", "every week", "usually", "routine",
        "remember", "remind me that", "don't forget", "do not forget",
        "allergic", "allergy", "vegetarian", "my birthday", "my sister", "my brother",
        "my mother", "my father", "my mom", "my mum", "my dad", "my wife", "my husband",
        "my friend", "my boss", "my doctor", "my car", "my bike", "my house", "my flat",
        "my room", "my office", "my locker", "my number", "my email", "my address",
        "i parked", "i take", "i bought", "i booked", "i ordered", "i decided",
        "mera ", "meri ", "mujhe ", "main ", "mai ", "hum ", "hamara ", "hamari ",
        "yaad rakh", "yaad rakhna", "bhool mat", "pasand", "roz ", "har din"
    )

    fun judge(text: String, explicit: Boolean = false): Decision {
        val cleaned = text.trim().trim('.', '!', '।')
        if (cleaned.length < 4) return Decision(Kind.REFUSE, "too short to mean anything")

        if (looksLikeCredential(cleaned)) {
            return Decision(Kind.REFUSE, "looks like a credential, and I don't keep those")
        }

        val normalised = cleaned.lowercase()

        // An explicit "remember this" beats the small-talk filter, but never the secret one.
        if (!explicit && (normalised in smallTalk || normalised.length < 6)) {
            return Decision(Kind.REFUSE, "small talk")
        }

        if (durableMarkers.any { normalised.contains(it) }) {
            return Decision(Kind.PERMANENT, "durable detail about the owner")
        }

        if (explicit) return Decision(Kind.PERMANENT, "the owner asked me to keep it")

        if (normalised.endsWith("?")) {
            return Decision(Kind.REFUSE, "a question is not a fact")
        }

        return if (cleaned.length >= 14) {
            Decision(Kind.EPISODE, "worth a note about the conversation, not a fact")
        } else {
            Decision(Kind.REFUSE, "nothing durable in it")
        }
    }

    /** True for anything that must never reach memory, in any form. */
    fun looksLikeCredential(text: String): Boolean {
        val lower = text.lowercase()
        if (credentials.any { lower.contains(it) }) return true
        return credentialShapes.any { it.containsMatchIn(text) }
    }
}
