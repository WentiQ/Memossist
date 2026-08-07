package com.example.apptempleate

import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ConnectionsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove window title & hide action bar
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_connections)

        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            finishWithSmoothAnimation()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishWithSmoothAnimation()
    }

    private fun finishWithSmoothAnimation() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
