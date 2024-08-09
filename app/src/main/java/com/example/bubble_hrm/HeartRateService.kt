package com.example.bubble_hrm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log

class HeartRateService : Service() {

    private val channelId = "HeartRateChannel"
    private val notificationId = 124

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val heartRate = intent?.getStringExtra("heartRate") ?: "Unknown"
//        Log.d("HeartRateService", "Starting foreground service with heart rate: $heartRate")

        createNotificationChannel()
        val notification = createNotification(heartRate)
//        Log.d("HeartRateService", "Notification created: $notification")

        startForeground(notificationId, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Heart Rate Monitor"
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d("HeartRateService", "Notification channel created")
        }
    }

    private fun createNotification(heartRate: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Heart Rate Monitor")
            .setContentText("Current Heart Rate: $heartRate")
//            .setSmallIcon(R.drawable.ic_heart)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Test with a default icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Ensure visibility is set
            .build()
    }
}
