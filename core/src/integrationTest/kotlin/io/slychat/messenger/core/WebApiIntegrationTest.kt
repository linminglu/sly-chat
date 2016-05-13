package io.slychat.messenger.core

import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.crypto.signal.GeneratedPreKeys
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.ApiException
import io.slychat.messenger.core.http.api.accountupdate.*
import io.slychat.messenger.core.http.api.authentication.AuthenticationClient
import io.slychat.messenger.core.http.api.authentication.AuthenticationRequest
import io.slychat.messenger.core.http.api.authentication.AuthenticationResponse
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.http.api.prekeys.*
import io.slychat.messenger.core.http.api.registration.RegistrationClient
import io.slychat.messenger.core.http.api.registration.RegistrationInfo
import io.slychat.messenger.core.http.api.registration.registrationRequestFromKeyVault
import io.slychat.messenger.core.http.get
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.http.api.accountupdate.AccountUpdateClient
import io.slychat.messenger.core.http.api.accountupdate.RequestPhoneUpdateRequest
import io.slychat.messenger.core.http.api.accountupdate.UpdatePhoneRequest
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import java.net.ConnectException
import kotlin.test.*

data class GeneratedSiteUser(
    val user: SiteUser,
    val keyVault: KeyVault
)

fun SiteUser.toContactInfo(): ContactInfo =
    ContactInfo(id, username, name, phoneNumber, publicKey)

fun GeneratedSiteUser.getUserCredentials(authToken: AuthToken, deviceId: Int = DEFAULT_DEVICE_ID): UserCredentials {
    return user.getUserCredentials(authToken, deviceId)
}

fun SiteUser.getUserCredentials(authToken: AuthToken, deviceId: Int = DEFAULT_DEVICE_ID): UserCredentials {
    return UserCredentials(
        SlyAddress(id, deviceId),
        authToken
    )
}

