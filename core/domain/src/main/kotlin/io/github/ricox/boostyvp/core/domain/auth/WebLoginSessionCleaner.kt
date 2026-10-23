package io.github.ricox.boostyvp.core.domain.auth

fun interface WebLoginSessionCleaner {
    suspend fun clear()
}
