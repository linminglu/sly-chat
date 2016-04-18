package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.UserId

/**
 * Information about a conversation with a contact. Each contact has exactly one conversation.
 *
 * @param lastMessage Last message in the conversation.
 */
data class ConversationInfo(
    val userId: UserId,
    val unreadMessageCount: Int,
    val lastMessage: String?,
    val lastTimestamp: Long?
) {
    init {
        require(unreadMessageCount >= 0) { "unreadMessageCount must be >= 0" }
    }
}