<?php
 
namespace App\Models;
 
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;
 
class User extends Authenticatable
{
    use HasApiTokens, Notifiable;
 
    protected $fillable = ['name', 'email', 'password', 'role'];
 
    protected $hidden = ['password', 'remember_token'];
 
    protected $casts = ['email_verified_at' => 'datetime'];
 
    public function isAdmin(): bool   { return $this->role === 'admin'; }
    public function isManager(): bool { return in_array($this->role, ['admin', 'manager']); }
    public function deliveries()      { return $this->hasMany(Delivery::class, 'driver_id'); }
    public function orders()          { return $this->hasMany(Order::class, 'created_by'); }
    public function stockHistories()  { return $this->hasMany(StockHistory::class); }
}
 