package com.scms.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.scms.app.R
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.ActivityMainBinding
import com.scms.app.ui.login.LoginActivity
import com.scms.app.utils.Resource
import com.scms.app.utils.SessionManager
import com.scms.app.utils.safeApiCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfig: AppBarConfiguration
    lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 🛠️ FIXED: Added the missing comma right before the new approvals menu ID entry token
        appBarConfig = AppBarConfiguration(
            setOf(
                R.id.nav_dashboard,
                R.id.nav_inventory,
                R.id.nav_orders,
                R.id.nav_deliveries,
                R.id.nav_suppliers,
                R.id.nav_approvals
            )
        )

        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNavView.setupWithNavController(navController)

        // 🛠️ SECURITY VISIBILITY: Enforce role-based tab rendering directly on the BottomNav view structure
        val role = session.user?.role?.lowercase() ?: "field_personnel"

        if (role != "admin") {
            binding.bottomNavView.menu.findItem(R.id.nav_approvals)?.isVisible = false
        }

        // Hide suppliers for field personnel / drivers
        if (!session.isAdminOrManager) {
            binding.bottomNavView.menu.findItem(R.id.nav_suppliers)?.isVisible = false
        }

        startNotificationPolling()
    }

    private fun startNotificationPolling() {
        lifecycleScope.launch {
            while (isActive) {
                // Terminate background threads instantly if logging out or cleared
                if (!session.isLoggedIn) {
                    break
                }

                try {
                    val result = safeApiCall { RetrofitClient.instance.getNotifications() }
                    if (result is Resource.Success) {
                        val unread = result.data.data.count { !it.isRead }
                        runOnUiThread { updateNotificationBadge(unread) }
                    }
                } catch (_: Exception) {}

                delay(30_000) // Poll safely every 30 seconds
            }
        }
    }

    fun updateNotificationBadge(count: Int) {
        val badge = binding.bottomNavView.getOrCreateBadge(R.id.nav_dashboard)
        badge.isVisible = count > 0
        if (count > 0) badge.number = count
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) { logout(); return true }
        return super.onOptionsItemSelected(item)
    }

    fun logout() {
        lifecycleScope.launch { safeApiCall { RetrofitClient.instance.logout() } }
        session.clear()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}