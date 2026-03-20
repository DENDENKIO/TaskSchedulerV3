package com.example.taskschedulerv3.data.db.converter

import androidx.room.TypeConverter
import com.example.taskschedulerv3.data.model.RecurrencePattern
import com.example.taskschedulerv3.data.model.ScheduleType

class Converters {
    @TypeConverter
    fun fromScheduleType(value: ScheduleType): String = value.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)

    @TypeConverter
    fun fromRecurrencePattern(value: RecurrencePattern?): String? = value?.name

    @TypeConverter
    fun toRecurrencePattern(value: String?): RecurrencePattern? =
        value?.let { RecurrencePattern.valueOf(it) }
}
