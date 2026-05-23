<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Notification extends Model
{
    protected $fillable = ['type', 'title', 'message', 'data', 'read_at'];

    protected $casts = ['read_at' => 'datetime'];

    public function isRead(): bool { return $this->read_at !== null; }
}