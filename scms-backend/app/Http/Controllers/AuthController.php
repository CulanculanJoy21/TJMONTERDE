<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;
use App\Models\User;

class AuthController extends Controller
{
    /**
     * Authenticate a user and return a token.
     * Restricted to Admin and Manager only on web dashboards.
     */
    public function login(Request $request)
    {
        $data = $request->validate([
            'email'    => 'required|email',
            'password' => 'required|string',
        ]);

        $user = User::where('email', $data['email'])->first();

        if (! $user) {
            return response()->json([
                'message' => 'The email address you entered does not exist.'
            ], 404);
        }

        if (! Hash::check($data['password'], $user->password)) {
            return response()->json([
                'message' => 'Incorrect password. Please try again.'
            ], 401);
        }

        // 🛡️ ROLE GATEKEEPER
        $clientPlatform = $request->header('X-Client-Platform');

        if ($user->role === 'field_personnel') {
            if ($clientPlatform !== 'android') {
                return response()->json([
                    'message' => 'Unauthorized: Driver accounts can only log in via the mobile application.'
                ], 403);
            }
        } elseif (!in_array($user->role, ['admin', 'manager'])) {
            return response()->json([
                'message' => 'Unauthorized: Access denied.'
            ], 403);
        }

        $user->tokens()->delete();
        $token = $user->createToken('scms-token')->plainTextToken;

        return response()->json([
            'user'  => $user,
            'token' => $token,
        ]);
    }

    /**
     * Admin-only Registration.
     */
    public function register(Request $request)
    {
        if ($request->user()->role !== 'admin') {
            return response()->json(['message' => 'Unauthorized: Admin access required.'], 403);
        }

        $data = $request->validate([
            'name'     => 'required|string|max:255',
            'email'    => 'required|email|unique:users,email',
            'password' => 'required|string|min:8',
            'role'     => 'required|in:admin,manager,field_personnel,supplier',
        ]);

        $user = User::create([
            'name'     => $data['name'],
            'email'    => $data['email'],
            'password' => Hash::make($data['password']),
            'role'     => $data['role'],
        ]);

        return response()->json([
            'message' => 'User created successfully',
            'user'    => $user,
        ], 201);
    }

    /**
     * ─── NEW: ADMIN-ONLY USER DELETION ───
     * Permanent removal of system operators, protecting Admins.
     */
    public function destroy(Request $request, $id)
    {
        // 1. Enforce admin privileges
        if ($request->user()->role !== 'admin') {
            return response()->json([
                'message' => 'Unauthorized: Only system administrators can drop user accounts.'
            ], 403);
        }

        // 2. Locate target profile
        $userToKill = User::find($id);
        if (! $userToKill) {
            return response()->json([
                'message' => 'Target user record not found.'
            ], 404);
        }

        // 3. 🛡️ CRITICAL GATE: Block deletion of ANY Admin profile
        if ($userToKill->role === 'admin') {
            return response()->json([
                'message' => 'Action Forbidden: System Administrator accounts are structurally protected and cannot be deleted.'
            ], 403);
        }

        // 4. Execute standard database deletion
        $userToKill->delete();

        return response()->json([
            'message' => 'User account successfully expunged from system registry.'
        ], 200);
    }

    public function logout(Request $request)
    {
        $request->user()->currentAccessToken()->delete();
        return response()->json(['message' => 'Logged out successfully.']);
    }

    public function me(Request $request)
    {
        return response()->json($request->user());
    }

    public function updateProfile(Request $request)
    {
        $data = $request->validate([
            'name'  => 'sometimes|string|max:255',
            'email' => 'sometimes|email|unique:users,email,' . $request->user()->id,
        ]);

        $request->user()->update($data);
        return response()->json($request->user());
    }
}