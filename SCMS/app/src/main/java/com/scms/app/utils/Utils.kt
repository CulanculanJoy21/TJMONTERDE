package com.scms.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.scms.app.models.User
import retrofit2.Response
import android.widget.TextView
import com.scms.app.R


// ─── SESSION MANAGER ──────────────────────────────────────────────────────────
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "scms_session"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER  = "auth_user"
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var user: User?
        get() {
            val json = prefs.getString(KEY_USER, null) ?: return null
            return try { gson.fromJson(json, User::class.java) } catch (e: Exception) { null }
        }
        set(value) {
            val json = if (value != null) gson.toJson(value) else null
            prefs.edit().putString(KEY_USER, json).apply()
        }

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    val isAdminOrManager: Boolean
        get() = user?.role in listOf("admin", "manager")

    val isAdmin: Boolean
        get() = user?.role == "admin"

    val isFieldPersonnel: Boolean
        get() = user?.role == "field_personnel"

    fun clear() {
        prefs.edit().clear().apply()
    }
}

// ─── RESOURCE WRAPPER ─────────────────────────────────────────────────────────
sealed class Resource<T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val message: String) : Resource<T>()
    class Loading<T> : Resource<T>()
}

// ─── SAFE API CALL ────────────────────────────────────────────────────────────
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Resource<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) Resource.Success(body)
            else Resource.Error("Empty response body")
        } else {
            val errMsg = response.errorBody()?.string() ?: "Unknown error"
            Resource.Error("Error ${response.code()}: $errMsg")
        }
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Network error. Check your connection.")
    }
}

// ─── VIEW EXTENSIONS ──────────────────────────────────────────────────────────
fun View.show()      { visibility = View.VISIBLE }
fun View.hide()      { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

// 🛠️ FIXED: Removed the deprecated android.app.Fragment extension that was breaking
fun androidx.fragment.app.Fragment.toast(message: String) {
    // Jetpack androidx fragments use requireContext() perfectly
    Toast.makeText(this.requireContext(), message, Toast.LENGTH_SHORT).show()
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

// ─── STATUS HELPERS ───────────────────────────────────────────────────────────
fun statusColor(status: String): Int = when (status?.lowercase()?.trim()) {
    "pending"          -> android.R.color.holo_orange_light
    "approved"         -> android.R.color.holo_green_light
    "shipped"          -> android.R.color.holo_blue_light
    "delivered"        -> android.R.color.darker_gray
    "rejected"         -> android.R.color.holo_red_light
    "in_transit"       -> android.R.color.holo_blue_light
    "out_for_delivery" -> android.R.color.holo_green_light
    "cancelled"        -> android.R.color.holo_red_dark // 🛠️ FIXED: Map cancellation background state
    else               -> android.R.color.darker_gray
}

fun statusLabel(status: String): String = when (status?.lowercase()?.trim()) {
    "pending"          -> "Pending"
    "approved"         -> "Approved"
    "shipped"          -> "Shipped"
    "delivered"        -> "Delivered"
    "rejected"         -> "Rejected"
    "in_transit"       -> "In Transit"
    "out_for_delivery" -> "Out for Delivery"
    "cancelled"        -> "Cancelled" // 🛠️ FIXED: Add display string matching for cancellations
    else               -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
}
// ─── ADD THIS GLOBAL STATUS BADGE STYLER ───
fun styleStatusBadge(textView: TextView, status: String) {
    val context = textView.context
    when (status.lowercase()) {
        "pending" -> {
            textView.setBackgroundResource(R.drawable.bg_badge_pending_soft)
            textView.setTextColor(context.getColor(android.R.color.holo_orange_dark))
        }
        "in_transit", "shipped", "out_for_delivery" -> {
            textView.setBackgroundResource(R.drawable.bg_badge_transit_soft)
            textView.setTextColor(context.getColor(android.R.color.holo_blue_dark))
        }
        "delivered", "approved" -> {
            textView.setBackgroundResource(R.drawable.bg_badge_delivered_soft)
            textView.setTextColor(context.getColor(android.R.color.holo_green_dark))
        }
        "cancelled", "rejected" -> {
            textView.setBackgroundResource(R.drawable.bg_badge_rejected_soft)
            textView.setTextColor(context.getColor(android.R.color.holo_red_dark))
        }
        else -> {
            textView.setBackgroundResource(R.drawable.bg_badge_pending_soft)
            textView.setTextColor(context.getColor(android.R.color.darker_gray))
        }
    }
}
fun formatCurrency(amount: Double): String = "₱%,.2f".format(amount)