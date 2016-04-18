package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.UserId

data class ContactInfo(
    @JsonProperty("id")
    val id: UserId,
    @JsonProperty("email")
    val email: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("phone-number")
    val phoneNumber: String?,
    @JsonProperty("public-key")
    val publicKey: String
)