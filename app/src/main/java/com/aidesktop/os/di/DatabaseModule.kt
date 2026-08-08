package com.aidesktop.os.di

import android.content.Context
import androidx.room.Room
import com.aidesktop.os.data.local.AppDatabase
import com.aidesktop.os.data.local.dao.AccountDao
import com.aidesktop.os.data.local.dao.AiActivityDao
import com.aidesktop.os.data.local.dao.BrowserDao
import com.aidesktop.os.data.local.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ai_desktop_os.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideBrowserDao(db: AppDatabase): BrowserDao = db.browserDao()

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideAiActivityDao(db: AppDatabase): AiActivityDao = db.aiActivityDao()
}
