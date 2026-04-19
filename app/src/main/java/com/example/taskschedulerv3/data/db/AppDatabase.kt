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
        PhotoTagCrossRef::class,
        QuickDraftTask::class,
        RoadmapStep::class
    ],
    version = 9,
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
    abstract fun quickDraftTaskDao(): QuickDraftTaskDao
    abstract fun roadmapStepDao(): RoadmapStepDao

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quick_draft_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `photoPath` TEXT,
                        `ocrText` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'DRAFT'
                    )
                """.trimIndent())
            }
        }

        // Migration: v6 -> v7: add tagIds column to quick_draft_tasks
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE quick_draft_tasks ADD COLUMN tagIds TEXT")
            }
        }

        // Migration: v7 -> v8: add parentTaskId, roadmap fields to tasks and create roadmap_steps table
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN parentTaskId INTEGER")
                database.execSQL("ALTER TABLE tasks ADD COLUMN roadmapEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tasks ADD COLUMN activeRoadmapStepId INTEGER")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roadmap_steps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `date` TEXT,
                        `sortOrder` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `completedAt` INTEGER,
                        `notificationEnabled` INTEGER NOT NULL DEFAULT 1,
                        `notificationRequestCode` INTEGER
                    )
                """.trimIndent())
            }
        }

        // Migration: v8 -> v9: add date/time columns to quick_draft_tasks
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE quick_draft_tasks ADD COLUMN startDate TEXT")
                database.execSQL("ALTER TABLE quick_draft_tasks ADD COLUMN startTime TEXT")
                database.execSQL("ALTER TABLE quick_draft_tasks ADD COLUMN endTime TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_scheduler.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build().also { INSTANCE = it }
            }
    }
}
