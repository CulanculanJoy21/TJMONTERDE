package com.scms.app

import android.app.Application
import com.scms.app.api.RetrofitClient
// CRITICAL FIX: Missing package path reference utility layer import
import com.scms.app.utils.SessionManager

class SCMSApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Restore token on app start so API calls work immediately
        val session = SessionManager(this)
        if (session.isLoggedIn) {
            RetrofitClient.setToken(session.token)
        }
    }
}