package com.scms.app.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.ActivityLoginBinding
import com.scms.app.models.LoginRequest
import com.scms.app.utils.Resource
import com.scms.app.utils.SessionManager
import com.scms.app.utils.hide
import com.scms.app.utils.safeApiCall
import com.scms.app.utils.show
import com.scms.app.utils.toast
import kotlinx.coroutines.launch
import androidx.lifecycle.MutableLiveData
import com.scms.app.ui.dashboard.MainActivity

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
                    val data = result.data
                    session.token = data.token
                    session.user  = data.user
                    RetrofitClient.setToken(data.token)
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

        // ⚠️ FIXED: Auto-login logic removed from here!
        // It is now handled cleanly inside SplashActivity.

        binding.btnLogin.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                toast("Please enter email and password")
                return@setOnClickListener
            }

            viewModel.login(email, password, session)
        }

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.show()
                    binding.btnLogin.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.hide()
                    binding.btnLogin.isEnabled = true
                    goToMain()
                }
                is Resource.Error -> {
                    binding.progressBar.hide()
                    binding.btnLogin.isEnabled = true
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