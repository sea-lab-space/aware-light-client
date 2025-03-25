package com.aware.phone.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aware.Aware
import com.aware.Aware_Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class GeneralSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val PREFS_NAME = "health_connect_prefs"
        private const val LAST_SYNC_TIME_KEY = "last_sync_time"
    }

    override suspend fun doWork(): Result {
        Log.d("GeneralSyncWorker", "Worker started at ${System.currentTimeMillis()}")

        val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)
        val sharedPrefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSyncTimeMillis = sharedPrefs.getLong(LAST_SYNC_TIME_KEY, -1L)

        val now = Instant.now()
        val startTime = if (lastSyncTimeMillis != -1L) {
            Instant.ofEpochMilli(lastSyncTimeMillis)
        } else {
            // TODO: Xiaowen: Make pre-period dynamic from config
            // Default to past 5 days on first run
            now.minus(Duration.ofDays(5))
        }

        Log.d("GeneralSyncWorker", "Reading from $startTime to $now")

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.healthConnectDataDao()
        val deviceId = Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID) ?: "unknown"

        var totalCount = 0

        try {
            for (recordType in HealthConnectRecordList.supportedRecordTypes) {
                var pageToken: String? = null
                do {
                    val response = withContext(Dispatchers.IO) {
                        healthConnectClient.readRecords(
                            ReadRecordsRequest(
                                recordType = recordType,
                                timeRangeFilter = TimeRangeFilter.between(startTime, now),
                                pageToken = pageToken
                            )
                        )
                    }

                    Log.d("GeneralSyncWorker", "Fetched ${response.records.size} records for ${recordType.simpleName}")

                    response.records.forEach { record ->
                        val entity = HealthConnectDataEntity(
                            deviceId = deviceId,
                            timestamp = System.currentTimeMillis(),
                            type = recordType.simpleName ?: "unknown",
                            value = record.toString().take(1000), // Limit to 1000 chars to avoid oversized strings
                            isSync = false
                        )
                        withContext(Dispatchers.IO) {
                            dao.insert(entity)
                        }
                    }

                    totalCount += response.records.size
                    pageToken = response.pageToken
                } while (pageToken != null)
            }

            Log.d("GeneralSyncWorker", "Total records inserted: $totalCount")
            sharedPrefs.edit().putLong(LAST_SYNC_TIME_KEY, now.toEpochMilli()).apply()
            Log.d("GeneralSyncWorker", "Updated last sync time to $now")

            uploadUnsyncedData()
            return Result.success()

        } catch (e: Exception) {
            Log.e("GeneralSyncWorker", "Error during sync", e)
            return Result.retry()
        }
    }

    // TO Simplify the code, frequency of uploading data is set to be the same as reading data
    private suspend fun uploadUnsyncedData() {
        val dao = AppDatabase.getDatabase(applicationContext).healthConnectDataDao()
        val unsynced = withContext(Dispatchers.IO) { dao.getUnsynced() }

        if (unsynced.isEmpty()) {
            Log.d("GeneralSyncWorker", "No unsynced records to upload.")
            return
        }

        val backendHost = Aware.getSetting(applicationContext, "database_host") ?: return
        val port = "5000"
        val url = "http://$backendHost:$port/api/sync"

        val jsonArray = JSONArray()
        unsynced.forEach { data ->
            val obj = JSONObject()
            obj.put("timestamp", data.timestamp)
            obj.put("type", data.type)
            obj.put("value", data.value)
            obj.put("deviceId", data.deviceId)
            jsonArray.put(obj)
        }

        try {
            val requestBody = jsonArray.toString()
            val urlObj = URL(url)
            with(urlObj.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true

                outputStream.use { os ->
                    os.write(requestBody.toByteArray())
                    os.flush()
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("GeneralSyncWorker", "Upload success: ${unsynced.size} records.")
                    withContext(Dispatchers.IO) {
                        unsynced.forEach { dao.markAsSynced(it.id) }
                    }

                    cleanOldSyncedData()
                } else {
                    Log.e("GeneralSyncWorker", "Upload failed with code $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e("GeneralSyncWorker", "Upload failed: ${e.message}", e)
        }
    }

    private suspend fun cleanOldSyncedData() {
        val frequency = Aware.getSetting(applicationContext, Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA)?.toIntOrNull() ?: 0

        val cutoffTime = when (frequency) {
            1 -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)    // weekly
            2 -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)   // monthly
            3 -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)    // daily
            4 -> Long.MAX_VALUE                                            // always (delete all synced data)
            else -> null                                                   // never or invalid value
        }

        val dao = AppDatabase.getDatabase(applicationContext).healthConnectDataDao()

        if (cutoffTime != null) {
            withContext(Dispatchers.IO) {
                if (frequency == 4) {
                    dao.deleteAllSyncedData()
                    Log.d("GeneralSyncWorker", "All synced data deleted (clean=always).")
                } else {
                    dao.deleteOldSyncedData(cutoffTime)
                    Log.d("GeneralSyncWorker", "Deleted synced data older than $cutoffTime (clean=$frequency).")
                }
            }
        } else {
            Log.d("GeneralSyncWorker", "Skip cleaning old data (clean=$frequency).")
        }
    }

}
