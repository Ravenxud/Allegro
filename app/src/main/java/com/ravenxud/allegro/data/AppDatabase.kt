package com.ravenxud.allegro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ravenxud.allegro.data.dao.BookmarkDao
import com.ravenxud.allegro.data.dao.ClosedTabDao
import com.ravenxud.allegro.data.dao.DownloadDao
import com.ravenxud.allegro.data.dao.HistoryDao
import com.ravenxud.allegro.data.dao.TabDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ravenxud.allegro.data.entity.BookmarkEntity
import com.ravenxud.allegro.data.entity.BookmarkFolderEntity
import com.ravenxud.allegro.data.entity.ClosedTabEntity
import com.ravenxud.allegro.data.entity.DownloadEntity
import com.ravenxud.allegro.data.entity.HistoryEntity
import com.ravenxud.allegro.data.entity.TabEntity

/** 单库：tabs / closed_tabs / history / bookmarks / downloads。 */
@Database(
    entities = [
        TabEntity::class,
        ClosedTabEntity::class,
        HistoryEntity::class,
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao
    abstract fun closedTabDao(): ClosedTabDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

    /** v1 -> v2：书签增加 folderId/position，新增 bookmark_folders 表。 */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN folderId TEXT")
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmark_folders` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "simple_browser.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
