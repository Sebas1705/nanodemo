package com.example.geminifnanodemo.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class NanoDemoDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}
