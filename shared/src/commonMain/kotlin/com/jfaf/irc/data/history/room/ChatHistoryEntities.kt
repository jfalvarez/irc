package com.jfaf.irc.data.history.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val target: String,
    val position: Int,
    val isActive: Boolean
)

@Entity(tableName = "messages", indices = [Index("target")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: String,
    val position: Int,
    val fullText: String,
    val type: String,
    val sender: String?,
    val isOwnMessage: Boolean,
    val mediaUrl: String?,
    val mediaType: String
)
