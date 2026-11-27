package ir.namak.sms.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thread_meta")
data class ThreadMetaEntity(
    @PrimaryKey val threadId: Long,
    val address: String,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val isSpam: Boolean = false,
    val isBlocked: Boolean = false,
    val manualCategory: String? = null,
    val preferredSimSubId: Int? = null
)

@Entity(tableName = "starred_messages")
data class StarredMessageEntity(
    @PrimaryKey val messageId: Long,
    val threadId: Long,
    val starredAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "classification_rules")
data class ClassificationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val matchType: String,
    val pattern: String,
    val targetCategory: String,
    val priority: Int = 0,
    val isEnabled: Boolean = true
)

@Entity(tableName = "scheduled_sms")
data class ScheduledSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val body: String,
    val subscriptionId: Int?,
    val scheduledTimeMillis: Long,
    val userConfirmed: Boolean = true,
    val status: String = "PENDING",
    val errorMessage: String? = null
)

@Entity(tableName = "message_reminders")
data class MessageReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val messageId: Long?,
    val address: String,
    val previewText: String,
    val remindAtMillis: Long,
    val isTriggered: Boolean = false
)
