package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.ApiResult
import io.slychat.messenger.core.http.api.EmptyResponse
import io.slychat.messenger.core.http.api.apiGetRequest
import io.slychat.messenger.core.http.api.apiPostRequest
import io.slychat.messenger.core.typeRef

class AddressBookClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    fun get(userCredentials: UserCredentials): GetAddressBookResponse {
        val url = "$serverBaseUrl/v1/address-book"

        return apiGetRequest(httpClient, url, userCredentials, listOf(), typeRef())
    }

    fun update(userCredentials: UserCredentials, request: UpdateAddressBookRequest): Unit {
        val url = "$serverBaseUrl/v1/address-book"

        apiPostRequest(httpClient, url, userCredentials, request, typeRef<ApiResult<EmptyResponse>>())
    }
}