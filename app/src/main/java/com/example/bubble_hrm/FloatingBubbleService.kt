package com.example.bubble_hrm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingBubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var closeAppReceiver: BroadcastReceiver

    companion object {
        private const val CHANNEL_ID = "BubbleServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerCloseAppReceiver()

        // Create and start the foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "Bubble Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Bubble Service")
                .setContentText("The bubble service is running")
                .setSmallIcon(R.drawable.ic_heart) // Add your own small icon here
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerCloseAppReceiver() {
        closeAppReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == MainActivity.ACTION_CLOSE_APP) {
                    stopSelf()
                }
            }
        }
        registerReceiver(closeAppReceiver, IntentFilter(MainActivity.ACTION_CLOSE_APP))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingView == null) {
            createFloatingView()
        }
        val heartRate = intent?.getStringExtra("heartRate") ?: "Unknown"
        updateHeartRate(heartRate)
        
        return START_NOT_STICKY  // Changed from START_STICKY
    }

    private fun createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_bubble_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(floatingView, params)
    }

    private fun createNotification(heartRate: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heart Rate Monitor")
            .setContentText("Current Heart Rate: $heartRate")
            .setSmallIcon(R.drawable.ic_heart)
//            .setSmallIcon(android.R.drawable.ic_dialog_info) // Test with a default icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Ensure visibility is set
            .build()
    }

    private fun updateHeartRate(heartRate: String) {
        val heartRateTextView = floatingView?.findViewById<TextView>(R.id.tvHeartRate)
        if (heartRateTextView != null) {
            heartRateTextView.text = heartRate
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
        unregisterReceiver(closeAppReceiver)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
}