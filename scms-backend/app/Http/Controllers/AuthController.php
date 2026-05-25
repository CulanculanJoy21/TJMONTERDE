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
     * Restricted to Admin and Manager only.
     */
    public function login(Request $request)
    {
        $data = $request->validate([
            'email'    => 'required|email',
            'password' => 'required|string',
        ]);

        // 1. First, check if the email exists
        $user = User::where('email', $data['email'])->first();

        if (! $user) {
            return response()->json([
                'message' => 'The email address you entered does not exist.'
            ], 404); // Not Found
        }

        // 2. Next, check if the password is correct
        if (! Hash::check($data['password'], $user->password)) {
            return response()->json([
                'message' => 'Incorrect password. Please try again.'
            ], 401); // Unauthorized
        }

        // 3. 🛡️ ROLE GATEKEEPER
        $clientPlatform = $request->header('X-Client-Platform');

        if ($user->role === 'field_personnel') {
            // 🚚 Drivers are strictly forbidden from logging into the Web Dashboard
            if ($clientPlatform !== 'android') {
                return response()->json([
                    'message' => 'Unauthorized: Driver accounts can only log in via the mobile application.'
                ], 403);
            }
        } elseif (!in_array($user->role, ['admin', 'manager'])) {
            // Block any other roles that aren't admin or manager
            return response()->json([
                'message' => 'Unauthorized: Access denied.'
            ], 403);
        }

        // Revoke previous tokens for a clean session
        $user->tokens()->delete();
        $token = $user->createToken('scms-token')->plainTextToken;

        return response()->json([
            'user'  => $user,
            'token' => $token,
        ]);
    }

    /**
     * Admin-only Registration.
     * Use this for adding new users/drivers from the dashboard.
     */
    public function register(Request $request)
    {
        // 🛡️ Ensure only current admins can create new users
        if ($request->user()->role !== 'admin') {
            return response()->json(['message' => 'Unauthorized: Admin access required.'], 403);
        }

        $data = $request->validate([
            'name'     => 'required|string|max:255',
            'email'    => 'required|email|unique:users,email',
            'password' => 'required|string|min:8', // Usually set by admin
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