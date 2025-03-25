// PermissionsRationaleActivity.kt
package com.aware.phone.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class PermissionsRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        textView.text = "This app uses Health Connect to access your steps data in order to analyze your health trends. We do not share your data with any third party."
        textView.setPadding(32, 32, 32, 32)
        setContentView(textView)
    }
}
