package com.aidesktop.os.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aidesktop.os.data.local.dao.AccountDao
import com.aidesktop.os.data.local.dao.AiActivityDao
import com.aidesktop.os.data.local.dao.BrowserDao
import com.aidesktop.os.data.local.dao.ProjectDao
import com.aidesktop.os.data.local.entity.AccountEntity
import com.aidesktop.os.data.local.entity.AiActivityEntity
import com.aidesktop.os.data.local.entity.BookmarkEntity
import com.aidesktop.os.data.local.entity.HistoryEntity
import com.aidesktop.os.data.local.entity.ProjectEntity
import com.aidesktop.os.data.local.entity.ProjectFileEntity
import com.aidesktop.os.data.local.entity.ProjectTaskEntity

@Database(
    entities = [
        ProjectEntity::class,
        ProjectTaskEntity::class,
        ProjectFileEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        AccountEntity::class,
        AiActivityEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun browserDao(): BrowserDao
    abstract fun accountDao(): AccountDao
    abstract fun aiActivityDao(): AiActivityDao
}
