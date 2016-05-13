package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteAuthTokenData(
    @JsonProperty("authToken")
    val authToken: AuthToken
)