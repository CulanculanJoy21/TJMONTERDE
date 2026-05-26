package com.scms.app.api

import com.scms.app.models.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ScmsApi {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<MessageResponse>

    // 🛠️ ADDED: Synchronous companion call mapping that supports immediate .execute() on background threads
    @POST("auth/logout")
    fun logoutSync(): Call<MessageResponse>

    @GET("auth/me")
    suspend fun me(): Response<User>

    @PUT("auth/profile")
    suspend fun updateProfile(@Body body: Map<String, String>): Response<User>

    // Dashboard
    @GET("reports/dashboard")
    suspend fun getDashboard(): Response<DashboardStats>

    // Products
    @GET("products")
    suspend fun getProducts(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("low_stock") lowStock: Boolean? = null,
        @Query("page") page: Int = 1
    ): Response<PaginatedResponse<Product>>

    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: Int): Response<Product>

    @POST("products")
    suspend fun createProduct(@Body request: ProductRequest): Response<Product>

    @PUT("products/{id}")
    suspend fun updateProduct(@Path("id") id: Int, @Body request: ProductRequest): Response<Product>

    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: Int): Response<MessageResponse>

    @POST("products/{id}/adjust-stock")
    suspend fun adjustStock(@Path("id") id: Int, @Body request: StockAdjustRequest): Response<Map<String, Any>>

    // Suppliers
    @GET("suppliers")
    suspend fun getSuppliers(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1
    ): Response<PaginatedResponse<Supplier>>

    @GET("suppliers/{id}")
    suspend fun getSupplier(@Path("id") id: Int): Response<Supplier>

    @POST("suppliers")
    suspend fun createSupplier(@Body request: SupplierRequest): Response<Supplier>

    @PUT("suppliers/{id}")
    suspend fun updateSupplier(@Path("id") id: Int, @Body request: SupplierRequest): Response<Supplier>

    @DELETE("suppliers/{id}")
    suspend fun deleteSupplier(@Path("id") id: Int): Response<MessageResponse>

    // Orders
    @GET("orders")
    suspend fun getOrders(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1
    ): Response<PaginatedResponse<Order>>

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: Int): Response<Order>

    @POST("orders")
    suspend fun createOrder(@Body request: OrderRequest): Response<Order>

    @PATCH("orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: Int, @Body request: OrderStatusRequest): Response<Order>

    @DELETE("orders/{id}")
    suspend fun deleteOrder(@Path("id") id: Int): Response<MessageResponse>

    // Deliveries
    @GET("deliveries")
    suspend fun getDeliveries(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1
    ): Response<PaginatedResponse<Delivery>>

    @GET("deliveries/{id}")
    suspend fun getDelivery(@Path("id") id: Int): Response<Delivery>

    @PATCH("deliveries/{id}/status")
    suspend fun updateDeliveryStatus(@Path("id") id: Int, @Body request: DeliveryStatusRequest): Response<Delivery>

    @DELETE("deliveries/{id}")
    suspend fun deleteDelivery(@Path("id") id: Int): Response<MessageResponse>

    // Reports
    @GET("reports/inventory")
    suspend fun getInventoryReport(): Response<Map<String, Any>>

    @GET("reports/orders")
    suspend fun getOrdersReport(): Response<Map<String, Any>>

    @GET("reports/supplier-performance")
    suspend fun getSupplierPerformance(): Response<List<Supplier>>

    // Notifications
    @GET("notifications")
    suspend fun getNotifications(): Response<PaginatedResponse<Notification>>

    @PATCH("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Int): Response<Notification>

    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<MessageResponse>

    // Approvals
    @GET("approvals")
    suspend fun getPendingApprovals(): Response<List<ApprovalRequestMobile>>

    @PATCH("approvals/{id}/review")
    suspend fun reviewApproval(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>
}

object RetrofitClient {

    private var token: String? = null

    fun setToken(newToken: String?) {
        token = newToken
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            token?.let { addHeader("Authorization", "Bearer $it") }
            addHeader("Accept", "application/json")
            addHeader("Content-Type", "application/json")
            addHeader("X-Client-Platform", "android")
        }.build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: ScmsApi by lazy {
        Retrofit.Builder()
        // 🛠️ CHANGED: Swap 10.0.2.2 with your computer's real local IP address 192.168.1.39
            .baseUrl("http://10.0.2.2:8000/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScmsApi::class.java)
    }
}