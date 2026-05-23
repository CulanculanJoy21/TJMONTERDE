<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Order extends Model
{
    use SoftDeletes;

    protected $fillable = [
        'product_id', 'supplier_id', 'created_by', 'reviewed_by',
        'qty', 'unit_price', 'total_amount',
        'status', 'notes', 'review_note',
    ];

    protected $casts = [
        'unit_price'   => 'float',
        'total_amount' => 'float',
        'qty'          => 'integer',
    ];

    public function product()    { return $this->belongsTo(Product::class); }
    public function supplier()   { return $this->belongsTo(Supplier::class); }
    public function createdBy()  { return $this->belongsTo(User::class, 'created_by'); }
    public function reviewedBy() { return $this->belongsTo(User::class, 'reviewed_by'); }
    public function delivery()   { return $this->hasOne(Delivery::class); }
}