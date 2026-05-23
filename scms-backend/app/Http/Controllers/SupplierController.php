<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Supplier;

class SupplierController extends Controller
{
    public function index(Request $request) 
    {
        // Adding withCount ensures 'orders_count' is available for the React component
        return response()->json(
            Supplier::withCount('orders')->latest()->paginate(20)
        );
    }

    public function store(Request $request)
    {
        $data = $request->validate([
            'name'    => 'required|string|max:255|unique:suppliers,name',
            'email'   => 'required|email|unique:suppliers,email',
            'phone'   => 'nullable|string|max:50',
            'country' => 'nullable|string|max:100',
            'address' => 'nullable|string|max:500',
        ]);

        return response()->json(Supplier::create($data), 201);
    }

    public function show(Supplier $supplier)
    {
        return response()->json(
            $supplier->loadCount(['orders', 'products'])
                     ->load(['orders' => fn($q) => $q->latest()->limit(5)])
        );
    }

    public function update(Request $request, Supplier $supplier)
    {
        $data = $request->validate([
            'name'    => 'sometimes|string|max:255|unique:suppliers,name,' . $supplier->id,
            'email'   => 'sometimes|email|unique:suppliers,email,' . $supplier->id,
            'phone'   => 'nullable|string|max:50',
            'country' => 'nullable|string|max:100',
            'address' => 'nullable|string|max:500',
            'rating'  => 'nullable|numeric|min:0|max:5',
        ]);

        $supplier->update($data);

        return response()->json($supplier);
    }

    public function destroy(Supplier $supplier)
    {
        // check for active orders before deleting
        $hasActiveOrders = $supplier->orders()
            ->whereIn('status', ['pending', 'approved', 'shipped'])
            ->exists();

        if ($hasActiveOrders) {
            return response()->json([
                'message' => 'Cannot delete supplier. There are active orders in progress.'
            ], 422);
        }

        $supplier->delete(); // Since SoftDeletes are removed from Model, this is permanent.

        return response()->json(['message' => 'Supplier and associated records removed permanently.']);
    }
}
