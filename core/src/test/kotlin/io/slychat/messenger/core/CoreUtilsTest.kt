package io.slychat.messenger.core

import org.junit.Test
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreUtilsTest {
    @Test
    fun `isNotNetworkError should return false for SocketTimeoutException`() {
        assertFalse(isNotNetworkError(SocketTimeoutException()))
    }

    @Test
    fun `isNotNetworkError should return false for UnknownHostException`() {
        assertFalse(isNotNetworkError(UnknownHostException()))
    }

    @Test
    fun `isNotNetworkError should return false for SSLHandshakeException`() {
        assertFalse(isNotNetworkError(SSLHandshakeException("SSL handshake aborted: ssl=0x942d9800: I/O error during system call, Connection timed out")))
    }

    @Test
    fun `isNotNetworkError should return false for SocketException ETIMEDOUT`() {
        assertFalse(isNotNetworkError(SocketException("ETIMEDOUT (Connection timed out)")))
    }

    @Test
    fun `isNotNetworkError should return true for other SocketExceptions`() {
        assertTrue(isNotNetworkError(SocketException("some error")))
        assertTrue(isNotNetworkError(SocketException(null)))
    }
}