class WebApiIntegrationTest {
    companion object {
        val serverBaseUrl = "http://localhost:8000"

        val defaultRegistrationId = 12345

        //kinda hacky...
        var currentUserId = 1L

        fun nextUserId(): UserId {
            val r = currentUserId
            currentUserId += 1
            return UserId(r)
        }

        fun newSiteUser(registrationInfo: RegistrationInfo, password: String): GeneratedSiteUser {
            val keyVault = generateNewKeyVault(password)
            val serializedKeyVault = keyVault.serialize()

            val user = SiteUser(
                nextUserId(),
                registrationInfo.email,
                keyVault.remotePasswordHashParams.serialize(),
                keyVault.fingerprint,
                registrationInfo.name,
                registrationInfo.phoneNumber,
                serializedKeyVault
            )

            return GeneratedSiteUser(user, keyVault)
        }

        /** Short test for server dev functionality sanity. */
        private fun checkDevServerSanity() {
            val devClient = DevClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())
            val password = "test"
            val username = "a@a.com"

            devClient.clear()

            //users
            val userA = newSiteUser(RegistrationInfo(username, "a", "000-000-0000"), password)
            val siteUser = userA.user

            devClient.addUser(userA)

            val users = devClient.getUsers()

            if (users != listOf(siteUser))
                throw DevServerInsaneException("Register functionality failed")

            //auth token
            val authToken = devClient.createAuthToken(username)

            val gotToken = devClient.getAuthToken(username)

            if (gotToken != authToken)
                throw DevServerInsaneException("Auth token functionality failed")

            //prekeys
            val oneTimePreKeys = listOf("a", "b").sorted()
            devClient.addOneTimePreKeys(username, oneTimePreKeys)

            val gotPreKeys = devClient.getPreKeys(username).oneTimePreKeys.sorted()
            if (gotPreKeys != oneTimePreKeys)
                throw DevServerInsaneException("One-time prekey functionality failed")

            val signedPreKey = "s"
            devClient.setSignedPreKey(username, signedPreKey)

            if (devClient.getSignedPreKey(username) != signedPreKey)
                throw DevServerInsaneException("Signed prekey functionality failed")

            val lastResortPreKey = "l"
            devClient.setLastResortPreKey(username, lastResortPreKey)

            if (devClient.getLastResortPreKey(username) != lastResortPreKey)
                throw DevServerInsaneException("Last resort prekey functionality failed")

            //contacts list
            val userB = newSiteUser(RegistrationInfo("b@a.com", "B", "000-000-0000"), password)

            val contactsA = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.id))
            devClient.addContacts(username, contactsA)

            val contacts = devClient.getContactList(username)

            if (contacts != contactsA)
                throw DevServerInsaneException("Contacts functionality failed")

            //GCM
            val installationId = randomUUID()
            val gcmToken = randomUUID()
            devClient.registerGcmToken(username, installationId, gcmToken)

            val gcmTokens = devClient.getGcmTokens(username)

            if (gcmTokens != listOf(UserGcmTokenInfo(installationId, gcmToken)))
                throw DevServerInsaneException("GCM functionality failed")

            devClient.unregisterGcmToken(username, installationId)

            if (devClient.getGcmTokens(username).size != 0)
                throw DevServerInsaneException("GCM functionality failed")

            //devices
            val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
            val deviceId2 = devClient.addDevice(username, defaultRegistrationId +1, DeviceState.INACTIVE)

            val devices = devClient.getDevices(username)

            val expected = listOf(
                Device(deviceId, defaultRegistrationId, DeviceState.ACTIVE),
                Device(deviceId2, defaultRegistrationId + 1, DeviceState.INACTIVE)
            )

            if (devices != expected)
                throw DevServerInsaneException("Device functionality failed")
        }

        //only run if server is up
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            try {
                val response = io.slychat.messenger.core.http.JavaHttpClient().get("${serverBaseUrl}/dev")
                if (response.code == 404)
                    throw ServerDevModeDisabledException()
            }
            catch (e: ConnectException) {
                Assume.assumeTrue(false)
            }

            checkDevServerSanity()
        }
    }

    val dummyRegistrationInfo = RegistrationInfo("c@a.com", "name", "000-000-0000")
    val password = "test"
    val invalidUserCredentials = UserCredentials(SlyAddress(UserId(999999), 999), AuthToken(""))

    val devClient = DevClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

    var counter = 1111111111;

    fun injectSiteUser(registrationInfo: RegistrationInfo): GeneratedSiteUser {
        val siteUser = newSiteUser(registrationInfo, password)

        devClient.addUser(siteUser)

        return siteUser
    }

    fun injectNamedSiteUser(username: String, phoneNumber: String = counter.toString()): GeneratedSiteUser {
        val registrationInfo = RegistrationInfo(username, "name", phoneNumber)
        counter++
        return injectSiteUser(registrationInfo)
    }

    fun injectNewSiteUser(): GeneratedSiteUser {
        return injectSiteUser(dummyRegistrationInfo)
    }

    fun generatePreKeysForRequest(keyVault: KeyVault): Pair<GeneratedPreKeys, PreKeyRecord> {
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        val lastResortPreKey = generateLastResortPreKey()

        return generatedPreKeys to lastResortPreKey
    }

    @Before
    fun before() {
        counter = 1111111111
        devClient.clear()
    }

    @Test
    fun `register request should succeed when given a unique username`() {
        val keyVault = generateNewKeyVault(password)
        val request = registrationRequestFromKeyVault(dummyRegistrationInfo, keyVault)

        val client = RegistrationClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())
        val result = client.register(request)
        assertNull(result.errorMessage)
        assertNull(result.validationErrors)

        val users = devClient.getUsers()

        assertEquals(1, users.size)
        val user = users[0]

        val expected = SiteUser(
            //don't care about the id
            user.id,
            dummyRegistrationInfo.email,
            keyVault.remotePasswordHashParams.serialize(),
            keyVault.fingerprint,
            dummyRegistrationInfo.name,
            dummyRegistrationInfo.phoneNumber,
            keyVault.serialize()
        )

        assertEquals(expected, user)
    }

    @Test
    fun `register request should fail when a duplicate username is used`() {
        val siteUser = injectNewSiteUser().user
        val registrationInfo = RegistrationInfo(siteUser.username, "name", "0")

        val keyVault = generateNewKeyVault(password)
        val request = registrationRequestFromKeyVault(registrationInfo, keyVault)

        val client = RegistrationClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())
        val result = client.register(request)

        assertNotNull(result.errorMessage, "Null error message")
        val errorMessage = result.errorMessage!!
        assertTrue(errorMessage.contains("taken"), "Invalid error message: $errorMessage}")
    }

    fun sendAuthRequestForUser(userA: GeneratedSiteUser, deviceId: Int): AuthenticationResponse {
        val username = userA.user.username

        val client = AuthenticationClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val paramsApiResult = client.getParams(username)
        assertTrue(paramsApiResult.isSuccess, "Unable to fetch params")

        val csrfToken = paramsApiResult.params!!.csrfToken
        val authRequest = AuthenticationRequest(username, userA.keyVault.remotePasswordHash.hexify(), csrfToken, defaultRegistrationId, deviceId)

        return client.auth(authRequest)
    }

    @Test
    fun `authentication request should succeed when given a valid username and password hash for an existing device`() {
        val userA = injectNewSiteUser()
        val siteUser = userA.user
        val username = siteUser.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authApiResult = sendAuthRequestForUser(userA, deviceId)
        assertTrue(authApiResult.isSuccess, "auth failed: ${authApiResult.errorMessage}")

        val receivedSerializedKeyVault = authApiResult.data!!.keyVault

        assertEquals(siteUser.keyVault, receivedSerializedKeyVault)
    }

    fun runMaxDeviceTest(state: DeviceState) {
        val userA = injectNewSiteUser()
        val username = userA.user.username
        val maxDevices = devClient.getMaxDevices()

        for (i in 0..maxDevices-1)
            devClient.addDevice(username, 12345, state)

        val response = sendAuthRequestForUser(userA, 0)

        assertFalse(response.isSuccess, "Auth succeeded")
        assertTrue("too many registered devices" in response.errorMessage!!.toLowerCase())
    }

    @Test
    fun `attempting to authenticate with active devices maxed out should fail`() {
        runMaxDeviceTest(DeviceState.ACTIVE)
    }

    @Test
    fun `attempting to authenticate with pending devices maxed out should fail`() {
        runMaxDeviceTest(DeviceState.PENDING)
    }

    @Test
    fun `prekey info should reflect the current server prekey count`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(siteUser.user.username, deviceId)

        val maxCount = devClient.getPreKeyMaxCount()

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId, maxCount)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val response = client.getInfo(siteUser.getUserCredentials(authToken))

        assertEquals(generatedPreKeys.oneTimePreKeys.size, response.remaining, "Invalid remaining keys")
        assertEquals(0, response.uploadCount, "Invalid uploadCount")
    }

    @Test
    fun `prekey storage request should fail when an invalid auth token is used`() {
        val keyVault = generateNewKeyVault(password)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        assertFailsWith(UnauthorizedException::class) {
            client.store(invalidUserCredentials, request)
        }
    }

    fun injectPreKeys(username: String, keyVault: KeyVault, deviceId: Int = DEFAULT_DEVICE_ID, count: Int = 1): GeneratedPreKeys {
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, count)
        devClient.addOneTimePreKeys(username, serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys), deviceId)
        devClient.setSignedPreKey(username, serializeSignedPreKey(generatedPreKeys.signedPreKey), deviceId)
        return generatedPreKeys
    }

    fun injectLastResortPreKey(username: String, deviceId: Int = DEFAULT_DEVICE_ID): PreKeyRecord {
        val lastResortPreKey = generateLastResortPreKey()
        devClient.setLastResortPreKey(username, serializeOneTimePreKeys(listOf(lastResortPreKey))[0], deviceId)
        return lastResortPreKey
    }

    fun assertPreKeysStored(username: String, deviceId: Int, generatedPreKeys: GeneratedPreKeys, lastResortPreKey: PreKeyRecord) {
        val preKeys = devClient.getPreKeys(username, deviceId)

        val expectedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)
        val expectedLastResortPreKey = serializePreKey(lastResortPreKey)

        assertEquals(expectedOneTimePreKeys, preKeys.oneTimePreKeys, "One-time prekeys don't match")
        assertEquals(expectedSignedPreKey, preKeys.signedPreKey, "Signed prekey doesn't match")
        assertEquals(expectedLastResortPreKey, preKeys.lastResortPreKey, "Last resort prekey doesn't match")
    }

    @Test
    fun `prekey storage request should store keys on the server when a valid auth token and device id is used`() {
        val siteUser = injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(username, deviceId)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        assertTrue(response.isSuccess)

        assertPreKeysStored(username, deviceId, generatedPreKeys, lastResortPreKey)
    }

    @Test
    fun `attempting to push prekeys to a non-registered device should fail`() {
        val siteUser = injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.username

        val authToken = devClient.createAuthToken(username)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken), request)
        assertFalse(response.isSuccess, "Upload succeeded")
    }

    @Test
    fun `attempting to push prekeys to an inactive device should fail`() {
        val siteUser = injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.INACTIVE)

        val authToken = devClient.createAuthToken(username, deviceId)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        assertFalse(response.isSuccess, "Upload succeeded")
    }

    @Test
    fun `attempting to push prekeys to an pending device should success and update device state`() {
        val siteUser = injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.username
        val registrationId = defaultRegistrationId

        val deviceId = devClient.addDevice(username, registrationId, DeviceState.PENDING)

        val authToken = devClient.createAuthToken(username, deviceId)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(registrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        assertTrue(response.isSuccess, "Upload failed: ${response.errorMessage}")

        val devices = devClient.getDevices(username)
        val updatedDevice = Device(deviceId, registrationId, DeviceState.ACTIVE)
        assertEquals(listOf(updatedDevice), devices)
    }

    @Test
    fun `prekey storage should fail when too many keys are uploaded`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(siteUser.user.username, deviceId)

        val maxCount = devClient.getPreKeyMaxCount()

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId, maxCount+1)
        val lastResortPreKey = generateLastResortPreKey()

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, siteUser.keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val exception = assertFailsWith<ApiException> {
            client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        }

        assertTrue("too many" in exception.message!!.toLowerCase(), "Invalid error message: ${exception.message}")
    }

    @Test
    fun `prekey retrieval should fail when an invalid auth token is used`() {
        val siteUser = injectNewSiteUser()

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())
        assertFailsWith(UnauthorizedException::class) {
            client.retrieve(invalidUserCredentials, PreKeyRetrievalRequest(siteUser.user.id, listOf()))
        }
    }

    //TODO more elaborate tests

    @Test
    fun `prekey retrieval should fail when the target user has no active devices`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username

        val requestingSiteUser = injectNamedSiteUser("b@a.com")
        val requestingUsername = requestingSiteUser.user.username

        devClient.addDevice(username, defaultRegistrationId, DeviceState.PENDING)
        devClient.addDevice(username, defaultRegistrationId, DeviceState.INACTIVE)

        val authToken = devClient.createAuthToken(requestingUsername)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val response = client.retrieve(requestingSiteUser.getUserCredentials(authToken), PreKeyRetrievalRequest(siteUser.user.id, listOf()))

        assertTrue(response.isSuccess)

        assertTrue(response.bundles.isEmpty(), "Bundle not empty")
    }

    @Test
    fun `prekey retrieval should return the next available prekey when a valid auth token is used`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username

        val requestingSiteUser = injectNamedSiteUser("b@a.com")
        val requestingUsername = requestingSiteUser.user.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId)

        val authToken = devClient.createAuthToken(requestingUsername)

        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val response = client.retrieve(requestingSiteUser.getUserCredentials(authToken, deviceId), PreKeyRetrievalRequest(siteUser.user.id, listOf()))

        assertTrue(response.isSuccess)

        assertNotNull(response.bundles, "No prekeys found")
        val bundles = response.bundles

        assertEquals(1, bundles.size, "Invalid number of bundles")
        assertTrue(deviceId in bundles, "Missing device id in bundle")

        val preKeyData = bundles[deviceId]!!

        val serializedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)

        assertEquals(expectedSignedPreKey, preKeyData.signedPreKey, "Signed prekey doesn't match")

        assertTrue(serializedOneTimePreKeys.contains(preKeyData.preKey), "No matching one-time prekey found")
    }

    fun assertNextPreKeyIs(userId: UserId, authToken: AuthToken, expected: PreKeyRecord, signedPreKey: SignedPreKeyRecord) {
        val client = PreKeyClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val userCredentials = UserCredentials(SlyAddress(userId, DEFAULT_DEVICE_ID), authToken)

        val response = client.retrieve(userCredentials, PreKeyRetrievalRequest(userId, listOf()))

        assertTrue(response.isSuccess)

        assertNotNull(response.bundles, "No prekeys found")
        val bundles = response.bundles

        assertEquals(1, bundles.size, "Invalid number of bundles")
        assertTrue(DEFAULT_DEVICE_ID in bundles, "Missing device id in bundle")

        val preKeyData = bundles[DEFAULT_DEVICE_ID]!!

        val expectedOneTimePreKeys = serializePreKey(expected)
        val expectedSignedPreKey = serializeSignedPreKey(signedPreKey)

        assertEquals(expectedSignedPreKey, preKeyData.signedPreKey, "Signed prekey doesn't match")

        assertEquals(expectedOneTimePreKeys, preKeyData.preKey, "One-time prekey doesn't match")
    }

    @Test
    fun `prekey retrieval should return the last resort key once no other keys are available`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username
        val userId = siteUser.user.id

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(siteUser.user.username, deviceId)

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId)
        val lastResortPreKey = injectLastResortPreKey(username, deviceId)

        assertNextPreKeyIs(userId, authToken, generatedPreKeys.oneTimePreKeys[0], generatedPreKeys.signedPreKey)
        assertNextPreKeyIs(userId, authToken, lastResortPreKey, generatedPreKeys.signedPreKey)
    }

    @Test
    fun `new contact fetch from email should return the contact information`() {
        val siteUser = injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.id, siteUser.user.username, siteUser.user.name, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val contactResponseEmail = client.fetchContactInfo(siteUser.getUserCredentials(authToken), NewContactRequest(siteUser.user.username, null))
        assertTrue(contactResponseEmail.isSuccess)

        val receivedEmailContactInfo = contactResponseEmail.contactInfo!!

        assertEquals(contactDetails, receivedEmailContactInfo)
    }

    @Test
    fun `new contact fetch from phone should return the contact information`() {
        val siteUser = injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.id, siteUser.user.username, siteUser.user.name, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val contactResponse = client.fetchContactInfo(siteUser.getUserCredentials(authToken), NewContactRequest(null, siteUser.user.phoneNumber))
        assertTrue(contactResponse.isSuccess)

        val receivedContactInfo = contactResponse.contactInfo!!

        assertEquals(contactDetails, receivedContactInfo)
    }

    fun assertContactListEquals(expected: List<RemoteContactEntry>, actual: List<RemoteContactEntry>) {
        val sortedLocalContacts = expected.sortedBy { it.hash }
        val sortedRemoteContacts = actual.sortedBy { it.hash }

        assertEquals(sortedLocalContacts, sortedRemoteContacts)
    }

    @Test
    fun `Adding contacts to the user's contact list should register the contacts remotely`() {
        val siteUser = injectNewSiteUser()
        val contactUser = injectNamedSiteUser("a@a.com")

        val username = siteUser.user.username
        val authToken = devClient.createAuthToken(username)

        val encryptedContacts = encryptRemoteContactEntries(siteUser.keyVault, listOf(contactUser.user.id))
        val request = AddContactsRequest(encryptedContacts)

        val client = ContactListClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())
        client.addContacts(siteUser.getUserCredentials(authToken), request)

        val contacts = devClient.getContactList(username)

        assertContactListEquals(encryptedContacts, contacts)
    }

    @Test
    fun `Adding a duplicate contact should cause no errors`() {
        val userA = injectNamedSiteUser("a@a.com")
        val userB = injectNamedSiteUser("b@a.com")

        val authToken = devClient.createAuthToken(userA.user.username)
        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.id))
        val request = AddContactsRequest(aContacts)

        val client = ContactListClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())
        val userCredentials = userA.getUserCredentials(authToken)
        client.addContacts(userCredentials, request)
        client.addContacts(userCredentials, request)

        val contacts = devClient.getContactList(userA.user.username)

        assertContactListEquals(aContacts, contacts)
    }

    @Test
    fun `Fetching a contact list should fetch only contacts for that user`() {
        val userA = injectNamedSiteUser("a@a.com")
        val userB = injectNamedSiteUser("b@a.com")
        val userC = injectNamedSiteUser("c@a.com")

        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.id))
        val bContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userC.user.id))

        devClient.addContacts(userA.user.username, aContacts)
        devClient.addContacts(userB.user.username, bContacts)

        val client = ContactListClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.username)
        val response = client.getContacts(userA.getUserCredentials(authToken))

        assertContactListEquals(aContacts, response.contacts)
    }

    @Test
    fun `Removing a contact should remove only that contact`() {
        val userA = injectNamedSiteUser("a@a.com")
        val userB = injectNamedSiteUser("b@a.com")
        val userC = injectNamedSiteUser("c@a.com")

        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userC.user.id, userB.user.id))

        devClient.addContacts(userA.user.username, aContacts)

        val client = ContactListClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.username)

        val request = RemoveContactsRequest(listOf(aContacts[0].hash))
        client.removeContacts(userA.getUserCredentials(authToken), request)

        val contacts = devClient.getContactList(userA.user.username)

        assertContactListEquals(listOf(aContacts[1]), contacts)
    }

    @Test
    fun `findLocalContacts should find matches for both phone number and emails`() {
        val bPhoneNumber = "15555555555"
        val userA = injectNamedSiteUser("a@a.com").user
        val userB = injectNamedSiteUser("b@a.com", bPhoneNumber).user
        val userC = injectNamedSiteUser("c@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val platformContacts = listOf(
            PlatformContact("B", listOf(userC.username), listOf()),
            PlatformContact("C", listOf(), listOf(bPhoneNumber))
        )
        val request = FindLocalContactsRequest(platformContacts)
        val response = client.findLocalContacts(userA.getUserCredentials(authToken), request)

        val expected = listOf(
            userB.toContactInfo(),
            userC.toContactInfo()
        )

        assertEquals(expected, response.contacts.sortedBy { it.email })
    }

    @Test
    fun `fetchContactInfoById should fetch users with the given ids`() {
        val userA = injectNamedSiteUser("a@a.com").user
        val userB = injectNamedSiteUser("b@a.com").user
        val userC = injectNamedSiteUser("c@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val request = FetchContactInfoByIdRequest(listOf(userB.id, userC.id))
        val response = client.fetchContactInfoById(userA.getUserCredentials(authToken), request)

        val expected = listOf(
            userB.toContactInfo(),
            userC.toContactInfo()
        )

        assertEquals(expected, response.contacts.sortedBy { it.email })
    }

    @Test
    fun `Update Phone should succeed when right password is provided`() {
        val user = injectNamedSiteUser("a@a.com")

        val client = RegistrationClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val newPhone = "123453456"

        val request = UpdatePhoneRequest(user.user.username, user.keyVault.remotePasswordHash.hexify(), newPhone)
        val response = client.updatePhone(request)

        assertTrue(response.isSuccess, "Update request failed: ${response.errorMessage}")

        val expected = SiteUser(
            user.user.id,
            user.user.username,
            user.keyVault.remotePasswordHashParams.serialize(),
            user.keyVault.fingerprint,
            user.user.name,
            newPhone,
            user.keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers());
    }

    @Test
    fun `Update Phone should fail when wrong password is provided`() {
        val user = injectNamedSiteUser("a@a.com")

        val client = RegistrationClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val request = UpdatePhoneRequest(user.user.username, "wrongPassword", "1111111111")
        val response = client.updatePhone(request)

        assertFalse(response.isSuccess)

        val expected = SiteUser(
            user.user.id,
            user.user.username,
            user.keyVault.remotePasswordHashParams.serialize(),
            user.keyVault.fingerprint,
            user.user.name,
            user.user.phoneNumber,
            user.keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers());
    }

    @Test
    fun `Update Email should succeed when email is available`() {
        val userA = injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val newEmail = "b@b.com"

        val request = UpdateEmailRequest(newEmail)
        val response = client.updateEmail(userA.getUserCredentials(authToken), request);

        assertTrue(response.isSuccess);
        assertEquals(response.accountInfo!!.username, newEmail)
    }

    @Test
    fun `Update Email should fail when email is not available`() {
        val userA = injectNamedSiteUser("a@a.com").user
        injectNamedSiteUser("b@b.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val newEmail = "b@b.com"

        val request = UpdateEmailRequest(newEmail)
        val response = client.updateEmail(userA.getUserCredentials(authToken), request);

        assertFalse(response.isSuccess);
    }

    @Test
    fun `Update Name should succeed`() {
        val userA = injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val newName = "newName"

        val request = UpdateNameRequest(newName)
        val response = client.updateName(userA.getUserCredentials(authToken), request);

        assertTrue(response.isSuccess);
        assertEquals(response.accountInfo!!.name, newName)
    }

    @Test
    fun `Update Phone should succeed when phone is available`() {
        val userA = injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val newPhone = "12345678901"

        val request = RequestPhoneUpdateRequest(newPhone)
        val userCredentials = userA.getUserCredentials(authToken)
        val response = client.requestPhoneUpdate(userCredentials, request);

        assertTrue(response.isSuccess);

        val secondRequest = ConfirmPhoneNumberRequest("12345")
        val secondResponse = client.confirmPhoneNumber(userCredentials, secondRequest);

        assertTrue(secondResponse.isSuccess);
        assertEquals(secondResponse.accountInfo!!.phoneNumber, newPhone)
    }

    @Test
    fun `Update Phone should fail when phone is not available`() {
        val userA = injectNamedSiteUser("a@a.com").user
        injectNamedSiteUser("b@b.com", "2222222222").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, io.slychat.messenger.core.http.JavaHttpClient())

        val newPhone = "2222222222"

        val request = RequestPhoneUpdateRequest(newPhone)
        val response = client.requestPhoneUpdate(userA.getUserCredentials(authToken), request);

        assertFalse(response.isSuccess, "Update failed");
    }
}