<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Product;
use App\Models\StockHistory;
use App\Models\Notification;
use App\Models\ActivityLog;

class ProductController extends Controller
{
    public function index(Request $request)
    {
        $query = Product::with('supplier:id,name');

        if ($request->filled('search')) {
            $search = $request->search;
            $query->where(function ($q) use ($search) {
                $q->where('name', 'like', "%{$search}%")
                  ->orWhere('sku',  'like', "%{$search}%");
            });
        }

        if ($request->filled('category')) {
            $query->where('category', $request->category);
        }

        if ($request->filled('low_stock')) {
            $query->whereColumn('stock_qty', '<=', 'reorder_point');
        }

        $products = $query->orderBy('name')->paginate(20);

        return response()->json($products);
    }

    public function store(Request $request)
    {
        $data = $request->validate([
            'name'          => 'required|string|max:255',
            'sku'           => 'required|string|max:100|unique:products,sku',
            'category'      => 'required|string|max:100',
            'supplier_id'   => 'required|exists:suppliers,id',
            'stock_qty'     => 'required|integer|min:0',
            'reorder_point' => 'required|integer|min:0',
            'unit_price'    => 'required|numeric|min:0',
            'description'   => 'nullable|string',
            'unit'          => 'nullable|string|max:50',
        ]);

        $product = Product::create($data);

        // LOG PRODUCT CREATION TO AUDIT TRAIL
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'CREATE',
            'description' => "Added new product '{$product->name}' (SKU: {$product->sku}) with initial stock of {$product->stock_qty} units",
            'ip_address' => $request->ip()
        ]);

        StockHistory::create([
            'product_id' => $product->id,
            'user_id'    => $request->user()->id,
            'type'       => 'initial',
            'qty_change' => $data['stock_qty'],
            'qty_after'  => $data['stock_qty'],
            'note'       => 'Initial stock entry',
        ]);

        return response()->json($product->load('supplier:id,name'), 201);
    }

    public function show(Product $product)
    {
        return response()->json($product->load('supplier:id,name'));
    }

    public function update(Request $request, Product $product)
    {
        $data = $request->validate([
            'name'          => 'sometimes|string|max:255',
            'sku'           => 'sometimes|string|max:100|unique:products,sku,' . $product->id,
            'category'      => 'sometimes|string|max:100',
            'supplier_id'   => 'sometimes|exists:suppliers,id',
            'stock_qty'     => 'sometimes|integer|min:0',
            'reorder_point' => 'sometimes|integer|min:0',
            'unit_price'    => 'sometimes|numeric|min:0',
            'description'   => 'nullable|string',
            'unit'          => 'nullable|string|max:50',
        ]);

        $product->update($data);

        // LOG PRODUCT COMPONENT EDITS TO AUDIT TRAIL
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'UPDATE',
            'description' => "Updated product details for '{$product->name}' (SKU: {$product->sku})",
            'ip_address' => $request->ip()
        ]);

        return response()->json($product->load('supplier:id,name'));
    }

    public function destroy(Product $product)
    {
        // LOG PRODUCT DELETION TO AUDIT TRAIL BEFORE WIPING REFS
        ActivityLog::create([
            'user_id' => auth()->id(),
            'action' => 'DELETE',
            'description' => "Removed product '{$product->name}' (SKU: {$product->sku}) from system inventory records",
            'ip_address' => request()->ip()
        ]);

        $product->delete();

        return response()->json(['message' => 'Product deleted successfully.']);
    }

    public function adjustStock(Request $request, Product $product)
    {
        $data = $request->validate([
            'type'       => 'required|in:add,remove',
            'qty'        => 'required|integer|min:1',
            'note'       => 'nullable|string|max:500',
        ]);

        $change   = $data['type'] === 'add' ? $data['qty'] : -$data['qty'];
        $newStock = $product->stock_qty + $change;

        if ($newStock < 0) {
            return response()->json(['message' => 'Insufficient stock.'], 422);
        }

        $product->update(['stock_qty' => $newStock]);

        // LOG INDIVIDUAL STOCK ADJUSTMENT TYPE CHROMATICALLY
        $actionWord = $data['type'] === 'add' ? 'RESTOCK' : 'DEDUCT';
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => $actionWord,
            'description' => "Manually adjusted stock for '{$product->name}': " . ($data['type'] === 'add' ? "added" : "removed") . " {$data['qty']} units. (New Facility Stock: {$newStock})",
            'ip_address' => $request->ip()
        ]);

        StockHistory::create([
            'product_id' => $product->id,
            'user_id'    => $request->user()->id,
            'type'       => $data['type'],
            'qty_change' => $change,
            'qty_after'  => $newStock,
            'note'       => $data['note'] ?? null,
        ]);

        if ($newStock <= $product->reorder_point) {
            Notification::create([
                'type'    => 'low_stock',
                'title'   => 'Low Stock Alert',
                'message' => "{$product->name} is at {$newStock} units (reorder point: {$product->reorder_point}).",
                'data'    => json_encode(['product_id' => $product->id]),
            ]);
        }

        return response()->json([
            'product'   => $product->fresh(),
            'new_stock' => $newStock,
        ]);
    }

    public function stockHistory(Product $product)
    {
        $history = $product->stockHistories()
            ->with('user:id,name')
            ->latest()
            ->paginate(30);

        return response()->json($history);
    }
}