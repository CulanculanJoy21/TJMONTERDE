<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('approval_requests', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->onDelete('cascade'); 
            $table->string('model_type'); // 'Product', 'Order', 'Supplier', 'Dispatch'
            $table->string('action_type'); // 'CREATE', 'UPDATE', 'DISPATCH'
            $table->unsignedBigInteger('model_id')->nullable(); 
            $table->json('payload'); 
            $table->string('status')->default('pending'); // 'pending', 'approved', 'rejected'
            $table->foreignId('reviewed_by')->nullable()->constrained('users')->onDelete('set null');
            $table->text('review_note')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('approval_requests');
    }
};