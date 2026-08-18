package com.poultryguard.ai.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.poultryguard.ai.data.model.UserProfile
import com.poultryguard.ai.data.model.FarmEvent
import com.poultryguard.ai.data.model.HardwareKit
import com.poultryguard.ai.data.model.Batch

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
    fun cacheTelemetry(temp: Float?, humid: Float?, ammonia: Float?, sound: Float?) {
        prefs.edit().apply {
            if (temp != null) putFloat(KEY_LAST_TEMP, temp) else remove(KEY_LAST_TEMP)
            if (humid != null) putFloat(KEY_LAST_HUMID, humid) else remove(KEY_LAST_HUMID)
            if (ammonia != null) putFloat(KEY_LAST_AMMONIA, ammonia) else remove(KEY_LAST_AMMONIA)
            if (sound != null) putFloat(KEY_LAST_SOUND, sound) else remove(KEY_LAST_SOUND)
        }.apply()
    }

    fun getCachedTelemetry(): Map<String, Float?> {
        val temp = if (prefs.contains(KEY_LAST_TEMP)) prefs.getFloat(KEY_LAST_TEMP, 0f) else null
        val humid = if (prefs.contains(KEY_LAST_HUMID)) prefs.getFloat(KEY_LAST_HUMID, 0f) else null
        val ammonia = if (prefs.contains(KEY_LAST_AMMONIA)) prefs.getFloat(KEY_LAST_AMMONIA, 0f) else null
        val sound = if (prefs.contains(KEY_LAST_SOUND)) prefs.getFloat(KEY_LAST_SOUND, 0f) else null
        return mapOf(
            "temp" to temp,
            "humid" to humid,
            "ammonia" to ammonia,
            "sound" to sound
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
        val json = prefs.getString("cached_farm_events", null) ?: return emptyList()
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

    fun getHardwareKits(): List<HardwareKit> {
        val json = prefs.getString("hardware_kits", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<HardwareKit>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHardwareKits(kits: List<HardwareKit>) {
        try {
            val json = gson.toJson(kits)
            prefs.edit().putString("hardware_kits", json).apply()
        } catch (e: Exception) {
        }
    }

    fun addHardwareKit(kit: HardwareKit) {
        val list = getHardwareKits().toMutableList()
        if (kit.isActive) {
            for (i in list.indices) {
                list[i] = list[i].copy(isActive = false)
            }
        }
        list.removeAll { it.gatewayId == kit.gatewayId }
        list.add(kit)
        saveHardwareKits(list)
    }

    fun deleteHardwareKit(gatewayId: String) {
        val list = getHardwareKits().toMutableList()
        list.removeAll { it.gatewayId == gatewayId }
        saveHardwareKits(list)
    }

    fun setActiveGatewayId(gatewayId: String) {
        val list = getHardwareKits().toMutableList()
        for (i in list.indices) {
            list[i] = list[i].copy(isActive = list[i].gatewayId == gatewayId)
        }
        saveHardwareKits(list)
    }

    fun cacheActiveBatch(batch: Batch?) {
        try {
            if (batch == null) {
                prefs.edit().remove("cached_active_batch").apply()
            } else {
                val json = gson.toJson(batch)
                prefs.edit().putString("cached_active_batch", json).apply()
            }
        } catch (e: Exception) {
            // handle error gracefully
        }
    }

    fun getCachedActiveBatch(): Batch? {
        val json = prefs.getString("cached_active_batch", null) ?: return null
        return try {
            gson.fromJson(json, Batch::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
