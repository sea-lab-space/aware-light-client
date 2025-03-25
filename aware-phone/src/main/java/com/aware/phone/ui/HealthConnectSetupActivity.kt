package com.aware.phone.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aware.Aware
import com.aware.Aware_Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import com.aware.phone.data.AppDatabase
import com.aware.phone.data.HealthConnectDataEntity
import com.aware.phone.data.StepSyncWorker
import java.util.concurrent.TimeUnit


class HealthConnectSetupActivity : AppCompatActivity() {

    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var statusText: TextView

    private val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(BodyWaterMassRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class),
        HealthPermission.getReadPermission(CervicalMucusRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(IntermenstrualBleedingRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(MenstruationFlowRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(OvulationTestRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SexualActivityRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(WheelchairPushesRecord::class)
    )


    private val permissionRequestLauncher =
        this.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(PERMISSIONS)) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
                statusText.text = "Permissions granted. Reading steps..."
                scheduleHourlyStepSync()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
                statusText.text = "Permissions denied. Please enable permissions manually."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.aware.phone.R.layout.activity_health_connect_setup)

        statusText = findViewById(com.aware.phone.R.id.health_status)
        statusText.text = "Checking Health Connect..."

        // Delay start to ensure the page is visible before requesting permissions
        Handler(Looper.getMainLooper()).postDelayed({
            checkHealthConnectAndRequest()
        }, 300)
    }

    private fun checkHealthConnectAndRequest() {
        val availabilityStatus = HealthConnectClient.getSdkStatus(this, "com.google.android.apps.healthdata")

        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            Toast.makeText(this, "Health Connect not available", Toast.LENGTH_LONG).show()
            statusText.text = "Health Connect is not available on this device."
            return
        }

        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val uriString = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", packageName)
                }
            )
            statusText.text = "Redirecting to update Health Connect..."
            return
        }

        healthConnectClient = HealthConnectClient.getOrCreate(this)

        CoroutineScope(Dispatchers.Main).launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (!granted.containsAll(PERMISSIONS)) {
                statusText.text = "Requesting permissions..."
                permissionRequestLauncher.launch(PERMISSIONS)
            } else {
                statusText.text = "Permissions already granted. Reading steps..."
                scheduleHourlyStepSync()
            }
        }
    }

    private fun scheduleHourlyStepSync() {
        // TODO: Xiaowen: Make it dynamic
        // Schedule a periodic work to sync steps every 15 minutes (minimum interval)
        val workRequest = PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StepSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }



}
