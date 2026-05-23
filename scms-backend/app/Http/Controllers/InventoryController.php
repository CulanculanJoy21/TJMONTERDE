<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Product;
use App\Models\Notification;

class InventoryController extends Controller
{
    /**
     * Dispatch items out of the warehouse (Deduct Stock)
     * Make sure this exact name matches your routes/api.php file
     */
    public function dispatchStock(Request $request)
    {
        // 1. Validate incoming form data
        $data = $request->validate([
            'product_id' => 'required|exists:products,id',
            'quantity'   => 'required|integer|min:1',
            'reason'     => 'required|string|max:255',
        ]);

        // 2. Fetch the product record
        $product = Product::findOrFail($data['product_id']);

        // 3. Safety Check: Verify stock availability
        // NOTE: If your column name is 'stock' instead of 'stock_qty', change it here
        if ($product->stock_qty < $data['quantity']) {
            return response()->json([
                'message' => "Stockout danger! Only {$product->stock_qty} units available."
            ], 422);
        }

        // 4. Decrement the value in the database
        // NOTE: If your column name is 'stock' instead of 'stock_qty', change it here as well
        $product->decrement('stock_qty', $data['quantity']);

        // 5. Create an automated activity notification if stock hits reorder point
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
    } // <--- Make sure this closing bracket is HERE

} // <--- This bracket closes the whole Controller class