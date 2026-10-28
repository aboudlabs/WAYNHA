package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class WaynhaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        getSharedPreferences("waynha_push_v1", Context.MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "وينها"
        val body = message.notification?.body ?: message.data["body"] ?: "عندك إشعار جديد"
        showNotification(title, body, message.data)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val channelId = "waynha_notifications"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channelId) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "إشعارات وينها", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data["related_conversation_id"]?.takeIf { it.isNotBlank() }?.let {
                putExtra("related_conversation_id", it)
            }
            data["related_report_id"]?.takeIf { it.isNotBlank() }?.let {
                putExtra("related_report_id", it)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, (data["notification_id"] ?: System.currentTimeMillis().toString()).hashCode(),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(
                (data["notification_id"] ?: System.currentTimeMillis().toString()).hashCode(),
                notification
            )
        } catch (_: SecurityException) {
            // Android 13+ requires the user to grant notification permission.
        }
    }
}
