package com.aware.phone.data

import androidx.room.*

@Dao
interface HealthConnectDataDao {

    // Insert a single record
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: HealthConnectDataEntity)

    // Insert multiple records
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dataList: List<HealthConnectDataEntity>)

    // Get all data
    @Query("SELECT * FROM health_connect_data")
    suspend fun getAll(): List<HealthConnectDataEntity>

    // Get unsynced data
    @Query("SELECT * FROM health_connect_data WHERE isSync = 0")
    suspend fun getUnsynced(): List<HealthConnectDataEntity>

    // Update data sync status
    @Update
    suspend fun update(data: HealthConnectDataEntity)

    // Clear all data (optional)
    @Query("DELETE FROM health_connect_data")
    suspend fun deleteAll()

    @Query("UPDATE health_connect_data SET isSync = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("DELETE FROM health_connect_data WHERE isSync = 1 AND timestamp < :cutoffTime")
    suspend fun deleteOldSyncedData(cutoffTime: Long)

    // Delete all data that is marked as synced
    @Query("DELETE FROM health_connect_data WHERE isSync = 1")
    suspend fun deleteAllSyncedData()

}
