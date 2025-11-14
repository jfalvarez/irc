package com.jfaf.irc.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM private_messages WHERE conversationPartnerNick = :partnerNick ORDER BY timestamp ASC")
    fun getMessagesForTarget(partnerNick: String): Flow<List<MessageEntity>>
}
