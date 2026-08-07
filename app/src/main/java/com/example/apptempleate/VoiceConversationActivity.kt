package com.example.apptempleate

import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VoiceConversationActivity : AppCompatActivity() {

    private lateinit var btnEndCall: ImageButton
    private lateinit var btnMuteMic: ImageButton
    private lateinit var btnSpeakerToggle: ImageButton
    private lateinit var tvVoiceStatus: TextView
    private lateinit var leafOrbView: LeafOrbView

    private var isMuted = false
    private var isSpeakerOn = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove window title & hide action bar header completely
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_voice_conversation)

        btnEndCall = findViewById(R.id.btnEndCall)
        btnMuteMic = findViewById(R.id.btnMuteMic)
        btnSpeakerToggle = findViewById(R.id.btnSpeakerToggle)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        leafOrbView = findViewById(R.id.leafOrbView)

        btnEndCall.setOnClickListener {
            Toast.makeText(this, "Voice conversation ended", Toast.LENGTH_SHORT).show()
            finishWithSmoothAnimation()
        }

        btnMuteMic.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                btnMuteMic.setImageResource(R.drawable.ic_mic_off)
                tvVoiceStatus.text = "Microphone Muted"
                Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show()
            } else {
                btnMuteMic.setImageResource(R.drawable.ic_mic)
                tvVoiceStatus.text = "Listening..."
                Toast.makeText(this, "Unmuted", Toast.LENGTH_SHORT).show()
            }
        }

        btnSpeakerToggle.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            val message = if (isSpeakerOn) "Speaker Phone On" else "Earpiece On"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun finishWithSmoothAnimation() {
        finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
