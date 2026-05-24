<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Delivery;
use App\Models\Notification;
use App\Models\ActivityLog;

class DeliveryController extends Controller
{
    public function index(Request $request)
    {
        // 🛠️ FIXED: Added foreign keys (product_id, supplier_id) so the data hooks don't fail
        $query = Delivery::with([
            'order' => function($q) {
                $q->select('id', 'product_id', 'supplier_id', 'qty', 'status', 'total_amount', 'created_at');
            },
            'order.product:id,name,sku', 
            'order.supplier:id,name', 
            'driver:id,name'
        ]);

        if ($request->user()->role === 'field_personnel') {
            $query->where('driver_id', $request->user()->id);
        }

        if ($request->filled('status')) {
            $query->where('status', $request->status);
        }

        $deliveries = $query->latest()->paginate(20);

        return response()->json($deliveries);
    }

    public function store(Request $request)
    {
        $data = $request->validate([
            'order_id'    => 'required|exists:orders,id',
            'driver_id'   => 'nullable|exists:users,id',
            'destination' => 'required|string|max:255',
            'eta'         => 'required|date',
            'notes'       => 'nullable|string|max:1000',
        ]);

        $data['status'] = 'pending';

        $delivery = Delivery::create($data);

        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'CREATE',
            'description' => "Created dispatch tracking record TRK-{$delivery->id} assigned to Order #{$delivery->order_id}",
            'ip_address' => $request->ip()
        ]);

        return response()->json($delivery->load(['order.product:id,name', 'driver:id,name']), 201);
    }

    public function show(Delivery $delivery)
    {
        return response()->json(
            $delivery->load(['order.product:id,name,sku', 'order.supplier:id,name', 'driver:id,name'])
        );
    }

    public function update(Request $request, Delivery $delivery)
    {
        $data = $request->validate([
            'driver_id'   => 'nullable|exists:users,id',
            'destination' => 'sometimes|string|max:255',
            'eta'         => 'sometimes|date',
            'notes'       => 'nullable|string|max:1000',
        ]);

        $delivery->update($data);

        return response()->json($delivery->load(['order.product:id,name', 'driver:id,name']));
    }

    public function destroy(Delivery $delivery)
    {
        // 🔥 REMOVED THE GUARD BLOCK THAT RESTRICTED DELETING DELIVERED SHIPMENTS

        ActivityLog::create([
            'user_id' => auth()->id(),
            'action' => 'DELETE',
            'description' => "Deleted tracking record TRK-{$delivery->id} for Order #{$delivery->order_id}",
            'ip_address' => request()->ip()
        ]);

        $delivery->delete();

        return response()->json(['message' => 'Delivery record deleted successfully.']);
    }

    public function updateStatus(Request $request, Delivery $delivery)
    {
        // 🛠️ FIXED: Consolidated status declarations to include 'cancelled' cleanly
        $data = $request->validate([
            'status'    => 'required|in:pending,in_transit,out_for_delivery,delivered,cancelled',
            'location'  => 'nullable|string|max:255',
            'note'      => 'nullable|string|max:500',
            'driver_id' => 'nullable|exists:users,id',
            'eta'       => 'nullable|date',
        ]);

        $oldStatus = strtoupper(str_replace('_', ' ', $delivery->status));
        $newStatus = strtoupper(str_replace('_', ' ', $data['status']));

        $actionTag = 'UPDATE';
        $logDetails = "Changed transit tracking status of delivery TRK-{$delivery->id} from {$oldStatus} to {$newStatus}";

        if ($data['status'] === 'delivered' && $delivery->status !== 'delivered') {
            $order = $delivery->order;
            
            if ($order->status !== 'delivered') {
                $order->update(['status' => 'delivered']);
                $product = $order->product;
                $product->increment('stock_qty', $order->qty);

                Notification::create([
                    'type'    => 'delivery_complete',
                    'title'   => 'Delivery Completed',
                    'message' => "Delivery TRK-{$delivery->id} has arrived. {$order->qty} units of {$product->name} added to stock.",
                    'data'    => json_encode(['delivery_id' => $delivery->id, 'order_id' => $order->id]),
                ]);
                
                $actionTag = 'ARRIVAL';
                $logDetails = "Delivery TRK-{$delivery->id} has arrived. {$order->qty} units of '{$product->name}' successfully verified and checked into warehouse inventory";
            }
        }

        // 🔥 ADDED LOGIC FOR ACTION BADGE RECORD IF MANUALLY CANCELLED
        if ($data['status'] === 'cancelled' && $delivery->status !== 'cancelled') {
            $actionTag = 'DELETE';
            $logDetails = "Cancelled active progress trail for tracking record TRK-{$delivery->id}";
        }

        $delivery->update([
            'status'           => $data['status'],
            'driver_id'        => $data['driver_id'] ?? $delivery->driver_id,
            'eta'              => $data['eta'] ?? $delivery->eta, 
            'current_location' => $data['location'] ?? $delivery->current_location,
            'notes'            => $data['note'] ?? $delivery->notes,
            'delivered_at'     => $data['status'] === 'delivered' ? now() : $delivery->delivered_at,
        ]);
        
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => $actionTag,
            'description' => $logDetails,
            'ip_address' => $request->ip()
        ]);

        return response()->json($delivery->load(['order.product:id,name', 'driver:id,name']));
    }

    public function report()
    {
        return response()->json([
            'total'          => Delivery::count(),
            'by_status'      => Delivery::select('status', \DB::raw('count(*) as count'))
                                        ->groupBy('status')
                                        ->get(),
            'avg_days'       => Delivery::whereNotNull('delivered_at')
                                        ->select(\DB::raw('AVG(DATEDIFF(delivered_at, created_at)) as avg'))
                                        ->first()->avg ?? 0,
            'recent'         => Delivery::with(['order.product:id,name', 'driver:id,name'])
                                        ->latest()
                                        ->limit(10)
                                        ->get(),
        ]);
    }
}