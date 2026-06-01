package com.poultryguard.ai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

enum class AppLanguage {
    ENGLISH,
    HINDI,
    MARATHI
}

// Complete dictionary mappings for agricultural UI parameters
object Translations {
    private val englishMap = mapOf(
        "app_title" to "Poultry Guard AI",
        "welcome" to "Hello, Farmer Joe",
        "live" to "Live",
        "mqtt_sync" to "Syncing...",
        "dashboard" to "Monitor",
        "controls" to "Controls",
        "alerts" to "Alerts",
        "profile" to "Profile",
        "temp" to "Temperature",
        "humid" to "Humidity",
        "ammonia" to "Ammonia Gas",
        "sound" to "Acoustic Panic",
        "exhaust_fans" to "Exhaust Fans",
        "brooder_heater" to "Brooder Heater",
        "cooling_misters" to "Cooling Misters",
        "shed_lights" to "Shed Lights",
        "environmental_controls" to "Environmental Controls",
        "disease_risk" to "AI Disease Risk Level",
        "mortality_mgmt" to "Flock Mortality Tracker",
        "log_death" to "Log Bird Deaths",
        "symptoms" to "Observed Symptoms",
        "submit_log" to "Submit Mortality Log",
        "active_shed" to "Shed #4 (Broilers - Day 18)",
        "healthy_stock" to "Healthy Stock",
        "sim_deck" to "Telemetry Simulator Deck",
        "immunization" to "Immunization Calendar",
        "active" to "Active",
        "idle" to "Idle"
    )

    private val hindiMap = mapOf(
        "app_title" to "पोल्ट्री गार्ड एआई",
        "welcome" to "नमस्ते, किसान जो",
        "live" to "सक्रिय",
        "mqtt_sync" to "सिंक हो रहा है...",
        "dashboard" to "निगरानी",
        "controls" to "नियंत्रण",
        "alerts" to "अलर्ट",
        "profile" to "प्रोफ़ाइल",
        "temp" to "तापमान",
        "humid" to "नमी (आर्द्रता)",
        "ammonia" to "अमोनिया गैस",
        "sound" to "ध्वनि (शोर)",
        "exhaust_fans" to "निकास पंखे",
        "brooder_heater" to "ब्रूडर हीटर",
        "cooling_misters" to "कूलिंग मिस्टर्स",
        "shed_lights" to "शेड लाइट्स",
        "environmental_controls" to "पर्यावरण नियंत्रण",
        "disease_risk" to "एआई रोग जोखिम स्तर",
        "mortality_mgmt" to "पक्षी मृत्यु दर ट्रैकर",
        "log_death" to "पक्षी मृत्यु दर्ज करें",
        "symptoms" to "देखे गए लक्षण",
        "submit_log" to "लॉग सबमिट करें",
        "active_shed" to "शेड #4 (ब्रोइलर - दिन 18)",
        "healthy_stock" to "स्वस्थ पक्षी",
        "sim_deck" to "टेलीमेट्री सिम्युलेटर डेक",
        "immunization" to "टीकाकरण कैलेंडर",
        "active" to "चालू",
        "idle" to "निष्क्रिय"
    )

    private val marathiMap = mapOf(
        "app_title" to "पोल्ट्री गार्ड एआय",
        "welcome" to "नमस्कार, शेतकरी जो",
        "live" to "सक्रिय",
        "mqtt_sync" to "सिंक होत आहे...",
        "dashboard" to "निरीक्षण",
        "controls" to "नियंत्रण",
        "alerts" to "अलर्ट",
        "profile" to "प्रोफाईल",
        "temp" to "तापमान",
        "humid" to "दमटपणा (आर्द्रता)",
        "ammonia" to "अमोनिया वायू",
        "sound" to "आवाज (गोंगाट)",
        "exhaust_fans" to "एक्झॉस्ट फॅन",
        "brooder_heater" to "ब्रूडर हीटर",
        "cooling_misters" to "कूलिंग मिस्टर्स",
        "shed_lights" to "शेड लाइट्स",
        "environmental_controls" to "पर्यावरण नियंत्रण",
        "disease_risk" to "एआय रोग जोखीम पातळी",
        "mortality_mgmt" to "पक्षी मृत्यु दर ट्रॅकर",
        "log_death" to "पक्षी मृत्यू नोंदवा",
        "symptoms" to "आढळलेली लक्षणे",
        "submit_log" to "नोंद सबमिट करा",
        "active_shed" to "शेड #४ (ब्रोइलर - दिवस १८)",
        "healthy_stock" to "निरोगी पक्षी",
        "sim_deck" to "टेलीमेट्री सिम्युलेटर डेक",
        "immunization" to "लसीकरण वेळापत्रक",
        "active" to "सुरू",
        "idle" to "बंद"
    )

    fun translate(key: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.ENGLISH -> englishMap[key] ?: key
            AppLanguage.HINDI -> hindiMap[key] ?: key
            AppLanguage.MARATHI -> marathiMap[key] ?: key
        }
    }
}

// CompositionLocal to allow clean theme access inside Composable hierarchy
val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun stringResource(key: String): String {
    val currentLang = LocalAppLanguage.current
    return Translations.translate(key, currentLang)
}
