package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.state.AppStateManager
import com.example.ui.FloatingOverlayContent
import com.example.ui.theme.MyApplicationTheme

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingOverlayService onCreate")
        AppStateManager.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted. Stopping service.")
            stopSelf()
            return
        }

        startForegroundServiceNotification()
        showOverlayWindow()
        AppStateManager.setOverlayRunning(true)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "reply_float_ai_channel"
        val channelName = "ReplyFloatAi Floating Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ReplyFloatAi floating bar active over apps"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ReplyFloatAi Active")
            .setContentText("Floating bar is running over other apps")
            .setSmallIcon(R.drawable.ic_stat_reply_float)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showOverlayWindow() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 40
                y = 300
            }

            lifecycleOwner = OverlayLifecycleOwner()
            lifecycleOwner?.start()

            floatingView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)

                setContent {
                    MyApplicationTheme {
                        FloatingOverlayContent(
                            onDragDelta = { dx, dy ->
                                this@FloatingOverlayService.windowLayoutParams?.let { params ->
                                    params.x = (params.x + dx.toInt()).coerceAtLeast(0)
                                    params.y = (params.y + dy.toInt()).coerceAtLeast(0)
                                    try {
                                        windowManager?.updateViewLayout(floatingView, params)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to update overlay position", e)
                                    }
                                }
                            },
                            onCloseService = {
                                stopSelf()
                            }
                        )
                    }
                }
            }

            windowManager?.addView(floatingView, windowLayoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view to WindowManager", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingOverlayService onDestroy")
        AppStateManager.setOverlayRunning(false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground notification", e)
        }

        try {
            if (floatingView != null && windowManager != null) {
                windowManager?.removeView(floatingView)
                floatingView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay view", e)
        }

        lifecycleOwner?.stop()
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    companion object {
        private const val TAG = "FloatingOverlayService"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }
}
