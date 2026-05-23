<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Delivery extends Model
{
    protected $fillable = [
        'order_id', 'driver_id',
        'status', 'destination', 'current_location',
        'eta', 'delivered_at', 'notes',
    ];

    protected $casts = [
        'eta'          => 'datetime',
        'delivered_at' => 'datetime',
    ];

    public function order()  { return $this->belongsTo(Order::class); }
    public function driver() { return $this->belongsTo(User::class, 'driver_id'); }
}