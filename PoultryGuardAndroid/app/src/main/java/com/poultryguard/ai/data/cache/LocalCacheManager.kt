package com.poultryguard.ai.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.poultryguard.ai.data.model.UserProfile

class LocalCacheManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("poultry_guard_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_USER_PROFILE = "cached_user_profile"
        private const val KEY_LAST_TEMP = "last_cached_temp"
        private const val KEY_LAST_HUMID = "last_cached_humid"
        private const val KEY_LAST_AMMONIA = "last_cached_ammonia"
        private const val KEY_LAST_SOUND = "last_cached_sound"
        private const val KEY_MORTALITIES = "cached_mortalities_count"
    }

    // Cache User Profiles
    fun cacheUserProfile(profile: UserProfile) {
        try {
            val json = gson.toJson(profile)
            prefs.edit().putString(KEY_USER_PROFILE, json).apply()
        } catch (e: Exception) {
            // Graceful log or fallback
        }
    }

    fun getCachedUserProfile(): UserProfile? {
        val json = prefs.getString(KEY_USER_PROFILE, null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Cache Telemetry Readings
    fun cacheTelemetry(temp: Float, humid: Float, ammonia: Float, sound: Float) {
        prefs.edit().apply {
            putFloat(KEY_LAST_TEMP, temp)
            putFloat(KEY_LAST_HUMID, humid)
            putFloat(KEY_LAST_AMMONIA, ammonia)
            putFloat(KEY_LAST_SOUND, sound)
        }.apply()
    }

    fun getCachedTelemetry(): Map<String, Float> {
        return mapOf(
            "temp" to prefs.getFloat(KEY_LAST_TEMP, 24.2f),
            "humid" to prefs.getFloat(KEY_LAST_HUMID, 61.5f),
            "ammonia" to prefs.getFloat(KEY_LAST_AMMONIA, 12.0f),
            "sound" to prefs.getFloat(KEY_LAST_SOUND, 54.0f)
        )
    }

    // Persist Bird Mortalities
    fun cacheLoggedMortalities(count: Int) {
        val currentCount = getCachedMortalities()
        prefs.edit().putInt(KEY_MORTALITIES, currentCount + count).apply()
    }

    fun getCachedMortalities(): Int {
        return prefs.getInt(KEY_MORTALITIES, 0)
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
