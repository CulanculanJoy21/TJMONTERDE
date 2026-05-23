<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use App\Models\Product;
use App\Models\Order;
use App\Models\Delivery;
use App\Models\Supplier;
use App\Models\Notification;

class ReportController extends Controller
{
    public function dashboard()
    {
        // 1. TIME RANGES for Trends
        $thisMonthStart = now()->startOfMonth();
        $lastMonthStart = now()->subMonth()->startOfMonth();
        $lastMonthEnd   = now()->subMonth()->endOfMonth();

        // 2. TREND CALCULATIONS
        $currentValue = (float) Order::where('status', '!=', 'rejected')->where('created_at', '>=', $thisMonthStart)->sum('total_amount');
        $lastMonthValue = (float) Order::where('status', '!=', 'rejected')->whereBetween('created_at', [$lastMonthStart, $lastMonthEnd])->sum('total_amount');
        $valueTrend = $lastMonthValue > 0 ? round((($currentValue - $lastMonthValue) / $lastMonthValue) * 100, 1) : 100;

        $currentFulfilled = Order::where('status', 'delivered')->where('created_at', '>=', $thisMonthStart)->count();
        $lastMonthFulfilled = Order::where('status', 'delivered')->whereBetween('created_at', [$lastMonthStart, $lastMonthEnd])->count();
        $fulfilledDiff = $currentFulfilled - $lastMonthFulfilled;

        // 3. STATS
        $avgDelivery = Delivery::whereNotNull('delivered_at')
            ->select(DB::raw('AVG(DATEDIFF(delivered_at, created_at)) as average'))
            ->first()->average;

        return response()->json([
            'total_order_value' => (float) Order::where('status', '!=', 'rejected')->sum('total_amount'),
            'orders_this_month' => $currentFulfilled,
            'avg_delivery_days' => $avgDelivery ? round($avgDelivery, 1) : 0,
            'trends' => [
                'value_change' => ($valueTrend >= 0 ? '+' : '') . $valueTrend . '%',
                'value_up'     => $valueTrend >= 0,
                'orders_change'=> ($fulfilledDiff >= 0 ? '+' : '') . $fulfilledDiff . ' vs last month',
                'orders_up'    => $fulfilledDiff >= 0,
            ],
            'total_products'    => (int) Product::count(),
            'low_stock_items'   => (int) Product::whereColumn('stock_qty', '<=', 'reorder_point')->count(),
            'active_deliveries' => (int) Delivery::whereIn('status', ['in_transit', 'out_for_delivery'])->count(),
            'pending_orders'    => Order::where('status', 'pending')->count(),
            'approved_orders'   => Order::where('status', 'approved')->count(),
            'delivered_orders'  => $currentFulfilled,
            'recent_orders'     => Order::with(['product:id,name,stock_qty,reorder_point', 'supplier:id,name'])->latest()->limit(5)->get(),
            'activity_feed'     => Notification::latest()->limit(10)->get(),
        ]);
    }

    // THIS IS THE METHOD THAT WAS MISSING
    public function supplierPerformance()
    {
        return response()->json(
            Supplier::withCount([
                'orders as total_orders',
                'orders as fulfilled_orders' => fn($q) => $q->where('status', 'delivered')
            ])
            ->get()
            ->map(function ($supplier) {
                $score = $supplier->total_orders > 0 
                    ? ($supplier->fulfilled_orders / $supplier->total_orders) * 5 
                    : 0;

                return [
                    'id'            => $supplier->id,
                    'name'          => $supplier->name,
                    'country'       => $supplier->country ?? 'Philippines',
                    'active_orders' => $supplier->total_orders,
                    'rating'        => round($score, 1),
                ];
            })
        );
    }
}