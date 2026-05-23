<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\ProductController;
use App\Http\Controllers\OrderController;
use App\Http\Controllers\DeliveryController;
use App\Http\Controllers\SupplierController;
use App\Http\Controllers\ReportController;
use App\Http\Controllers\NotificationController;
use App\Http\Controllers\InventoryController;
use App\Http\Controllers\ActivityLogController; // Added for audit logging

/*
|--------------------------------------------------------------------------
| SCMS API Routes
|--------------------------------------------------------------------------
*/

// ── Public Routes ─────────────────────────────────────────────────────────────
// Only Login remains public. Registration is now an internal Admin task.
Route::post('/auth/login', [AuthController::class, 'login'])->name('login');

// ── Protected Routes ──────────────────────────────────────────────────────────
Route::middleware('auth:sanctum')->group(function () {

    // ── Auth, Profile & Identity ──
    Route::post('/auth/logout',   [AuthController::class, 'logout']);
    Route::get('/auth/me',        [AuthController::class, 'me']);
    Route::put('/auth/profile',   [AuthorizationController::class, 'updateProfile']);
    Route::put('/auth/password',  [AuthController::class, 'changePassword']);

    // ── ADMIN ONLY (User & Team Management) ──
    Route::middleware('role:admin')->group(function () {
        // Only an Admin can create new accounts (Managers, Drivers, etc.)
        Route::post('/auth/register', [AuthController::class, 'register']);
        Route::get('/users', function() { return \App\Models\User::all(); });
        
        // ── ADDED: SECURITY AUDIT ENDPOINT ───────────────────────────────────
        Route::get('/activity-logs', [ActivityLogController::class, 'index']);
        // ─────────────────────────────────────────────────────────────────────
    });

    // ── ADMIN / MANAGER ONLY (Business Logic & Inventory) ──
    Route::middleware('role:admin,manager')->group(function () {
        // Products / Inventory
        Route::apiResource('products', ProductController::class);
        Route::get('/products/{id}/history', [ProductController::class, 'stockHistory']);
        Route::post('/products/{id}/adjust-stock', [ProductController::class, 'adjustStock']);

        Route::post('/inventory/dispatch', [InventoryController::class, 'dispatchStock']);
        
        // Orders
        Route::apiResource('orders', OrderController::class);
        Route::patch('/orders/{id}/status', [OrderController::class, 'updateStatus']);
        Route::delete('/orders/{id}', [OrderController::class, 'destroy']);

        // Suppliers
        Route::apiResource('suppliers', SupplierController::class);

        // Reports & Analytics
        Route::get('/reports/dashboard', [ReportController::class, 'dashboard']);
        Route::get('/reports/inventory', [ReportController::class, 'inventory']);
        Route::get('/reports/orders',    [ReportController::class, 'orders']);
        Route::get('/reports/supplier-performance', [ReportController::class, 'supplierPerformance']);
        Route::get('/reports/delivery',  [ReportController::class, 'delivery']);
    });

    // ── SHARED LOGISTICS (Admin + Field Personnel) ──
    Route::get('/deliveries',                   [DeliveryController::class, 'index']);
    Route::get('/deliveries/{delivery}',        [DeliveryController::class, 'show']); 
    Route::patch('/deliveries/{delivery}/status', [DeliveryController::class, 'updateStatus']); 

    // Management of deliveries (Admin/Manager only)
    Route::middleware('role:admin,manager')->group(function () {
        // Shared delivery controls
        Route::post('/deliveries',              [DeliveryController::class, 'store']);
        // Specific document parameters
        Route::put('/deliveries/{delivery}',    [DeliveryController::class, 'update']); 
        Route::delete('/deliveries/{delivery}', [DeliveryController::class, 'destroy']); 
    });

    // ── Notifications ──
    Route::get('/notifications',             [NotificationController::class, 'index']);
    
    // 🔥 FIXED: Clear-all is placed explicitly ABOVE the wildcard parameter {id}
    Route::delete('/notifications/clear-all', [NotificationController::class, 'clearAll']);
    
    Route::patch('/notifications/{id}/read', [NotificationController::class, 'markRead']);
    Route::post('/notifications/read-all',   [NotificationController::class, 'markAllRead']);
});