package com.example.taskschedulerv3.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.taskschedulerv3.data.db.converter.Converters
import com.example.taskschedulerv3.data.model.*

@Database(
    entities = [
        Task::class,
        Tag::class,
        TaskTagCrossRef::class,
        TaskRelation::class,
        PhotoMemo::class,
        TaskCompletion::class
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_scheduler.db"
                ).build().also { INSTANCE = it }
            }
    }
}
