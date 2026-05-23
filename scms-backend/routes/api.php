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
use App\Http\Controllers\ActivityLogController;

/*
|--------------------------------------------------------------------------
| SCMS API Routes
|--------------------------------------------------------------------------
*/

// ── Public Routes ─────────────────────────────────────────────────────────────
Route::post('/auth/login', [AuthController::class, 'login'])->name('login');

// ── Protected Routes ──────────────────────────────────────────────────────────
Route::middleware('auth:sanctum')->group(function () {

    // ── Auth, Profile & Identity ──
    Route::post('/auth/logout',   [AuthController::class, 'logout']);
    Route::get('/auth/me',        [AuthController::class, 'me']);
    Route::put('/auth/profile',   [AuthorizationController::class, 'updateProfile']);
    Route::put('/auth/password',  [AuthController::class, 'changePassword']);

    // ── STRICT ADMINISTRATIVE SECURITY OVERRIDES (Admin Only) ──
    Route::middleware('role:admin')->group(function () {
        Route::post('/auth/register', [AuthController::class, 'register']);
        
        // Data Destructive Capabilities (Restricted to Admin)
        Route::delete('/products/{product}',   [ProductController::class, 'destroy']);
        Route::delete('/orders/{id}',          [OrderController::class, 'destroy']);
        Route::delete('/deliveries/{delivery}', [DeliveryController::class, 'destroy']); 
        Route::delete('/suppliers/{supplier}', [SupplierController::class, 'destroy']);

        // System Auditing
        Route::get('/activity-logs', [ActivityLogController::class, 'index']);
    });

    // ── ADMIN / MANAGER SHARED USER DIRECTORY ──
    Route::middleware('role:admin,manager')->group(function () {
        Route::get('/users', function() { return \App\Models\User::all(); });
    });

    // ── ADMIN / MANAGER ONLY (Operational Business Logic) ──
    Route::middleware('role:admin,manager')->group(function () {
        // Products / Inventory Management (Excluding Delete)
        Route::get('/products',                 [ProductController::class, 'index']);
        Route::post('/products',                [ProductController::class, 'store']);
        Route::get('/products/{product}',       [ProductController::class, 'show']);
        Route::put('/products/{product}',       [ProductController::class, 'update']);
        Route::get('/products/{id}/history',    [ProductController::class, 'stockHistory']);
        Route::post('/products/{id}/adjust-stock', [ProductController::class, 'adjustStock']);

        Route::post('/inventory/dispatch',      [InventoryController::class, 'dispatchStock']);
        
        // Orders Management (Excluding Delete)
        Route::get('/orders',                   [OrderController::class, 'index']);
        Route::post('/orders',                  [OrderController::class, 'store']);
        Route::get('/orders/{order}',           [OrderController::class, 'show']);
        Route::put('/orders/{order}',           [OrderController::class, 'update']);
        Route::patch('/orders/{id}/status',     [OrderController::class, 'updateStatus']);

        // Suppliers Management (Excluding Delete)
        Route::get('/suppliers',                [SupplierController::class, 'index']);
        Route::post('/suppliers',               [SupplierController::class, 'store']);
        Route::get('/suppliers/{supplier}',     [SupplierController::class, 'show']);
        Route::put('/suppliers/{supplier}',     [SupplierController::class, 'update']);

        // Reports & Analytics
        Route::get('/reports/dashboard',           [ReportController::class, 'dashboard']);
        Route::get('/reports/inventory',           [ReportController::class, 'inventory']);
        Route::get('/reports/orders',              [ReportController::class, 'orders']);
        Route::get('/reports/supplier-performance', [ReportController::class, 'supplierPerformance']);
        Route::get('/reports/delivery',            [ReportController::class, 'delivery']);
    });

    // ── SHARED LOGISTICS (Admin + Manager + Field Personnel) ──
    Route::get('/deliveries',                     [DeliveryController::class, 'index']);
    Route::get('/deliveries/{delivery}',        [DeliveryController::class, 'show']); 
    Route::patch('/deliveries/{delivery}/status', [DeliveryController::class, 'updateStatus']); 

    Route::middleware('role:admin,manager')->group(function () {
        Route::post('/deliveries',              [DeliveryController::class, 'store']);
        Route::put('/deliveries/{delivery}',    [DeliveryController::class, 'update']); 
    });

    // ── Notifications ──
    Route::get('/notifications',              [NotificationController::class, 'index']);
    Route::delete('/notifications/clear-all', [NotificationController::class, 'clearAll']);
    Route::patch('/notifications/{id}/read',  [NotificationController::class, 'markRead']);
    Route::post('/notifications/read-all',    [NotificationController::class, 'markAllRead']);
});