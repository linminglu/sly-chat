package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

/** Manages contacts. */
interface ContactsPersistenceManager {
    fun get(userId: UserId): Promise<ContactInfo?, Exception>
    fun getAll(): Promise<List<ContactInfo>, Exception>
    /** Returns info for all available conversations. */
    fun getAllConversations(): Promise<List<Conversation>, Exception>

    /** Returns a ConversationInfo for the given user. */
    fun getConversationInfo(userId: UserId): Promise<ConversationInfo, Exception>

    /** Resets unread message count for the given contact's conversation. */
    fun markConversationAsRead(userId: UserId): Promise<Unit, Exception>

    /** Adds a new contact and conversation for a contact. */
    fun add(contactInfo: ContactInfo): Promise<Unit, Exception>
    fun addAll(contacts: List<ContactInfo>): Promise<Unit, Exception>
    /** Updates the given contact's info. */
    fun update(contactInfo: ContactInfo): Promise<Unit, Exception>
    /** Removes a contact and their associated conversation. */
    fun remove(contactInfo: ContactInfo): Promise<Unit, Exception>

    fun searchByPhoneNumber(phoneNumber: String): Promise<List<ContactInfo>, Exception>
    fun searchByName(name: String): Promise<List<ContactInfo>, Exception>
    fun searchByEmail(email: String): Promise<List<ContactInfo>, Exception>

    /** Find which platform contacts aren't currently in the contacts list. */
    fun findMissing(platformContacts: List<PlatformContact>): Promise<List<PlatformContact>, Exception>

    /** Diff the current contact list with the given remote one. */
    fun getDiff(ids: List<UserId>): Promise<ContactListDiff, Exception>

    fun applyDiff(newContacts: List<ContactInfo>, removedContacts: List<UserId>): Promise<Unit, Exception>
}