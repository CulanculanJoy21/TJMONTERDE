package com.scms.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.ActivityLoginBinding
import com.scms.app.models.LoginRequest
import com.scms.app.ui.dashboard.MainActivity
import com.scms.app.utils.Resource
import com.scms.app.utils.SessionManager
import com.scms.app.utils.safeApiCall
import com.scms.app.utils.toast
import kotlinx.coroutines.launch

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class LoginViewModel : ViewModel() {
    val loginState = MutableLiveData<Resource<Unit>>()

    fun login(email: String, password: String, session: SessionManager) {
        viewModelScope.launch {
            loginState.value = Resource.Loading()
            val result = safeApiCall {
                RetrofitClient.instance.login(LoginRequest(email, password))
            }
            when (result) {
                is Resource.Success -> {
                    session.token = result.data.token
                    session.user  = result.data.user
                    RetrofitClient.setToken(result.data.token)
                    loginState.value = Resource.Success(Unit)
                }
                is Resource.Error   -> loginState.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }
}

// ─── ACTIVITY ─────────────────────────────────────────────────────────────────

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Auto-login setup redirection layer
        if (session.isLoggedIn) {
            RetrofitClient.setToken(session.token)
            goToMain()
            return
        }

        // 🛠️ FIXED: Target the updated button element layout identifier token (btnSignIn)
        binding.btnSignIn.setOnClickListener {
            val email    = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                toast("Please enter email and password")
                return@setOnClickListener
            }

            viewModel.login(email, password, session)
        }

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is Resource.Loading -> {
                    // 🛠️ FIXED: Clean text toggle changes instead of crashing on a missing layout bar view
                    binding.btnSignIn.text = "Signing in..."
                    binding.btnSignIn.isEnabled = false
                }
                is Resource.Success -> {
                    binding.btnSignIn.text = "Continue"
                    binding.btnSignIn.isEnabled = true
                    goToMain()
                }
                is Resource.Error -> {
                    binding.btnSignIn.text = "Continue"
                    binding.btnSignIn.isEnabled = true
                    // Shows the comprehensive, clean security error toast messages
                    toast(state.message)
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}