package com.jfaf.irc.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "private_messages",
    indices = [Index(value = ["conversationPartnerNick"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationPartnerNick: String,
    val senderNick: String,
    val content: String,
    val timestamp: Long,
    val isOwnMessage: Boolean
)
