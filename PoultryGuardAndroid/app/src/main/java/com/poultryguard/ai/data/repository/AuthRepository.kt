package com.poultryguard.ai.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.UserRole
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<UserProfile>
    suspend fun register(name: String, email: String, password: String, role: UserRole): Result<UserProfile>
    suspend fun forgotPassword(email: String): Result<Unit>
    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUser(): UserProfile?
    fun isSimulatedMode(): Boolean
}

class FirebaseAuthRepository(private val context: Context) : AuthRepository {

    private var firebaseInitialized = false
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null

    // Simple simulated in-memory store for fallback mode
    private companion object {
        var simulatedUser: UserProfile? = null
        val simulatedUsersDb = mutableMapOf<String, UserProfile>(
            "farmer@poultry.ai" to UserProfile("sim_1", "Joe Patterson", "farmer@poultry.ai", UserRole.FARMER, joinDate = "31 May 2026"),
            "vet@poultry.ai" to UserProfile("sim_2", "Dr. Sarah Jenkins", "vet@poultry.ai", UserRole.VETERINARIAN, joinDate = "31 May 2026"),
            "admin@poultry.ai" to UserProfile("sim_3", "Admin Control Desk", "admin@poultry.ai", UserRole.ADMIN, joinDate = "31 May 2026")
        )
    }

    init {
        try {
            // Check if Firebase is initialized. If google-services.json is missing, this will fail gracefully.
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                auth = FirebaseAuth.getInstance()
                db = FirebaseFirestore.getInstance()
                firebaseInitialized = true
                Log.d("PoultryGuardAuth", "Firebase Auth & Firestore initialized successfully.")
            } else {
                Log.w("PoultryGuardAuth", "Firebase is not initialized. Using simulated agricultural credentials fallback.")
            }
        } catch (e: Exception) {
            Log.w("PoultryGuardAuth", "Firebase initialization skipped. Operating in Simulated Agricultural mode: ${e.localizedMessage}")
        }
    }

    override fun isSimulatedMode(): Boolean {
        return !firebaseInitialized
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> {
        if (!firebaseInitialized || auth == null || db == null) {
            // Simulated login fallback
            return try {
                kotlinx.coroutines.delay(1000) // Realistic network delay
                if (password.length < 6) {
                    throw Exception("Password must be at least 6 characters.")
                }
                
                val lowerEmail = email.lowercase().trim()
                val existingProfile = simulatedUsersDb[lowerEmail]
                
                val profile = if (existingProfile != null) {
                    existingProfile
                } else {
                    // Implicitly register a new user in simulation if they provide a valid role email format
                    val detectedRole = when {
                        lowerEmail.contains("vet") -> UserRole.VETERINARIAN
                        lowerEmail.contains("admin") -> UserRole.ADMIN
                        else -> UserRole.FARMER
                    }
                    val newProfile = UserProfile(
                        uid = "sim_${System.currentTimeMillis()}",
                        name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                        email = lowerEmail,
                        role = detectedRole,
                        joinDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                    )
                    simulatedUsersDb[lowerEmail] = newProfile
                    newProfile
                }
                simulatedUser = profile
                Result.success(profile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Real Firebase Auth & Firestore flow
        return try {
            val authResult = auth!!.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("Authentication returned empty user credential.")
            
            // Read role details from Firestore database
            val docSnap = db!!.collection("users").document(uid).get().await()
            if (!docSnap.exists()) {
                // If auth exists but firestore record is missing, create a default Farmer role
                val defaultProfile = UserProfile(
                    uid = uid,
                    name = authResult.user?.displayName ?: "Farmer",
                    email = email,
                    role = UserRole.FARMER,
                    joinDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                )
                db!!.collection("users").document(uid).set(defaultProfile).await()
                Result.success(defaultProfile)
            } else {
                val profile = docSnap.toObject(UserProfile::class.java) ?: throw Exception("Failed parsing User Document.")
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        role: UserRole
    ): Result<UserProfile> {
        if (!firebaseInitialized || auth == null || db == null) {
            // Simulated registration fallback
            return try {
                kotlinx.coroutines.delay(1200)
                if (password.length < 6) {
                    throw Exception("Password must be at least 6 characters.")
                }
                
                val lowerEmail = email.lowercase().trim()
                if (simulatedUsersDb.containsKey(lowerEmail)) {
                    throw Exception("An account already exists with this email address.")
                }

                val newProfile = UserProfile(
                    uid = "sim_${System.currentTimeMillis()}",
                    name = name,
                    email = lowerEmail,
                    role = role,
                    joinDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                )
                simulatedUsersDb[lowerEmail] = newProfile
                simulatedUser = newProfile
                Result.success(newProfile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Real Firebase register and Firestore record save
        return try {
            val authResult = auth!!.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("User creation failed.")

            val profile = UserProfile(
                uid = uid,
                name = name,
                email = email,
                role = role,
                joinDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
            )
            
            // Save role & profile to Cloud Firestore database
            db!!.collection("users").document(uid).set(profile).await()
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forgotPassword(email: String): Result<Unit> {
        if (!firebaseInitialized || auth == null) {
            kotlinx.coroutines.delay(800)
            return Result.success(Unit) // Always succeed in simulated testing
        }
        return try {
            auth!!.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        if (!firebaseInitialized || auth == null) {
            simulatedUser = null
            return Result.success(Unit)
        }
        return try {
            auth!!.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): UserProfile? {
        if (!firebaseInitialized || auth == null || db == null) {
            return simulatedUser
        }
        val currentUser = auth!!.currentUser ?: return null
        return try {
            val docSnap = db!!.collection("users").document(currentUser.uid).get().await()
            docSnap.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
