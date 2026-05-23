<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\ActivityLog;

class ActivityLogController extends Controller
{
    /**
     * Display a paginated list of system activity logs.
     * Restricted to Admins via middleware.
     */
    public function index()
    {
        // Fetch logs sorted by latest action, loading the user details
        $logs = ActivityLog::with('user:id,name,role')
            ->latest()
            ->paginate(50);

        return response()->json($logs);
    }
}