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

    @Query("SELECT * FROM private_messages WHERE target = :target ORDER BY timestamp ASC")
    fun getMessagesForTarget(target: String): Flow<List<MessageEntity>>
}
