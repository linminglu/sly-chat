package com.vfpowertech.keytap.core.http

/** Simple HTTP Client. */
interface HttpClient {
    /** Get a http resource. */
    fun get(url: String): HttpResponse

    /** Post the given data as application/json. */
    fun postJSON(url: String, body: ByteArray): HttpResponse
}

/** GET request with query params. */
fun HttpClient.get(url: String, queryParams: List<Pair<String, String>>): HttpResponse {
    val query = toQueryString(queryParams)
    val fullUrl = if (query.isNotEmpty()) "$url?$query" else url
    return get(fullUrl)
}

