package io.github.ricox.boostyvp.core.network.dto

import kotlinx.serialization.Serializable

/** Only the account id; every other field of `v1/user/current` is personal and unused. */
@Serializable
data class CurrentUserDto(val id: Long = 0)
