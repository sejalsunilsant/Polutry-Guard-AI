package com.poultryguard.ai.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.FarmEvent

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

    fun getCachedFarmEvents(): List<FarmEvent> {
        val json = prefs.getString("cached_farm_events", null)
        if (json == null) {
            val mocks = listOf(
                FarmEvent(
                    dateStr = "2026-05-25",
                    timeStr = "09:00",
                    type = com.poultryguard.ai.data.model.FarmEventType.CLEANING,
                    title = "Routine Shed Cleaning",
                    notes = "Litter turned and completely dried. Fans checked."
                ),
                FarmEvent(
                    dateStr = "2026-05-28",
                    timeStr = "10:30",
                    type = com.poultryguard.ai.data.model.FarmEventType.VACCINE,
                    title = "Gumboro Vaccine Administered",
                    notes = "Applied via drinking water lines."
                ),
                FarmEvent(
                    dateStr = "2026-06-01",
                    timeStr = "08:00",
                    type = com.poultryguard.ai.data.model.FarmEventType.VACCINE,
                    title = "Newcastle LaSota Booster",
                    notes = "Aerosol spray application in Shed #4."
                ),
                FarmEvent(
                    dateStr = "2026-06-03",
                    timeStr = "14:00",
                    type = com.poultryguard.ai.data.model.FarmEventType.FEEDING,
                    title = "High-Protein Starter Blend",
                    notes = "Feeding sweep audit completed for Shed #4."
                ),
                FarmEvent(
                    dateStr = "2026-06-05",
                    timeStr = "11:00",
                    type = com.poultryguard.ai.data.model.FarmEventType.VENTILATION,
                    title = "Exhaust Fan Speed Testing",
                    notes = "Verify fans at 100% max velocity.",
                    isScheduled = true,
                    recurrence = com.poultryguard.ai.data.model.RecurrenceType.WEEKLY
                )
            )
            cacheFarmEvents(mocks)
            return mocks
        }
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<FarmEvent>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cacheFarmEvents(events: List<FarmEvent>) {
        try {
            val json = gson.toJson(events)
            prefs.edit().putString("cached_farm_events", json).apply()
        } catch (e: Exception) {
            // fallback
        }
    }

    fun addFarmEvent(event: FarmEvent) {
        val current = getCachedFarmEvents().toMutableList()
        current.add(event)
        cacheFarmEvents(current)
    }

    fun getApiBaseUrl(): String {
        return prefs.getString("api_base_url", "http://10.0.2.2:5000/") ?: "http://10.0.2.2:5000/"
    }

    fun saveApiBaseUrl(url: String) {
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString("api_base_url", formattedUrl).apply()
    }
}
