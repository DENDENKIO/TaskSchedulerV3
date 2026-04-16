package com.example.taskschedulerv3.data.model

enum class RecurrencePattern {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY_DATE,
    MONTHLY_WEEK,
    YEARLY,
    // 第7弾: 追加
    NONE,
    EVERY_N_DAYS,    // N日ごと (recurrenceDays="N")
    WEEKLY_MULTI,    // 曜日複数 (recurrenceDays="1,3,5")
    MONTHLY_DATES    // 毎月日付複数 (recurrenceDays="5,20,28")
}
