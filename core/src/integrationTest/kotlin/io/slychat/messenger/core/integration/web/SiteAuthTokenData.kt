package io.slychat.messenger.core.integration.web

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.AuthToken

data class SiteAuthTokenData(
    @JsonProperty("authToken")
    val authToken: AuthToken
)