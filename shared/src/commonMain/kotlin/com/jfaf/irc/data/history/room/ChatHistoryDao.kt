package com.jfaf.irc.data.history.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ChatHistoryDao {

    @Query("SELECT * FROM tabs ORDER BY position")
    abstract suspend fun tabs(): List<TabEntity>

    @Query("SELECT * FROM messages ORDER BY target, position")
    abstract suspend fun messages(): List<MessageEntity>

    @Query("DELETE FROM tabs")
    protected abstract suspend fun clearTabs()

    @Insert
    protected abstract suspend fun insertTabs(tabs: List<TabEntity>)

    @Query("DELETE FROM messages WHERE target = :target")
    abstract suspend fun deleteMessages(target: String)

    @Insert
    protected abstract suspend fun insertMessages(messages: List<MessageEntity>)

    @Transaction
    open suspend fun replaceTabs(tabs: List<TabEntity>) {
        clearTabs()
        insertTabs(tabs)
    }

    @Transaction
    open suspend fun replaceMessages(target: String, messages: List<MessageEntity>) {
        deleteMessages(target)
        insertMessages(messages)
    }
}
