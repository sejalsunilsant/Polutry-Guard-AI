package com.poultryguard.ai.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.poultryguard.ai.MainActivity
import com.poultryguard.ai.R
import com.poultryguard.ai.data.cache.LocalCacheManager
import com.poultryguard.ai.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PoultryGuardFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "poultry_guard_alerts_channel"
        const val CHANNEL_NAME = "Poultry Guard Health & Farm Alerts"
        const val CHANNEL_DESC = "Real-time critical alerts for environmental thresholds, mortality, and disease detection."

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val cacheManager = LocalCacheManager(applicationContext)
        cacheManager.saveFcmToken(token)

        // Attempt to sync new token to backend for current logged-in farmer/vet
        serviceScope.launch {
            try {
                val authRepo = SupabaseAuthRepository(applicationContext)
                authRepo.updateFcmToken(token)
            } catch (e: Exception) {
                // Background sync failure handled gracefully; will retry upon app startup
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Extract structured parameters from data payload (or fallback to notification fields)
        val farmName = remoteMessage.data["farmName"] ?: "Poultry Farm"
        val alertType = remoteMessage.data["alertType"] ?: "ALERT"
        val severity = remoteMessage.data["severity"] ?: "HIGH"
        val rawTitle = remoteMessage.data["title"] ?: remoteMessage.notification?.title
        val rawMessage = remoteMessage.data["message"] ?: remoteMessage.notification?.body ?: "Attention required in poultry shed."
        val batchId = remoteMessage.data["batchId"]
        val deviceId = remoteMessage.data["deviceId"]

        val displayTitle = rawTitle ?: "[$farmName] $severity ${alertType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}"
        val displayBody = rawMessage

        showNotification(
            farmName = farmName,
            alertType = alertType,
            severity = severity,
            title = displayTitle,
            message = displayBody,
            batchId = batchId,
            deviceId = deviceId
        )
    }

    private fun showNotification(
        farmName: String,
        alertType: String,
        severity: String,
        title: String,
        message: String,
        batchId: String?,
        deviceId: String?
    ) {
        createNotificationChannel(applicationContext)

        // Intent to launch MainActivity with alert context
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_open_alerts", true)
            putExtra("extra_farm_name", farmName)
            putExtra("extra_alert_type", alertType)
            putExtra("extra_severity", severity)
            if (!batchId.isNullOrBlank()) putExtra("extra_batch_id", batchId)
            if (!deviceId.isNullOrBlank()) putExtra("extra_device_id", deviceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Color coding by severity
        val alertColor = when (severity.uppercase()) {
            "CRITICAL" -> Color.parseColor("#D32F2F") // Deep Red
            "HIGH" -> Color.parseColor("#E65100")     // Dark Orange
            "WARNING" -> Color.parseColor("#F57C00")  // Orange
            "MEDIUM" -> Color.parseColor("#FFA000")   // Amber
            else -> Color.parseColor("#1976D2")       // Blue
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
                    .setBigContentTitle(title)
                    .setSummaryText(farmName)
            )
            .setAutoCancel(true)
            .setColor(alertColor)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (e: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission check catch
        }
    }
}
