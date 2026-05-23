<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Order;
use App\Models\Product;
use App\Models\Notification;
use App\Models\Delivery;
use App\Models\ActivityLog;
use App\Models\ApprovalRequest;
use Illuminate\Support\Facades\DB;

class OrderController extends Controller
{
    /**
     * Display a listing of orders with pagination and filters.
     */
    public function index(Request $request)
    {
        $query = Order::with(['product:id,name,sku', 'supplier:id,name', 'createdBy:id,name']);

        if ($request->filled('status')) {
            $query->where('status', $request->status);
        }

        if ($request->filled('supplier_id')) {
            $query->where('supplier_id', $request->supplier_id);
        }

        if ($request->filled('from')) {
            $query->whereDate('created_at', '>=', $request->from);
        }

        if ($request->filled('to')) {
            $query->whereDate('created_at', '<=', $request->to);
        }

        return response()->json($query->latest()->paginate(20));
    }

    /**
     * Create a new order and notify administrators.
     */
    public function store(Request $request)
    {
        $data = $request->validate([
            'product_id'   => 'required|exists:products,id',
            'supplier_id'  => 'required|exists:suppliers,id',
            'qty'          => 'required|integer|min:1',
            'unit_price'   => 'required|numeric|min:0',
            'notes'        => 'nullable|string|max:1000',
        ]);

        // 🔥 INTERCEPT MANAGER OPERATIONS FOR ADMINISTRATIVE APPROVAL
        if ($request->user()->role === 'manager') {
            ApprovalRequest::create([
                'user_id' => $request->user()->id,
                'model_type' => 'Order',
                'action_type' => 'CREATE',
                'payload' => $data
            ]);
            return response()->json(['message' => 'Supply procurement request submitted for Admin review.'], 202);
        }

        $data['total_amount'] = $data['qty'] * $data['unit_price'];
        $data['status']       = 'pending';
        $data['created_by']   = $request->user()->id;

        $order = Order::create($data);

        // LOG ACTION TO IMMUTABLE AUDIT TRAIL
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'ORDER',
            'description' => "Placed new supply procurement request Order #{$order->id} for {$order->qty} units",
            'ip_address' => $request->ip()
        ]);

        Notification::create([
            'type'    => 'order_created',
            'title'   => 'New Order Submitted',
            'message' => "Order #{$order->id} has been submitted.",
            'data'    => json_encode(['order_id' => $order->id]),
        ]);

        return response()->json($order->load(['product:id,name,sku', 'supplier:id,name']), 201);
    }

    /**
     * Approve or reject an order.
     */
    public function updateStatus(Request $request, $id)
    {
        $order = Order::findOrFail($id);

        $data = $request->validate([
            'status' => 'required|in:approved,rejected,shipped,delivered',
            'note'   => 'nullable|string|max:500',
        ]);

        DB::transaction(function () use ($request, $order, $data) {
            $order->update([
                'status'      => $data['status'],
                'reviewed_by' => $request->user()->id,
                'review_note' => $data['note'] ?? null,
            ]);

            if ($data['status'] === 'approved') {
                Delivery::updateOrCreate(
                    ['order_id' => $order->id],
                    [
                        'status'      => 'pending',
                        'driver_id'   => null,
                        'destination' => 'Main Warehouse',
                        'eta'         => now()->addDays(3)->toDateString(),
                    ]
                );
            }

            // DYNAMICALLY MAP ACTION BADGE TYPE TO FIT THE CHOSEN OPERATION
            $actionTag = $data['status'] === 'approved' ? 'APPROVE' : ($data['status'] === 'rejected' ? 'REJECT' : 'UPDATE');
            ActivityLog::create([
                'user_id' => $request->user()->id,
                'action' => $actionTag,
                'description' => ucfirst($data['status']) . " procurement supply request Order #{$order->id}",
                'ip_address' => $request->ip()
            ]);

            Notification::create([
                'type'    => 'order_status',
                'title'   => 'Order Status Updated',
                'message' => "Order #{$order->id} has been {$data['status']}.",
                'data'    => json_encode(['order_id' => $order->id, 'status' => $data['status']]),
            ]);
        });

        return response()->json($order->load(['product:id,name,sku', 'supplier:id,name', 'delivery']));
    }

    /**
     * Display a specific order.
     */
    public function show(Order $order)
    {
        return response()->json(
            $order->load(['product:id,name,sku', 'supplier:id,name', 'createdBy:id,name', 'delivery'])
        );
    }

    /**
     * Update general order details.
     */
    public function update(Request $request, Order $order)
    {
        $data = $request->validate([
            'qty'        => 'sometimes|integer|min:1',
            'unit_price' => 'sometimes|numeric|min:0',
            'notes'      => 'nullable|string|max:1000',
        ]);

        if (isset($data['qty']) || isset($data['unit_price'])) {
            $qty   = $data['qty']        ?? $order->qty;
            $price = $data['unit_price'] ?? $order->unit_price;
            $data['total_amount'] = $qty * $price;
        }

        $order->update($data);

        return response()->json($order->load(['product:id,name,sku', 'supplier:id,name']));
    }

    /**
     * Remove the order (Pending/Rejected only).
     */
    public function destroy($id)
    {
        $order = Order::findOrFail($id);
        
        if ($order->status === 'pending' || $order->status === 'approved' || $order->status === 'shipped') {
            return response()->json(['message' => 'Cannot delete active orders'], 403);
        }

        ActivityLog::create([
            'user_id' => auth()->id(),
            'action' => 'DELETE',
            'description' => "Permanently deleted Order #{$order->id} from historical records",
            'ip_address' => request()->ip()
        ]);

        $order->delete();
        return response()->json(['message' => 'Order deleted successfully']);
    }
}