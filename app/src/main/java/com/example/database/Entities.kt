package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Long = 0L,
    val sender: String, // "user" or "ai"
    val message: String,
    val imageBase64: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: Int = 1,
    val friendshipScore: Int = 0, // 0 to 100
    val username: String = "Insignificante",
    val insultsCount: Int = 0,
    val coffeesGiven: Int = 0,
    val apologiesCount: Int = 0
)
