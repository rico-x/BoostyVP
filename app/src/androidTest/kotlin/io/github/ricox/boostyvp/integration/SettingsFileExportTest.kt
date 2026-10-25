package io.github.ricox.boostyvp.integration

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsFileExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val export = SettingsFileExport(context)
    private val text = """{"app":"BoostyVP","schema":1,"preferences":{"player_preferences":{},"interface_preferences":{},"tv_channels":{}}}"""
    private val app = ExportApp("example.files", "example.files.Save", "Files", ExportMethod.SaveCopy)

    @Test fun immutableCopyIsReadableAsJsonAndHasSuggestedFilename() {
        check(context.packageName.endsWith(".debug"))
        val name = export.filename(1_700_000_000_000)
        val first = export.prepareCopy(text, name)
        val second = export.prepareCopy(text.replace("\"tv_channels\":{}", "\"tv_channels\":{\"cards\":10}"), name)
        assertNotEquals(first, second)
        assertEquals("application/json", context.contentResolver.getType(first))
        context.contentResolver.openInputStream(first)!!.use { assertEquals(text, it.bufferedReader().readText()) }
        context.contentResolver.query(first, arrayOf("_display_name"), null, null, null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals(name, it.getString(0))
        }
    }

    @Test fun copyIntentTargetsChosenAppAndGrantsOnlyTemporaryReadAccess() {
        val uri = export.prepareCopy(text, export.filename())
        val intent = export.copyIntent(app, uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(app.packageName, intent.component!!.packageName)
        assertEquals(app.activityName, intent.component!!.className)
        assertEquals("application/json", intent.type)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
    }

    @Test fun nativeCreatorReceivesExplicitDestinationAndSuggestedName() {
        val intent = export.documentIntent(app.copy(method = ExportMethod.CreateDocument), "backup.json")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("backup.json", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(app.activityName, intent.component!!.className)
    }

    @Test fun providerRejectsAppPreferencesAndUnsafeOrNonSettingsCopies() {
        val privateFile = File(context.cacheDir, "not-an-export.txt")
        assertTrue(runCatching { FileProvider.getUriForFile(context, "${context.packageName}.exports", privateFile) }.isFailure)
        assertTrue(runCatching { export.prepareCopy(text, "../../settings.json") }.isFailure)
        assertTrue(runCatching { export.prepareCopy("{\"token\":\"private\"}", export.filename()) }.isFailure)
    }
}
