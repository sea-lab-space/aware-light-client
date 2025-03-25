package com.aware.phone.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
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
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.time.Duration
import java.time.Instant

class StepSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val PREFS_NAME = "health_connect_prefs"
        private const val LAST_SYNC_TIME_KEY = "last_sync_time"
    }

    override suspend fun doWork(): Result {
        Log.d("StepSyncWorker", "Worker started at ${System.currentTimeMillis()}")

        val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)

        val sharedPrefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSyncTimeMillis = sharedPrefs.getLong(LAST_SYNC_TIME_KEY, -1L)

        val now = Instant.now()
        val startTime = if (lastSyncTimeMillis != -1L) {
            Instant.ofEpochMilli(lastSyncTimeMillis)
        } else {
            // TODO: Xiaowen: Make it dynamic
            // By default, read the past 5 days on the first run
            now.minus(Duration.ofDays(5))
        }

        Log.d("StepSyncWorker", "Reading from $startTime to $now")

        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )

        return try {
            val response = withContext(Dispatchers.IO) {
                healthConnectClient.readRecords(request)
            }

            Log.d("StepSyncWorker", "Fetched ${response.records.size} step records.")

            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.healthConnectDataDao()
            val deviceId = Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID) ?: "unknown"


            response.records.forEach { record ->
                val entity = HealthConnectDataEntity(
                    deviceId = deviceId,
                    startTimestamp = record.startTime.toEpochMilli(),
                    endTimestamp = record.endTime.toEpochMilli(),
                    timestamp = System.currentTimeMillis(),
                    type = "step",
                    value = record.count.toDouble(),
                    isSync = false
                )
                withContext(Dispatchers.IO) {
                    dao.insert(entity)
                }
            }

            sharedPrefs.edit().putLong(LAST_SYNC_TIME_KEY, now.toEpochMilli()).apply()
            Log.d("StepSyncWorker", "Updated last sync time to $now")

            uploadUnsyncedData()

            Result.success()
        } catch (e: Exception) {
            Log.e("StepSyncWorker", "Error during step sync", e)
            Result.retry()
        }
    }

    private suspend fun uploadUnsyncedData() {
        val dao = AppDatabase.getDatabase(applicationContext).healthConnectDataDao()
        val unsynced = withContext(Dispatchers.IO) { dao.getUnsynced() }

        if (unsynced.isEmpty()) {
            Log.d("StepSyncWorker", "No unsynced records to upload.")
            return
        }

        // TODO: Xiaowen: Make it decent
        val backendHost = Aware.getSetting(applicationContext, "database_host") ?: return
        val port = "5000"
        val url = "http://$backendHost:$port/api/sync"

        val jsonArray = JSONArray()
        // TODO: Xiaowen: remove isSync
        unsynced.forEach { data ->
            val obj = JSONObject()
            obj.put("startTimestamp", data.startTimestamp)
            obj.put("endTimestamp", data.endTimestamp)
            obj.put("timestamp", data.timestamp)
            obj.put("type", data.type)
            obj.put("value", data.value)
            obj.put("isSync", data.isSync)
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
                    Log.d("StepSyncWorker", "Upload success: ${unsynced.size} records.")
                    withContext(Dispatchers.IO) {
                        unsynced.forEach { dao.markAsSynced(it.id) }
                    }
                } else {
                    Log.e("StepSyncWorker", "Upload failed with code $responseCode")
                }
            }
        } catch (e: Exception) {
            Log.e("StepSyncWorker", "Upload failed: ${e.message}", e)
        }
    }

}
