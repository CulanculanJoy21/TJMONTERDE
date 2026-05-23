<?php
 
namespace App\Models;
 
use Illuminate\Database\Eloquent\Model;
// Removed SoftDeletes import
 
class Supplier extends Model
{
    // Removed use SoftDeletes;
 
    protected $fillable = ['name', 'email', 'phone', 'country', 'address', 'rating'];
    protected $casts = ['rating' => 'float'];
 
    public function products() { return $this->hasMany(Product::class); }
    public function orders()   { return $this->hasMany(Order::class); }
}