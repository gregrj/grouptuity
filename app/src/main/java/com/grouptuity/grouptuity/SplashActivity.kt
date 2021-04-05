package com.grouptuity.grouptuity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class SplashActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immediately launch the main activity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}