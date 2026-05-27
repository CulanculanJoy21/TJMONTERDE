<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

class DatabaseSeeder extends Seeder
{
    /**
     * Seed the application's database.
     */
    public function run(): void
    {
        // Disable foreign key checks to safely truncate/delete tables in order
        DB::statement('SET FOREIGN_KEY_CHECKS=0;');

        DB::table('orders')->delete();
        DB::table('products')->delete();
        DB::table('suppliers')->delete(); // Clean up suppliers
        DB::table('users')->delete();

        DB::statement('SET FOREIGN_KEY_CHECKS=1;');

        // ─── 1. SEED SYSTEM ROLES / USERS ───
        DB::table('users')->insert([
            [
                'name' => 'System Administrator',
                'email' => 'admin@scms.local',
                'password' => Hash::make('password'),
                'role' => 'admin',
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'name' => 'Logistics Manager',
                'email' => 'manager@scms.com',
                'password' => Hash::make('password123'),
                'role' => 'manager',
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'name' => 'Field Personnel Driver',
                'email' => 'driver@scms.com',
                'password' => Hash::make('password123'),
                'role' => 'field_personnel',
                'created_at' => now(),
                'updated_at' => now(),
            ]
        ]);

        // ─── 2. SEED DEFAULT SUPPLIER (CRITICAL FOR THE FOREIGN KEY) ───
        // We insert a default supplier so our products have a valid 'supplier_id' to point to.
        $supplierId = DB::table('suppliers')->insertGetId([
            'name' => 'AgriGrow Global Supplies Ltd.',
            'email' => 'contact@agrigrow.com',
            'phone' => '+639123456789',
            'address' => 'Building 4, Industrial Zone, Cagayan de Oro',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        // ─── 3. SEED AGRICULTURAL PRODUCTS ───
        DB::table('products')->insert([
            [
                'supplier_id' => $supplierId,
                'name' => 'Premium Urea Fertilizer (50kg)',
                'sku' => 'FERT-UREA-001',
                'category' => 'Fertilizers',
                'description' => 'High-nitrogen 46-0-0 granular fertilizer optimized for rapid crop vegetative growth cycles.',
                'unit' => 'bags',
                'stock_qty' => 120,
                'reorder_point' => 25,
                'unit_price' => 1750.00,
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'supplier_id' => $supplierId,
                'name' => 'Complete 14-14-14 Fertilizer (50kg)',
                'sku' => 'FERT-COMP-002',
                'category' => 'Fertilizers',
                'description' => 'Balanced NPK granular compound fertilizer supporting overall plant health and root development.',
                'unit' => 'bags',
                'stock_qty' => 85,
                'reorder_point' => 20,
                'unit_price' => 1450.00,
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'supplier_id' => $supplierId,
                'name' => 'Hybrid Yellow Corn Seeds (9kg)',
                'sku' => 'SEED-CORN-001',
                'category' => 'Seeds',
                'description' => 'High-yield variety seeds with exceptional drought resistance and excellent grain quality traits.',
                'unit' => 'bags',
                'stock_qty' => 45,
                'reorder_point' => 15,
                'unit_price' => 2100.00,
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'supplier_id' => $supplierId,
                'name' => 'RC-222 Certified Rice Seeds (40kg)',
                'sku' => 'SEED-RICE-222',
                'category' => 'Seeds',
                'description' => 'Inbred irrigated lowland rice seeds known for high tillering capacity and pest resistance.',
                'unit' => 'bags',
                'stock_qty' => 12, 
                'reorder_point' => 15,
                'unit_price' => 1350.00,
                'created_at' => now(),
                'updated_at' => now(),
            ],
            [
                'supplier_id' => $supplierId,
                'name' => 'Knapsack Sprayer (16L manual)',
                'sku' => 'EQPT-SPRY-016',
                'category' => 'Equipment',
                'description' => 'Heavy-duty ergonomic crop protection fluid pressure sprayer with adjustable nozzles.',
                'unit' => 'pcs',
                'stock_qty' => 30,
                'reorder_point' => 5,
                'unit_price' => 1950.00,
                'created_at' => now(),
                'updated_at' => now(),
            ],
        ]);

        $premiumUrea = DB::table('products')->where('sku', 'FERT-UREA-001')->value('id');
        $riceSeeds   = DB::table('products')->where('sku', 'SEED-RICE-222')->value('id');
        $sprayer     = DB::table('products')->where('sku', 'EQPT-SPRY-016')->value('id');

        // Fetch the Logistics Manager user we created earlier to sign off on the orders
        $managerId = DB::table('users')->where('email', 'manager@scms.com')->value('id');

        DB::table('orders')->insert([
            [
                'product_id' => $premiumUrea, 
                'supplier_id' => $supplierId,
                'created_by' => $managerId ?? 2, // 👈 Added this line
                'qty' => 10,
                'unit_price' => 1750.00,
                'total_amount' => 17500.00,
                'status' => 'approved', 
                'created_at' => now()->subDays(2),
                'updated_at' => now(),
            ],
            [
                'product_id' => $riceSeeds, 
                'supplier_id' => $supplierId,
                'created_by' => $managerId ?? 2, // 👈 Added this line
                'qty' => 3,
                'unit_price' => 1350.00,
                'total_amount' => 4050.00,
                'status' => 'pending', 
                'created_at' => now()->subHours(5),
                'updated_at' => now(),
            ],
            [
                'product_id' => $sprayer, 
                'supplier_id' => $supplierId,
                'created_by' => $managerId ?? 2, // 👈 Added this line
                'qty' => 1,
                'unit_price' => 1950.00,
                'total_amount' => 19500.00,
                'status' => 'delivered', 
                'created_at' => now()->subDays(5),
                'updated_at' => now(),
            ]
        ]);
    }
}