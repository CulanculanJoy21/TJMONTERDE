<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Product;
use App\Models\Notification;

class InventoryController extends Controller
{
    /**
     * Dispatch items out of the warehouse (Deduct Stock)
     */
    public function dispatchStock(Request $request)
    {
        // 1. Validate incoming form data
        $data = $request->validate([
            'product_id' => 'required|exists:products,id',
            'quantity'   => 'required|integer|min:1',
            'reason'     => 'required|string|max:255',
        ]);

        // 🔥 FIX: Move the product look-up ABOVE the activity logging line
        // 2. Fetch the product record
        $product = Product::findOrFail($data['product_id']);

        // 3. Log the Activity safely now that $product is available
        \App\Models\ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'CREATE',
            'description' => "Dispatched {$data['quantity']} units of '{$product->name}' (Reason: {$data['reason']})",
            'ip_address' => $request->ip()
        ]);

        // 4. Safety Check: Verify stock availability
        if ($product->stock_qty < $data['quantity']) {
            return response()->json([
                'message' => "Stockout danger! Only {$product->stock_qty} units available."
            ], 422);
        }

        // 5. Decrement the value in the database
        $product->decrement('stock_qty', $data['quantity']);

        // 6. Create an automated activity notification if stock hits reorder point
        if ($product->fresh()->stock_qty <= $product->reorder_point) {
            Notification::create([
                'title' => 'Low Stock Warning',
                'message' => "Product '{$product->name}' has dropped below its reorder point.",
                'type' => 'warning'
            ]);
        }

        return response()->json([
            'message' => 'Stock successfully dispatched.',
            'product' => $product->fresh()
        ], 200);
    } 
}