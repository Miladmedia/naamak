package ir.namak.sms.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.namak.sms.data.local.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NamakDatabase =
        Room.databaseBuilder(context, NamakDatabase::class.java, "namak_sms.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideThreadMetaDao(db: NamakDatabase): ThreadMetaDao = db.threadMetaDao()
    @Provides fun provideStarredDao(db: NamakDatabase): StarredMessageDao = db.starredMessageDao()
    @Provides fun provideRuleDao(db: NamakDatabase): RuleDao = db.ruleDao()
    @Provides fun provideScheduledDao(db: NamakDatabase): ScheduledSmsDao = db.scheduledSmsDao()
    @Provides fun provideReminderDao(db: NamakDatabase): ReminderDao = db.reminderDao()
}
