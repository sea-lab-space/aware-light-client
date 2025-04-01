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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.aware.utils.HealthConnectRecordTypeList


class HealthConnectSetupActivity : AppCompatActivity() {

    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var statusText: TextView

    private val PERMISSIONS = HealthConnectRecordTypeList.supportedRecordTypes
        .map { HealthPermission.getReadPermission(it) }
        .toSet()

    private val permissionRequestLauncher =
        this.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(PERMISSIONS)) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
                statusText.text = "Permissions granted. Reading records..."
                com.aware.Aware.startHealthConnect(applicationContext)
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
                statusText.text = "Permissions already granted. Reading records..."
                com.aware.Aware.startHealthConnect(applicationContext)
            }
        }
    }


}
