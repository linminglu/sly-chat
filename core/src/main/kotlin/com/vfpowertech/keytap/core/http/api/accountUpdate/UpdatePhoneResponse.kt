package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class UpdatePhoneResponse(
    @param:JsonProperty("error-message")
    @get:com.fasterxml.jackson.annotation.JsonProperty("error-message")
    val errorMessage: String?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}