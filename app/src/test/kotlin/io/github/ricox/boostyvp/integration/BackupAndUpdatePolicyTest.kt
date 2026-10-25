package io.github.ricox.boostyvp.integration

import org.junit.Assert.*
import org.junit.Test

class BackupAndUpdatePolicyTest {
    private fun backup(player: String = "{}", ui: String = "{}", tv: String = "{}") =
        """{"app":"BoostyVP","schema":1,"preferences":{"player_preferences":$player,"interface_preferences":$ui,"tv_channels":$tv}}"""
    @Test fun `backup decodes supported typed preferences only`() {
        val decoded = BackupSchema.decode(backup("""{"seek_back_seconds":15,"remember_speed":false,"video_scale":"crop"}""", """{"card_size":"small"}"""))
        assertEquals(15, decoded.getValue("player_preferences")["seek_back_seconds"])
        assertEquals(false, decoded.getValue("player_preferences")["remember_speed"])
    }
    @Test fun `backup rejects injected tokens unknown keys wrong types and versions`() {
        listOf(backup("""{"access_token":"secret"}"""), backup("""{"seek_back_seconds":"15"}"""),
            backup("""{"playback_speed_author_private":"1.5"}"""), backup().replace("\"schema\":1", "\"schema\":2"),
            backup().replace("\"BoostyVP\"", "\"OtherApp\""), " ".repeat(70_000)).forEach {
            assertTrue(runCatching { BackupSchema.decode(it) }.isFailure)
        }
    }
    @Test fun `updater accepts only original repository download paths over https`() {
        assertTrue(UpdatePolicy.trustedAsset("https://github.com/rico-x/BoostyVP/releases/download/v2026.10.25.1/a.apk"))
        listOf("http://github.com/rico-x/BoostyVP/releases/download/v1/a.apk", "https://github.com/other/BoostyVP/releases/download/v1/a.apk",
            "https://github.com.evil.invalid/rico-x/BoostyVP/releases/download/v1/a.apk", "https://github.com/rico-x/BoostyVP/releases/download/v1/a.apk?redirect=bad").forEach {
            assertFalse(UpdatePolicy.trustedAsset(it))
        }
    }
    @Test fun `updater blocks downgrade different package and different signer`() {
        val info = AvailableUpdate("2026.10.25.1", 26102501, "notes", "a.apk", "https://github.com/rico-x/BoostyVP/releases/download/v1/a.apk", "a".repeat(64), 100, "signer")
        UpdatePolicy.validate(info, "app", "app", info.code, 26102400, "signer")
        assertTrue(runCatching { UpdatePolicy.validate(info, "other", "app", info.code, 1, "signer") }.isFailure)
        assertTrue(runCatching { UpdatePolicy.validate(info, "app", "app", info.code, info.code, "signer") }.isFailure)
        assertTrue(runCatching { UpdatePolicy.validate(info, "app", "app", info.code, 1, "unknown") }.isFailure)
        assertTrue(runCatching { UpdatePolicy.validate(info, "app", "app", info.code + 1, 1, "signer") }.isFailure)
    }
}
