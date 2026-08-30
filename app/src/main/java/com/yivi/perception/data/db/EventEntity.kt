package com.yivi.perception.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,        // "行程" / "闹钟"
    val title: String,
    val note: String = "",
    val time: Long = 0L,         // 执行时间 epoch millis
    val remind: Boolean = false, // 行程: 是否提醒
    val repeatDays: String = "", // 闹钟: 重复星期，如 "1,3,5", 空表示单次
    val createdAt: Long = System.currentTimeMillis()
)
