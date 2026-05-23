<?php
 
namespace App\Models;
 
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;
 
class Product extends Model
{
    use SoftDeletes;
 
    protected $fillable = [
        'name', 'sku', 'category', 'description',
        'supplier_id', 'stock_qty', 'reorder_point',
        'unit_price', 'unit',
    ];
 
    protected $casts = [
        'unit_price'    => 'float',
        'stock_qty'     => 'integer',
        'reorder_point' => 'integer',
    ];
 
    public function supplier()       { return $this->belongsTo(Supplier::class); }
    public function orders()         { return $this->hasMany(Order::class); }
    public function stockHistories() { return $this->hasMany(StockHistory::class); }
 
    public function isLowStock(): bool
    {
        return $this->stock_qty <= $this->reorder_point;
    }
}