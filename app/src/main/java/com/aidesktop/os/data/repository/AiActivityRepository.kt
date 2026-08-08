package com.aidesktop.os.data.repository

import com.aidesktop.os.data.local.dao.AiActivityDao
import com.aidesktop.os.data.local.entity.AiActivityEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiActivityRepository @Inject constructor(private val dao: AiActivityDao) {

    /** Logs one real, already-succeeded AI action. Never call this for a failed/ambiguous result. */
    suspend fun log(actionType: String, query: String, resultLabel: String) {
        dao.insert(AiActivityEntity(actionType = actionType, query = query, resultLabel = resultLabel))
    }

    /**
     * Builds a short, plain-text summary of real recent activity for the model's
     * context — every line traces back to an actual logged row, nothing invented
     * or extrapolated beyond simple counting of what was literally asked before.
     * Returns null when there's no history yet, so the prompt doesn't claim a
     * pattern that doesn't exist.
     */
    suspend fun recentActivitySummary(): String? {
        val recent = dao.recent(limit = 30)
        if (recent.isEmpty()) return null

        val youtubePlays = recent.filter { it.actionType == "youtube_play" }.take(5)
        val whatsappSends = recent.filter { it.actionType == "whatsapp_send_message" }.take(5)
        val newsSearches = recent.filter { it.actionType == "news_search" }.take(5)

        val lines = buildList {
            if (youtubePlays.isNotEmpty()) {
                add("Recently played on YouTube (most recent first): " +
                    youtubePlays.joinToString("; ") { "\"${it.query}\" -> played \"${it.resultLabel}\"" })
            }
            if (whatsappSends.isNotEmpty()) {
                add("Recently messaged on WhatsApp (most recent first): " +
                    whatsappSends.joinToString("; ") { it.resultLabel })
            }
            if (newsSearches.isNotEmpty()) {
                add("Recently searched news topics: " + newsSearches.joinToString(", ") { it.query })
            }
        }
        if (lines.isEmpty()) return null
        return lines.joinToString("\n")
    }
}
