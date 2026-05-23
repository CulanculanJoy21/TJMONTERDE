<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Supplier;
use App\Models\ActivityLog;
use App\Models\ApprovalRequest;

class SupplierController extends Controller
{
    public function index(Request $request) 
    {
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

        // 🔥 INTERCEPT MANAGER OPERATIONS FOR ADMINISTRATIVE APPROVAL
        if ($request->user()->role === 'manager') {
            ApprovalRequest::create([
                'user_id' => $request->user()->id,
                'model_type' => 'Supplier',
                'action_type' => 'CREATE',
                'payload' => $data
            ]);
            return response()->json(['message' => 'Supplier registration details submitted for Admin review.'], 202);
        }

        $supplier = Supplier::create($data);

        // LOG ACTIONS SECURELY
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'CREATE',
            'description' => "Registered new trade partner supplier entry: '{$supplier->name}'",
            'ip_address' => $request->ip()
        ]);

        return response()->json($supplier, 201);
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

        // RECORD DATA PROFILE EDITS
        ActivityLog::create([
            'user_id' => $request->user()->id,
            'action' => 'UPDATE',
            'description' => "Modified profiles information metadata wrapper parameters for supplier '{$supplier->name}'",
            'ip_address' => $request->ip()
        ]);

        return response()->json($supplier);
    }

    public function destroy(Supplier $supplier)
    {
        $hasActiveOrders = $supplier->orders()
            ->whereIn('status', ['pending', 'approved', 'shipped'])
            ->exists();

        if ($hasActiveOrders) {
            return response()->json([
                'message' => 'Cannot delete supplier. There are active orders in progress.'
            ], 422);
        }

        ActivityLog::create([
            'user_id' => auth()->id(),
            'action' => 'DELETE',
            'description' => "Permanently purged supplier partner reference context metadata file row: '{$supplier->name}'",
            'ip_address' => request()->ip()
        ]);

        $supplier->delete();

        return response()->json(['message' => 'Supplier and associated records removed permanently.']);
    }
}