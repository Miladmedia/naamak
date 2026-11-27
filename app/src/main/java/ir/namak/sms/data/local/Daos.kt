package ir.namak.sms.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadMetaDao {
    @Query("SELECT * FROM thread_meta")
    fun observeAllMeta(): Flow<List<ThreadMetaEntity>>

    @Query("SELECT * FROM thread_meta WHERE threadId = :threadId")
    suspend fun getMeta(threadId: Long): ThreadMetaEntity?

    @Upsert
    suspend fun upsert(meta: ThreadMetaEntity)
}

@Dao
interface StarredMessageDao {
    @Query("SELECT messageId FROM starred_messages")
    fun observeStarredIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun star(entity: StarredMessageEntity)

    @Query("DELETE FROM starred_messages WHERE messageId = :messageId")
    suspend fun unstar(messageId: Long)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM classification_rules ORDER BY priority DESC, id ASC")
    fun observeRules(): Flow<List<ClassificationRuleEntity>>

    @Query("SELECT * FROM classification_rules WHERE isEnabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getEnabledRules(): List<ClassificationRuleEntity>

    @Upsert
    suspend fun upsertRule(rule: ClassificationRuleEntity)

    @Delete
    suspend fun deleteRule(rule: ClassificationRuleEntity)
}

@Dao
interface ScheduledSmsDao {
    @Query("SELECT * FROM scheduled_sms ORDER BY scheduledTimeMillis ASC")
    fun observeScheduled(): Flow<List<ScheduledSmsEntity>>

    @Query("SELECT * FROM scheduled_sms WHERE id = :id")
    suspend fun getById(id: Long): ScheduledSmsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sms: ScheduledSmsEntity): Long

    @Update
    suspend fun update(sms: ScheduledSmsEntity)

    @Delete
    suspend fun delete(sms: ScheduledSmsEntity)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM message_reminders WHERE isTriggered = 0 ORDER BY remindAtMillis ASC")
    fun observeActiveReminders(): Flow<List<MessageReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: MessageReminderEntity): Long

    @Query("UPDATE message_reminders SET isTriggered = 1 WHERE id = :id")
    suspend fun markTriggered(id: Long)
}
