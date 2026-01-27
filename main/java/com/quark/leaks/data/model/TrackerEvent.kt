package com.quark.leaks.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "tracker_events")
data class TrackerEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val domain: String? = null,
    val ipAddress: String,
    val packetType: String,
    val blocked: Boolean = true,
    val appName: String? = null,
    val appPackage: String? = null
) {
    val timeFormatted: String
        get() = Date(timestamp).toString()
}