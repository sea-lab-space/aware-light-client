package com.aware

import android.content.ContentResolver
import android.content.Intent
import android.content.SyncRequest
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aware.providers.Healthconnect_Provider
import com.aware.utils.Aware_Sensor
import java.util.concurrent.TimeUnit

class Healthconnect : Aware_Sensor() {


    override fun onCreate() {
        super.onCreate()
        AUTHORITY = Healthconnect_Provider.getAuthority(this)
        if (Aware.DEBUG) Log.d(TAG, "Healthconnect service created")

        val status = Aware.getSetting(this, Aware_Preferences.STATUS_HEALTH_CONNECT)
        val frequency = Aware.getSetting(this, Aware_Preferences.FREQUENCY_HEALTH_CONNECT)
        val preperiod = Aware.getSetting(this, Aware_Preferences.PREPERIOD_HEALTH_CONNECT)
        if (Aware.DEBUG) Log.d(TAG, "STATUS_HEALTH_CONNECT = $status")
        if (Aware.DEBUG) Log.d(TAG, "FREQUENCY_HEALTH_CONNECT = $frequency")
        if (Aware.DEBUG) Log.d(TAG, "PREPERIOD_HEALTH_CONNECT = $preperiod")

        // start WorkManager periodic task
        val freqHours = frequency?.toLongOrNull() ?: 1L
        val request = PeriodicWorkRequestBuilder<com.aware.workers.HealthConnectWorker>(
            freqHours, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HealthConnectWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        // Enable sync (e.g., if Study mode is enabled)
        if (Aware.isStudy(this)) {
            ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), AUTHORITY, true)
            val syncFreq = Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)
                ?.toLongOrNull()?.times(60) ?: 900L
            val extras = Bundle()
            val syncRequest = SyncRequest.Builder()
                .syncPeriodic(syncFreq, syncFreq / 3)
                .setSyncAdapter(Aware.getAWAREAccount(this), AUTHORITY)
                .setExtras(extras)
                .build()
            ContentResolver.requestSync(syncRequest)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Aware.DEBUG) Log.d(TAG, "Healthconnect service terminated")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
