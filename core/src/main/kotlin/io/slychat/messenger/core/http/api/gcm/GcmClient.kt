package io.slychat.messenger.core.http.api.gcm

import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.EmptyResponse
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.typeRef

class GcmClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun register(userCredentials: UserCredentials, request: RegisterRequest): RegisterResponse {
        val url = "$serverBaseUrl/v1/gcm/register"
        return apiPostRequest(httpClient, url, userCredentials, request, typeRef())
    }

    fun unregister(userCredentials: UserCredentials, request: UnregisterRequest) {
        val url = "$serverBaseUrl/v1/gcm/unregister"
        apiPostRequest(httpClient, url, userCredentials, request, typeRef<ApiResult<EmptyResponse>>())
    }
}