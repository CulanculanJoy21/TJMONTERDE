<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Notification;

class NotificationController extends Controller
{
    public function index()
    {
        return response()->json(
            Notification::latest()->paginate(30)
        );
    }

    public function markRead(Notification $notification)
    {
        $notification->update(['read_at' => now()]);
        return response()->json($notification);
    }

    public function markAllRead()
    {
        Notification::whereNull('read_at')->update(['read_at' => now()]);
        return response()->json(['message' => 'All notifications marked as read.']);
    }

    /**
     * Clear all notifications permanently from the database
     */
    public function clearAll()
    {
        // Mass deletes all rows in the notifications table safely
        Notification::query()->delete();

        return response()->json([
            'message' => 'Notification history cleared permanently.'
        ], 200);
    }
}