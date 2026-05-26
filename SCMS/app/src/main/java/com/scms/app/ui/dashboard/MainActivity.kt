package com.scms.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
    private var currentUnreadCount: Int = 0

    // Instantiate our decoupled inner tracking listener
    private val appBackgroundObserver = AppBackgroundObserver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        setSupportActionBar(binding.toolbar)

        // 🛠️ Attach the decoupled inner helper class to the global process life map
        ProcessLifecycleOwner.get().lifecycle.addObserver(appBackgroundObserver)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val role = session.user?.role?.lowercase() ?: "field_personnel"
        val menu = binding.bottomNavView.menu

        val topLevelDestinations = if (role == "field_personnel") {
            setOf(R.id.nav_deliveries)
        } else {
            setOf(
                R.id.nav_dashboard,
                R.id.nav_inventory,
                R.id.nav_orders,
                R.id.nav_deliveries,
                R.id.nav_suppliers,
                R.id.nav_approvals
            )
        }

        appBarConfig = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNavView.setupWithNavController(navController)

        if (role == "field_personnel") {
            menu.findItem(R.id.nav_dashboard)?.isVisible = false
            menu.findItem(R.id.nav_inventory)?.isVisible = false
            menu.findItem(R.id.nav_orders)?.isVisible = false
            menu.findItem(R.id.nav_suppliers)?.isVisible = false
            menu.findItem(R.id.nav_approvals)?.isVisible = false

            navController.navigate(R.id.nav_deliveries)
        } else {
            if (role != "admin") {
                menu.findItem(R.id.nav_approvals)?.isVisible = false
            }
            if (!session.isAdminOrManager) {
                menu.findItem(R.id.nav_suppliers)?.isVisible = false
            }
        }

        if (role != "field_personnel") {
            startNotificationPolling()
        }
    }

    // ─── 🔏 DECOUPLED LIFECYCLE OBSERVER HELPER CLASS ───
    private inner class AppBackgroundObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // Triggers immediately when the app goes into the background
            if (session.isLoggedIn) {
                // Fast local eviction so the next boot is guaranteed to force LoginActivity
                session.clear()

                // Fire the backend API token revocation on a background thread without blocking the OS suspension
                Thread {
                    try {
                        // 🛠️ FIXED: Changed .logout() to .logoutSync() to match your new API interface method!
                        RetrofitClient.instance.logoutSync().execute()
                    } catch (_: Exception) {}
                }.start()
            }
        }
    }

    private fun startNotificationPolling() {
        lifecycleScope.launch {
            while (isActive) {
                if (!session.isLoggedIn) {
                    break
                }

                try {
                    val result = safeApiCall { RetrofitClient.instance.getNotifications() }
                    if (result is Resource.Success) {
                        currentUnreadCount = result.data.data.count { !it.isRead }
                        runOnUiThread { updateNotificationBadge(currentUnreadCount) }
                    }
                } catch (_: Exception) {}

                delay(30_000)
            }
        }
    }

    fun updateNotificationBadge(count: Int) {
        if (session.user?.role?.lowercase() == "field_personnel") return

        val notificationItem = binding.toolbar.menu.findItem(R.id.action_notifications)
        if (notificationItem != null) {
            if (count > 0) {
                notificationItem.title = "Notifications ($count)"
            } else {
                notificationItem.title = "Notifications"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        if (currentUnreadCount > 0) {
            updateNotificationBadge(currentUnreadCount)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_notifications -> {
                try {
                    navController.navigate(R.id.nav_notifications)
                } catch (e: Exception) {
                    navController.navigate(R.id.nav_dashboard)
                }
                return true
            }
            R.id.action_toolbar_profile -> {
                try {
                    navController.navigate(R.id.nav_profile)
                } catch (e: Exception) {
                    navController.navigate(R.id.nav_dashboard)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun logout() {
        // Clear local credentials instantly to secure the UI state
        session.clear()

        lifecycleScope.launch {
            safeApiCall { RetrofitClient.instance.logout() }
        }

        val intent = Intent(this@MainActivity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        // Clean up our decoupled observer tracking securely
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appBackgroundObserver)
        super.onDestroy()
    }
}