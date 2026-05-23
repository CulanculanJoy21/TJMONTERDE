<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Product;
use App\Models\Notification;
use App\Models\ActivityLog;

class InventoryController extends Controller
{
    /**
     * Dispatch items out of the warehouse (Deduct Stock)
     */
    public function dispatchStock(Request $request)
    {
        $data = $request->validate([
            'product_id' => 'required|exists:products,id',
            'quantity'   => 'required|integer|min:1',
            'reason'     => 'required|string|max:255',
        ]);

        // FETCH PRODUCT FIRST SO THE VARIABLE VARIABLE EXISTS FOR DESCRIPTION CALLS
        $product = Product::findOrFail($data['product_id']);

        if ($product->stock_qty < $data['quantity']) {
            return response()->json([
                'message' => "Stockout danger! Only {$product->stock_qty} units available."
            ], 422);
        }

        // 🔥 LOG TRANSACTIONS UNDER EXPLICIT 'DISPATCH' LABEL
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'DISPATCH',
            'description' => "Dispatched {$data['quantity']} units of '{$product->name}' leaving facility. (Reason: {$data['reason']})",
            'ip_address' => $request->ip()
        ]);

        $product->decrement('stock_qty', $data['quantity']);

        if ($product->fresh()->stock_qty <= $product->reorder_point) {
            Notification::create([
                'title' => 'Low Stock Warning',
                'message' => "Product '{$product->name}' has dropped below its safety reorder point.",
                'type' => 'warning'
            ]);
        }

        return response()->json([
            'message' => 'Stock successfully dispatched.',
            'product' => $product->fresh()
        ], 200);
    } 
}