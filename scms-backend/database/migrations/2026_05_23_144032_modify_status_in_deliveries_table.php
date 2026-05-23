<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        \DB::statement("ALTER TABLE deliveries MODIFY COLUMN status ENUM('pending', 'in_transit', 'out_for_delivery', 'delivered', 'cancelled') NOT NULL DEFAULT 'pending'");
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('deliveries', function (Blueprint $table) {
            //
        });
    }
};
