package com.aidesktop.os.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One real, completed AI action, logged only after AiToolExecutor confirmed
 * it actually succeeded (never logged on a failed/ambiguous/not-found
 * result). This is the entire, honest basis for "preference learning" —
 * there is no separate guessed profile; the AI is only ever shown a summary
 * built directly from these real rows.
 */
@Entity(tableName = "ai_activity")
data class AiActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // e.g. "youtube_play", "whatsapp_send_message", "news_search"
    val query: String,      // what the user actually asked for, e.g. "sad songs"
    val resultLabel: String, // the real matched result, e.g. the video title that actually played
    val occurredAt: Long = System.currentTimeMillis()
)
