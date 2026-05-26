package com.scms.app.models

import com.google.gson.annotations.SerializedName

// ─── AUTH ─────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val user: User
)

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String  // admin | manager | field_personnel | supplier
)

// ─── PRODUCT ──────────────────────────────────────────────────────────────────

data class Product(
    val id: Int,
    val name: String,
    val sku: String,
    val category: String,
    val description: String?,
    val unit: String?,
    @SerializedName("stock_qty")     val stockQty: Int,
    @SerializedName("reorder_point") val reorderPoint: Int,
    @SerializedName("unit_price")    val unitPrice: Double,
    val supplier: SupplierBrief?
) {
    val isLowStock: Boolean get() = stockQty <= reorderPoint
}

data class ProductRequest(
    val name: String,
    val sku: String,
    val category: String,
    @SerializedName("supplier_id")   val supplierId: Int,
    @SerializedName("stock_qty")     val stockQty: Int,
    @SerializedName("reorder_point") val reorderPoint: Int,
    @SerializedName("unit_price")    val unitPrice: Double,
    val description: String? = null,
    val unit: String? = "pcs"
)

data class StockAdjustRequest(
    val type: String,  // "add" or "remove"
    val qty: Int,
    @SerializedName("note") val note: String? = null // 🛠️ FIXED: Standardized field serialized mapping name
)

// ─── SUPPLIER ─────────────────────────────────────────────────────────────────

data class Supplier(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String?,
    val country: String?,
    val address: String?,
    val rating: Double,
    @SerializedName("active_orders") val activeOrders: Int?
)

data class SupplierBrief(
    val id: Int,
    val name: String
)

data class SupplierRequest(
    val name: String,
    val email: String,
    val phone: String?,
    val country: String?,
    val address: String?
)

// ─── ORDER ────────────────────────────────────────────────────────────────────

data class Order(
    val id: Int,
    val product: ProductBrief?,
    val supplier: SupplierBrief?,
    val qty: Int,
    @SerializedName("unit_price")    val unitPrice: Double,
    @SerializedName("total_amount")  val totalAmount: Double,
    val status: String,   // pending | approved | rejected | shipped | delivered
    val notes: String?,
    @SerializedName("review_note")   val reviewNote: String?,
    @SerializedName("created_at")    val createdAt: String
)

data class ProductBrief(
    val id: Int,
    val name: String,
    val sku: String?,
    val supplier: Supplier? = null // 🛠️ ADDED: Backup relationship mapping
)

data class OrderRequest(
    @SerializedName("product_id")  val productId: Int,
    @SerializedName("supplier_id") val supplierId: Int,
    val qty: Int,
    @SerializedName("unit_price")  val unitPrice: Double,
    val notes: String? = null
)

data class OrderStatusRequest(
    val status: String,
    val note: String? = null
)

// ─── DELIVERY ─────────────────────────────────────────────────────────────────

data class Delivery(
    val id: Int,
    @SerializedName("order_id") val orderId: Int, // 🛠️ ADDED: Backup direct key mapping parameter
    val order: OrderBrief?,
    val driver: UserBrief?,
    val status: String,   // pending | in_transit | out_for_delivery | delivered | cancelled
    val destination: String,
    @SerializedName("current_location") val currentLocation: String?,
    val eta: String?,
    @SerializedName("delivered_at") val deliveredAt: String?,
    val notes: String?,
    @SerializedName("updated_at")   val updatedAt: String
)

data class OrderBrief(
    val id: Int,
    @SerializedName("product_id") val productId: Int?,
    val product: ProductBrief?,
    val supplier: Supplier? = null // 🛠️ ADDED: Lets the delivery model grab pickup supplier profiles directly
)

data class UserBrief(
    val id: Int,
    val name: String
)

data class DeliveryStatusRequest(
    val status: String,
    val location: String? = null,
    val note: String? = null
)

// ─── NOTIFICATION ─────────────────────────────────────────────────────────────

data class Notification(
    val id: Int,
    val type: String,
    val title: String,
    val message: String,
    @SerializedName("read_at") val readAt: String?,
    @SerializedName("created_at") val createdAt: String
) {
    val isRead: Boolean get() = readAt != null
}

// ─── PAGINATED RESPONSE ───────────────────────────────────────────────────────

data class PaginatedResponse<T>(
    val data: List<T>,
    val total: Int,
    @SerializedName("current_page") val currentPage: Int,
    @SerializedName("last_page")    val lastPage: Int
)

// ─── DASHBOARD ────────────────────────────────────────────────────────────────

data class DashboardStats(
    @SerializedName("total_products")    val totalProducts: Int,
    @SerializedName("low_stock_items")   val lowStockItems: Int,
    @SerializedName("pending_orders")    val pendingOrders: Int,
    @SerializedName("active_deliveries") val activeDeliveries: Int,
    @SerializedName("total_order_value") val totalOrderValue: Double,
    @SerializedName("orders_this_month") val ordersThisMonth: Int,
    @SerializedName("recent_orders")     val recentOrders: List<Order>,
    @SerializedName("activity_feed")     val activityFeed: List<Notification>
)

data class ApprovalRequestMobile(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("model_type") val modelType: String,
    @SerializedName("action_type") val actionType: String,
    val payload: Map<String, Any>,
    @SerializedName("created_at") val createdAt: String,
    val user: UserBrief?
)

// ─── GENERIC API RESPONSE ─────────────────────────────────────────────────────

data class MessageResponse(
    val message: String
)