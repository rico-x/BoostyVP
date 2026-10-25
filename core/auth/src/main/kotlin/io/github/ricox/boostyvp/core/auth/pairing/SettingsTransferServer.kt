package io.github.ricox.boostyvp.core.auth.pairing

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import java.util.UUID

/** Temporary settings-file transport only; separate from authentication and pairing. */
class SettingsTransferServer(private val export: () -> String, private val receive: (String) -> Unit) {
    private var server: MiniHttpServer? = null
    private val path = "/settings/" + UUID.randomUUID().toString().replace("-", "")
    fun start(host: String): String {
        check(server == null)
        val created = MiniHttpServer(0) { request ->
            when {
                request.method == "GET" && request.path == path -> HttpResponse.html(page(path))
                request.method == "GET" && request.path == "$path/download" -> HttpResponse(200, export(), "application/json; charset=utf-8")
                request.method == "POST" && request.path == "$path/upload" -> {
                    if (request.body.toByteArray().size > 64 * 1024) HttpResponse.badRequest(tr(UiText.settings_backup_001))
                    else try { receive(request.body); HttpResponse.ok(tr(UiText.settings_transfer_server_001)) }
                    catch (_: Exception) { HttpResponse.badRequest(tr(UiText.settings_transfer_server_002)) }
                }
                else -> HttpResponse.notFound()
            }
        }
        val port = try { created.start() } catch (e: Exception) { created.stop(); throw e }
        server = created
        return "http://$host:$port$path"
    }
    fun stop() { server?.stop(); server = null }
    private fun page(path: String) = tr(UiText.settings_transfer_server_003, path).trimIndent()
}
