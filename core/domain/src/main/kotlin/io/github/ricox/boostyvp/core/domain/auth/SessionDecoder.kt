package io.github.ricox.boostyvp.core.domain.auth

import io.github.ricox.boostyvp.core.model.Session

fun interface SessionDecoder {
    fun decode(rawAuth: String, clientId: String): Session
}
