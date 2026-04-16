package com.example.taskschedulerv3.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.taskschedulerv3.data.db.converter.Converters
import com.example.taskschedulerv3.data.model.*

@Database(
    entities = [
        Task::class,
        Tag::class,
        TaskTagCrossRef::class,
        TaskRelation::class,
        PhotoMemo::class,
        TaskCompletion::class,
        PhotoTagCrossRef::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun taskTagCrossRefDao(): TaskTagCrossRefDao
    abstract fun taskRelationDao(): TaskRelationDao
    abstract fun photoMemoDao(): PhotoMemoDao
    abstract fun taskCompletionDao(): TaskCompletionDao
    abstract fun photoTagCrossRefDao(): PhotoTagCrossRefDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration: v1 -> v2: add photo_tag_cross_ref table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `photo_tag_cross_ref` (
                        `photoId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`photoId`, `tagId`),
                        FOREIGN KEY(`photoId`) REFERENCES `photo_memos`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_tag_cross_ref_tagId` ON `photo_tag_cross_ref` (`tagId`)")
            }
        }

        // Migration: v2 -> v3: add isIndefinite column to tasks
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE tasks ADD COLUMN isIndefinite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migration: v3 -> v4: add ocrText and sourceType to photo_memos
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photo_memos ADD COLUMN ocrText TEXT")
                database.execSQL("ALTER TABLE photo_memos ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'CAMERA'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN progress INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_scheduler.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
    }
}
