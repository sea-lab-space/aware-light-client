package com.aware.phone.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Use the @Entity annotation to mark this class as a database table named "health_connect_data"
@Entity(tableName = "health_connect_data")
data class HealthConnectDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Primary key, auto-increment
    val deviceId: String,        // device ID
    val startTimestamp: Long,    // Data start time (milliseconds timestamp)
    val endTimestamp: Long,      // Data end time (milliseconds timestamp)
    val timestamp: Long,         // Data retrieval time
    val type: String,            // Data type (e.g., "step")
    val value: Double,           // Data value (e.g., step count)
    val isSync: Boolean          // Whether it has been synced to the server
)
