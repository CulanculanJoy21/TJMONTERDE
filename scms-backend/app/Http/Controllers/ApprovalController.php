<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\ApprovalRequest;
use App\Models\Product;
use App\Models\Order;
use App\Models\Supplier;
use App\Models\ActivityLog;

class ApprovalController extends Controller
{
    public function index()
    {
        return response()->json(ApprovalRequest::with('user:id,name,role')->where('status', 'pending')->latest()->get());
    }

    public function review(Request $request, $id)
    {
        $data = $request->validate([
            'status' => 'required|in:approved,rejected',
            'review_note' => 'nullable|string|max:500'
        ]);

        $approval = ApprovalRequest::findOrFail($id);
        
        if ($approval->status !== 'pending') {
            return response()->json(['message' => 'Request already reviewed.'], 422);
        }

        if ($data['status'] === 'approved') {
            $payload = $approval->payload;

            switch ($approval->model_type) {
                case 'Product':
                    if ($approval->action_type === 'CREATE') {
                        Product::create($payload);
                    } elseif ($approval->action_type === 'UPDATE') {
                        Product::findOrFail($approval->model_id)->update($payload);
                    }
                    break;

                case 'Order':
                    if ($approval->action_type === 'CREATE') {
                        // Automatically inject order creator details
                        $payload['total_amount'] = $payload['qty'] * $payload['unit_price'];
                        $payload['status'] = 'pending';
                        $payload['created_by'] = $approval->user_id;
                        Order::create($payload);
                    }
                    break;

                case 'Supplier':
                    if ($approval->action_type === 'CREATE') {
                        Supplier::create($payload);
                    }
                    break;

                case 'Dispatch':
                    $product = Product::findOrFail($payload['product_id']);
                    $product->decrement('stock_qty', $payload['quantity']);
                    break;
            }

            ActivityLog::create([
                'user_id' => $request->user()->id,
                'action' => 'APPROVE',
                'description' => "Approved manager request: {$approval->action_type} for {$approval->model_type}",
                'ip_address' => $request->ip()
            ]);
        }

        $approval->update([
            'status' => $data['status'],
            'reviewed_by' => $request->user()->id,
            'review_note' => $data['review_note'] ?? null
        ]);

        return response()->json(['message' => "Request successfully marked as {$data['status']}."]);
    }
}