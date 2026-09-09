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
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.state.AppStateManager
import com.example.ui.FloatingOverlayView
import com.example.ui.theme.ReplyFloatTheme

class FloatingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var overlayComposeView: View? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val CHANNEL_ID = "replyfloat_overlay_channel"
        private const val NOTIFICATION_ID = 2001

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppStateManager.init(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        createNotificationChannel()
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        initOverlayView()
        AppStateManager.setOverlayRunning(true)
    }

    private var pendingDx = 0f
    private var pendingDy = 0f
    private var isFrameScheduled = false
    private val frameCallback = Choreographer.FrameCallback {
        isFrameScheduled = false
        val intX = pendingDx.toInt()
        val intY = pendingDy.toInt()
        if (intX != 0 || intY != 0) {
            pendingDx -= intX
            pendingDy -= intY
            val lp = windowLayoutParams
            val view = overlayComposeView
            if (lp != null && view != null) {
                lp.x += intX
                lp.y += intY
                runCatching { windowManager?.updateViewLayout(view, lp) }
            }
        }
    }

    private fun flushPendingDrag() {
        val intX = pendingDx.toInt()
        val intY = pendingDy.toInt()
        if (intX != 0 || intY != 0) {
            pendingDx -= intX
            pendingDy -= intY
            val lp = windowLayoutParams
            val view = overlayComposeView
            if (lp != null && view != null) {
                lp.x += intX
                lp.y += intY
                runCatching { windowManager?.updateViewLayout(view, lp) }
            }
        }
    }

    private fun initOverlayView() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }
            windowLayoutParams = params

            val composeView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
                setViewTreeLifecycleOwner(this@FloatingOverlayService)
                setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
                setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

                setContent {
                    ReplyFloatTheme {
                        FloatingOverlayView(
                            context = this@FloatingOverlayService,
                            onDrag = { dx, dy ->
                                pendingDx += dx
                                pendingDy += dy
                                if (!isFrameScheduled) {
                                    isFrameScheduled = true
                                    Choreographer.getInstance().postFrameCallback(frameCallback)
                                }
                            },
                            onDragEnd = {
                                flushPendingDrag()
                            },
                            onClose = {
                                stopSelf()
                            }
                        )
                    }
                }
            }

            overlayComposeView = composeView
            windowManager?.addView(composeView, params)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ReplyFloat Overlay Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ReplyFloat smart suggestions floating over messenger apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ReplyFloat Assistant Active")
            .setContentText("Detecting conversation prompts in whitelisted messaging apps")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        if (isFrameScheduled) {
            runCatching { Choreographer.getInstance().removeFrameCallback(frameCallback) }
            isFrameScheduled = false
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()

        overlayComposeView?.let {
            windowManager?.removeView(it)
            overlayComposeView = null
        }
        QuestionDetectorAccessibilityService.resetScanningState()
        AppStateManager.setOverlayRunning(false)
        super.onDestroy()
    }
}
