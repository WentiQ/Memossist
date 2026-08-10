package com.example.apptempleate

import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class DeviceHelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_device_help)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnNavAppBattery: AppCompatButton = findViewById(R.id.btnNavAppBattery)
        val btnNavSleepStandby: AppCompatButton = findViewById(R.id.btnNavSleepStandby)
        val btnNavAutoLaunch: AppCompatButton = findViewById(R.id.btnNavAutoLaunch)
        val btnRequestExemption: AppCompatButton = findViewById(R.id.btnRequestExemption)

        btnBack.setOnClickListener {
            finish()
        }

        // 1. Settings -> Apps -> App management -> Memossist -> Battery usage
        btnNavAppBattery.setOnClickListener {
            BatteryOptimizationHelper.openAppBatterySettings(this)
        }

        // 2. Settings -> Battery -> More settings -> Sleep standby optimization
        btnNavSleepStandby.setOnClickListener {
            BatteryOptimizationHelper.openSleepStandbySettings(this)
        }

        // 3. Settings -> Apps -> Auto launch -> Memossist
        btnNavAutoLaunch.setOnClickListener {
            BatteryOptimizationHelper.openAutoLaunchSettings(this)
        }

        // 4. Direct battery exemption request
        btnRequestExemption.setOnClickListener {
            BatteryOptimizationHelper.requestExemptionIfNeeded(this)
        }
    }
}
