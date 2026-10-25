package dev.zed.assistant.command

import dev.zed.assistant.command.ParsedCommand.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserTest {

    @Test
    fun `english hardware commands`() {
        assertEquals(Kind.TORCH_ON, IntentParser.parse("turn on the flashlight").kind)
        assertEquals(Kind.TORCH_OFF, IntentParser.parse("flashlight off").kind)
        assertEquals(Kind.WIFI_OFF, IntentParser.parse("turn off wifi").kind)
        assertEquals(Kind.BT_ON, IntentParser.parse("bluetooth on").kind)
        assertEquals(Kind.BATTERY, IntentParser.parse("battery percentage").kind)
    }

    @Test
    fun `hinglish hardware commands`() {
        assertEquals(Kind.TORCH_ON, IntentParser.parse("torch chalu karo").kind)
        assertEquals(Kind.TORCH_OFF, IntentParser.parse("टॉर्च बंद करो").kind)
        assertEquals(Kind.WIFI_ON, IntentParser.parse("wifi chalu karo").kind)
        assertEquals(Kind.VOLUME_UP, IntentParser.parse("volume badhao").kind)
        assertEquals(Kind.VOLUME_SET, IntentParser.parse("volume 40 karo").kind)
    }

    @Test
    fun `volume set keeps the number`() {
        val parsed = IntentParser.parse("volume 40")
        assertEquals(Kind.VOLUME_SET, parsed.kind)
        assertEquals(40, parsed.number)
    }

    @Test
    fun `brightness set keeps the number`() {
        val parsed = IntentParser.parse("brightness 70")
        assertEquals(Kind.BRIGHTNESS_SET, parsed.kind)
        assertEquals(70, parsed.number)
    }

    @Test
    fun `alarm time is parsed`() {
        val morning = IntentParser.parse("set alarm for 7:30 am")
        assertEquals(Kind.ALARM_SET, morning.kind)
        assertEquals(7, morning.hour)
        assertEquals(30, morning.minute)

        val evening = IntentParser.parse("alarm lagao 9 baje shaam")
        assertEquals(Kind.ALARM_SET, evening.kind)
        assertEquals(21, evening.hour)
    }

    @Test
    fun `whatsapp message extracts target and body`() {
        val parsed = IntentParser.parse("whatsapp par rahul ko message bhejo ki kal milte hain")
        assertEquals(Kind.WHATSAPP_SEND, parsed.kind)
        assertEquals("rahul", parsed.target)
        assertEquals("kal milte hain", parsed.message)
    }

    @Test
    fun `whatsapp call and voice note`() {
        assertEquals(
            Kind.WHATSAPP_CALL,
            IntentParser.parse("whatsapp par mom ko call karo").kind
        )
        assertEquals(
            Kind.WHATSAPP_VIDEO_CALL,
            IntentParser.parse("whatsapp video call to rahul").kind
        )
        assertEquals(
            Kind.WHATSAPP_VOICE_NOTE,
            IntentParser.parse("whatsapp par rahul ko voice note bhejo").kind
        )
    }

    @Test
    fun `call and app launching`() {
        assertEquals(Kind.CALL_CONTACT, IntentParser.parse("call mom").kind)
        val app = IntentParser.parse("open instagram")
        assertEquals(Kind.OPEN_APP, app.kind)
        assertEquals("instagram", app.target)
        val hindiApp = IntentParser.parse("instagram kholo")
        assertEquals(Kind.OPEN_APP, hindiApp.kind)
        assertEquals("instagram", hindiApp.target)
    }

    @Test
    fun `screen and navigation intents`() {
        assertEquals(Kind.READ_SCREEN, IntentParser.parse("read screen please").kind)
        assertEquals(Kind.READ_NOTIFICATIONS, IntentParser.parse("read my notifications").kind)
        assertEquals(Kind.SCREENSHOT, IntentParser.parse("take a screenshot").kind)
        assertEquals(Kind.BACK, IntentParser.parse("go back").kind)
        assertEquals(Kind.HOME, IntentParser.parse("home").kind)
        assertEquals(Kind.SCROLL_DOWN, IntentParser.parse("scroll down").kind)
        assertEquals(Kind.TAP_TEXT, IntentParser.parse("tap on Send").kind)
    }

    @Test
    fun `translation defaults to hindi`() {
        assertEquals(Kind.TRANSLATE_SCREEN, IntentParser.parse("translate this").kind)
        assertEquals("hi", IntentParser.parse("translate this").target)
        assertEquals("en", IntentParser.parse("translate to english").target)
    }

    @Test
    fun `guard and siren`() {
        assertEquals(Kind.ARM_GUARD, IntentParser.parse("arm the anti theft guard").kind)
        assertEquals(Kind.DISARM_GUARD, IntentParser.parse("disarm guard").kind)
        assertEquals(Kind.SIREN_ON, IntentParser.parse("siren bajao").kind)
        assertEquals(Kind.SIREN_OFF, IntentParser.parse("stop the siren").kind)
    }

    @Test
    fun `unknown stays unknown`() {
        assertTrue(IntentParser.parse("write me a poem about the moon").kind == Kind.UNKNOWN)
    }
}
