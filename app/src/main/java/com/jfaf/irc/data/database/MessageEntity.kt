package com.jfaf.irc.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "private_messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: String, // The person the conversation is with
    val sender: String,
    val content: String,
    val timestamp: Long,
    val isOwnMessage: Boolean
)
