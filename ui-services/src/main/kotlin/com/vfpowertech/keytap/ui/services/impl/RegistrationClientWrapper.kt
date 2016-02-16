package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.registration.RegisterRequest
import com.vfpowertech.keytap.core.http.api.registration.RegisterResponse
import com.vfpowertech.keytap.core.http.api.registration.RegistrationClient
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class RegistrationClientWrapper(serverUrl: String) {
    private val registrationClient = RegistrationClient(serverUrl, JavaHttpClient())

    fun register(request: RegisterRequest): Promise<RegisterResponse, Exception> = task {
        val apiResponse = registrationClient.register(request)

        apiResponse.getOrThrow { it }
    }
}