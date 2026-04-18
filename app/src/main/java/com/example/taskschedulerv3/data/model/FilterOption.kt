package com.example.taskschedulerv3.data.model

data class FilterOption(
    val completionStatus: CompletionFilter = CompletionFilter.ALL,
    val scheduleTypes: Set<ScheduleType> = emptySet(),
    val tagIds: Set<Int> = emptySet(),
    val notifyFilter: NotifyFilter = NotifyFilter.ALL
)

enum class CompletionFilter { ALL, INCOMPLETE, COMPLETE }
enum class NotifyFilter { ALL, ON_ONLY, OFF_ONLY }
