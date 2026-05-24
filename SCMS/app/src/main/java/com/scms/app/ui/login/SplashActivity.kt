package com.scms.app.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.ActivitySplashBinding
import com.scms.app.ui.dashboard.MainActivity
import com.scms.app.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Run session token validation routine seamlessly in the background
        lifecycleScope.launch {
            // Give the user a moment to enjoy your new branding asset design (2 seconds)
            delay(2000)

            if (session.isLoggedIn) {
                // Securely forward authentication tokens down to the API wrapper instance
                RetrofitClient.setToken(session.token)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }

            // Pop this activity off the history tree so pressing "Back" doesn't return here
            finish()
        }
    }
}