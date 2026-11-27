package ir.namak.sms.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ThreadMetaEntity::class,
        StarredMessageEntity::class,
        ClassificationRuleEntity::class,
        ScheduledSmsEntity::class,
        MessageReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NamakDatabase : RoomDatabase() {
    abstract fun threadMetaDao(): ThreadMetaDao
    abstract fun starredMessageDao(): StarredMessageDao
    abstract fun ruleDao(): RuleDao
    abstract fun scheduledSmsDao(): ScheduledSmsDao
    abstract fun reminderDao(): ReminderDao
}
