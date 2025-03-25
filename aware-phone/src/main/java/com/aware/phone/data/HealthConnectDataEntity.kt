package com.aware.phone.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Use the @Entity annotation to mark this class as a database table named "health_connect_data"
@Entity(tableName = "health_connect_data")
data class HealthConnectDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Primary key, auto-increment
    val deviceId: String,        // device ID
    val timestamp: Long,         // Data retrieval time
    val type: String,            // Data type (e.g., "step", "heart_rate")
    val value: String,           // Raw record.toString() content
    val isSync: Boolean          // Whether it has been synced to the server
)
