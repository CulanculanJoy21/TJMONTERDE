<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;
use App\Models\User;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // This is your master login for both Web and Android
        User::create([
            'name' => 'System Administrator',
            'email' => 'admin@scms.local',
            'password' => Hash::make('password'),
            'role' => 'admin',
        ]);
    }
}