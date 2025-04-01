package com.aware.workers

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aware.Aware
import com.aware.Aware_Preferences
import com.aware.providers.Healthconnect_Provider
import com.aware.utils.HealthConnectRecordTypeList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class HealthConnectWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "HealthConnectWorker"
    private val prefs = appContext.getSharedPreferences("healthconnect", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val client = HealthConnectClient.getOrCreate(applicationContext)

            val preperiodDays = Aware.getSetting(applicationContext, Aware_Preferences.PREPERIOD_HEALTH_CONNECT)
                ?.toLongOrNull()?.coerceAtMost(30L) ?: 7L

            val end = Instant.now()
            val start = prefs.getString("last_read_time", null)?.let { Instant.parse(it) }
                ?: end.minus(Duration.ofDays(preperiodDays))

            prefs.edit().putString("last_read_time", end.toString()).apply()

            for (recordType in HealthConnectRecordTypeList.supportedRecordTypes) {
                try {
                    val response = client.readRecords(
                        ReadRecordsRequest(
                            recordType = recordType,
                            timeRangeFilter = TimeRangeFilter.between(start, end)
                        )
                    )

                    for (record in response.records) {
                        val rowData = ContentValues().apply {
                            put(Healthconnect_Provider.Healthconnect_Data.TIMESTAMP, System.currentTimeMillis())
                            put(Healthconnect_Provider.Healthconnect_Data.DEVICE_ID,
                                Aware.getSetting(applicationContext, Aware_Preferences.DEVICE_ID))
                            put(Healthconnect_Provider.Healthconnect_Data.TYPE, recordType.simpleName ?: "Unknown")
                            put(Healthconnect_Provider.Healthconnect_Data.VALUE, record.toString())
                        }
                        applicationContext.contentResolver.insert(
                            Healthconnect_Provider.Healthconnect_Data.CONTENT_URI,
                            rowData
                        )
                    }

                    Log.d(
                        TAG,
                        "Saved ${response.records.size} records for ${recordType.simpleName} from $start to $end"
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read ${recordType.simpleName}: ${e.message}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}")
            Result.retry()
        }
    }
}
