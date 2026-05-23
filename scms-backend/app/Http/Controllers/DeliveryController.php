<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Delivery;
use App\Models\Notification;
use App\Models\ActivityLog; // Imported model cleanly at top

class DeliveryController extends Controller
{
    public function index(Request $request)
    {
        $query = Delivery::with(['order.product:id,name,sku', 'order.supplier:id,name', 'driver:id,name']);

        // Field personnel see only their assigned deliveries
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

        // LOG CREATION
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'CREATE',
            'description' => "Created tracking record TRK-{$delivery->id} for Order #{$delivery->order_id}",
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
        // Safety Check: Prevent deleting records that have already updated stock
        if ($delivery->status === 'delivered') {
            return response()->json([
                'message' => 'Cannot delete a tracking record for a completed delivery.'
            ], 422);
        }

        // LOG DELETION
        ActivityLog::create([
            'user_id' => auth()->id(),
            'action' => 'DELETE',
            'description' => "Deleted tracking record TRK-{$delivery->id} for Order #{$delivery->order_id}",
            'ip_address' => request()->ip()
        ]);

        $delivery->delete();

        return response()->json(['message' => 'Delivery record deleted successfully.']);
    }

    /**
     * Update delivery status and assignments.
     */
    public function updateStatus(Request $request, Delivery $delivery)
    {
        $data = $request->validate([
            'status'    => 'required|in:pending,in_transit,out_for_delivery,delivered',
            'location'  => 'nullable|string|max:255',
            'note'      => 'nullable|string|max:500',
            'driver_id' => 'nullable|exists:users,id',
            'eta'       => 'nullable|date',
        ]);

        // Keep track of the previous status before modifying the object row
        $oldStatus = strtoupper(str_replace('_', ' ', $delivery->status));
        $newStatus = strtoupper(str_replace('_', ' ', $data['status']));

        // Only process stock and notifications if the status is CHANGING to delivered
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
            }
        }

        // Update with the new assignment fields
        $delivery->update([
            'status'           => $data['status'],
            'driver_id'        => $data['driver_id'] ?? $delivery->driver_id,
            'eta'              => $data['eta'] ?? $delivery->eta, 
            'current_location' => $data['location'] ?? $delivery->current_location,
            'notes'            => $data['note'] ?? $delivery->notes,
            'delivered_at'     => $data['status'] === 'delivered' ? now() : $delivery->delivered_at,
        ]);
        
        // LOG STATUS CHANGE
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'UPDATE',
            'description' => "Changed status of delivery TRK-{$delivery->id} from {$oldStatus} to {$newStatus}",
            'ip_address' => $request->ip()
        ]);

        return response()->json($delivery->load(['order.product:id,name', 'driver:id,name']));
    }

    /**
     * Delivery report.
     */
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