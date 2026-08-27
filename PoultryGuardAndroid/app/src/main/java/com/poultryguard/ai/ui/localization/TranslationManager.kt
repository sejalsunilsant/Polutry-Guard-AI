package com.poultryguard.ai.ui.localization

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
        "welcome_prefix" to "Hello, ",
        "live" to "Live",
        "mqtt_sync" to "Syncing...",
        "dashboard" to "Monitor",
        "controls" to "Controls",
        "alerts" to "Guardian",
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
        "idle" to "Idle",
        "mortality" to "Mortality",
        "suspected_cause" to "Suspected Cause",
        "env_snapshot" to "Sensor Snapshot",
        "other" to "Other",
        "symptoms_custom" to "Custom Symptoms",
        "cause_distribution" to "Cause Distribution",
        "delete" to "Delete",
        "start_batch" to "Start New Batch",
        "batch_history" to "Batch History",
        "close_batch" to "Close/Sell Batch",
        "breed" to "Breed",
        "initial_count" to "Initial Bird Count",
        "current_count" to "Remaining Bird Count",
        "start_date" to "Start Date",
        "end_date" to "End Date",
        "batch_id" to "Batch ID",
        "no_active_batch" to "No active batch running.",
        "mortality_rate" to "Mortality Rate",
        "days_active" to "Days Active",
        "confirm_close_batch" to "Confirm Close/Sell Batch",
        "sell_close_action" to "Sell / Close"
    )

    private val hindiMap = mapOf(
        "app_title" to "पोल्ट्री गार्ड एआई",
        "welcome_prefix" to "नमस्ते, ",
        "live" to "सक्रिय",
        "mqtt_sync" to "सिंक हो रहा है...",
        "dashboard" to "निगरानी",
        "controls" to "नियंत्रण",
        "alerts" to "गार्डियन",
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
        "idle" to "निष्क्रिय",
        "mortality" to "मृत्यु दर",
        "suspected_cause" to "संदेहास्पद कारण",
        "env_snapshot" to "सेंसर स्नैपशॉट",
        "other" to "अन्य",
        "symptoms_custom" to "कस्टम लक्षण",
        "cause_distribution" to "मृत्यु कारण वितरण",
        "delete" to "हटाएं",
        "start_batch" to "नया बैच शुरू करें",
        "batch_history" to "बैच इतिहास",
        "close_batch" to "बैच बंद/बेचें",
        "breed" to "नस्ल",
        "initial_count" to "शुरुआती पक्षियों की संख्या",
        "current_count" to "शेष पक्षियों की संख्या",
        "start_date" to "शुरू होने की तिथि",
        "end_date" to "समाप्ति की तिथि",
        "batch_id" to "बैच आईडी",
        "no_active_batch" to "कोई सक्रिय बैच नहीं चल रहा है।",
        "mortality_rate" to "मृत्यु दर",
        "days_active" to "सक्रिय दिन",
        "confirm_close_batch" to "बैच बंद/बेचने की पुष्टि करें",
        "sell_close_action" to "बेचें / बंद करें"
    )

    private val marathiMap = mapOf(
        "app_title" to "पोल्ट्री गार्ड एआय",
        "welcome_prefix" to "नमस्कार, ",
        "live" to "सक्रिय",
        "mqtt_sync" to "सिंक होत आहे...",
        "dashboard" to "निरीक्षण",
        "controls" to "नियंत्रण",
        "alerts" to "गार्डियन",
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
        "idle" to "बंद",
        "mortality" to "मृत्यू दर",
        "suspected_cause" to "संशयित कारण",
        "env_snapshot" to "सेन्सर स्नॅपशॉट",
        "other" to "इतर",
        "symptoms_custom" to "कस्टम लक्षणे",
        "cause_distribution" to "मृत्यू कारण वितरण",
        "delete" to "काढून टाका",
        "start_batch" to "नवीन बॅच सुरू करा",
        "batch_history" to "बॅच इतिहास",
        "close_batch" to "बॅच बंद/विक्री करा",
        "breed" to "जात",
        "initial_count" to "सुरुवाती पक्षांची संख्या",
        "current_count" to "उर्वरित पक्षांची संख्या",
        "start_date" to "सुरू होण्याची तारीख",
        "end_date" to "शेवटची तारीख",
        "batch_id" to "बॅच आयडी",
        "no_active_batch" to "कोणतीही सक्रिय बॅच सुरू नाही.",
        "mortality_rate" to "मृत्यू दर",
        "days_active" to "सक्रिय दिवस",
        "confirm_close_batch" to "बॅच बंद/विक्रीची पुष्टी करा",
        "sell_close_action" to "विक्री / बंद करा"
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
