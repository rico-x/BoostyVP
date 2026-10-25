package io.github.ricox.boostyvp.integration

import org.junit.Assert.*
import org.junit.Test

class ExportAppsTest {
    private fun app(pkg: String, method: ExportMethod = ExportMethod.SaveCopy, label: String = pkg) =
        ExportApp(pkg, "$pkg.Activity", label, method)

    @Test fun `TV stub and media share receivers are excluded while file manager remains`() {
        val stub = app("com.android.tv.frameworkpackagestubs", ExportMethod.CreateDocument)
        val cx = app("file.manager")
        val result = selectExportApps(listOf(stub), setOf(stub.packageName, cx.packageName),
            listOf(cx, app("video.player"), app("torrent.client"), app("send.only"), stub))
        assertEquals(listOf(cx), result)
    }

    @Test fun `real document creation takes precedence over save-copy for the same package`() {
        val creator = app("documents", ExportMethod.CreateDocument)
        assertEquals(listOf(creator), selectExportApps(listOf(creator, creator), setOf("documents"),
            listOf(app("documents"))))
    }

    @Test fun `picker without a save receiver is not a writable destination`() {
        assertTrue(selectExportApps(emptyList(), setOf("read.only"), emptyList()).isEmpty())
    }

    @Test fun `apps are unique and sorted by display label`() {
        val a = app("one", label = "Alpha")
        val z = app("two", label = "zeta")
        assertEquals(listOf(a, z), selectExportApps(emptyList(), setOf("one", "two"), listOf(z, a, a)))
    }
}
