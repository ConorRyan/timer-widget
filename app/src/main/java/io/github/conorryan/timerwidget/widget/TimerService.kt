package io.github.conorryan.timerwidget.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import io.github.conorryan.timerwidget.MainActivity
import io.github.conorryan.timerwidget.R
import io.github.conorryan.timerwidget.data.TimerStore
import io.github.conorryan.timerwidget.util.formatHms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TimerService : Service() {

    class Active(
        val timerId: String,
        val name: String,
        val endAtMs: Long,
        val totalMs: Long,
    ) {
        var finished = false
        val remainingMs: Long get() = (endAtMs - System.currentTimeMillis()).coerceAtLeast(0)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val widgetId = intent?.getIntExtra(
            EXTRA_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        when (intent?.action) {
            ACTION_TOGGLE -> when {
                widgetId == AppWidgetManager.INVALID_APPWIDGET_ID -> stopIfEmpty()
                TimerService.active.containsKey(widgetId) -> clear(widgetId)
                else -> start(widgetId)
            }
            ACTION_CLEAR_ALL -> clearAll()
            else -> stopIfEmpty()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun start(widgetId: Int) {
        ensureChannels(applicationContext)
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        if (type != 0) {
            startForeground(NOTIF_ID, foregroundNotification(), type)
        } else {
            startForeground(NOTIF_ID, foregroundNotification())
        }
        scope.launch {
            val timerId = WidgetPrefs.timerIdFor(applicationContext, widgetId)
                ?: run { stopIfEmpty(); return@launch }
            val def = TimerStore(applicationContext).timerById(timerId)
                ?: run { stopIfEmpty(); return@launch }
            active[widgetId] = Active(
                timerId = timerId,
                name = def.name.ifBlank { getString(R.string.timer_fallback) },
                endAtMs = System.currentTimeMillis() + def.totalSeconds * 1000L,
                totalMs = def.totalSeconds * 1000L,
            )
            ensureTicker()
        }
    }

    private fun clear(widgetId: Int) {
        active.remove(widgetId)
        scope.launch { TimerWidgetViews.update(applicationContext, widgetId) }
        stopIfEmpty()
    }

    private fun clearAll() {
        val ids = active.keys.toList()
        active.clear()
        scope.launch { ids.forEach { TimerWidgetViews.update(applicationContext, it) } }
        stopIfEmpty()
    }

    private fun stopIfEmpty() {
        if (active.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateForegroundNotification()
        }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (active.values.any { !it.finished }) {
                val now = System.currentTimeMillis()
                active.entries
                    .filter { !it.value.finished && it.value.endAtMs <= now }
                    .forEach { finishTimer(it.key, it.value) }
                active.entries
                    .filter { !it.value.finished }
                    .forEach { (widgetId, entry) -> notifyWidget(widgetId, entry) }
                updateForegroundNotification()
                delay(1000 - System.currentTimeMillis() % 1000)
            }
        }
    }

    private fun finishTimer(widgetId: Int, entry: Active) {
        entry.finished = true
        notifyWidget(widgetId, entry)
        postAlarmNotification(widgetId, entry)
    }

    private fun notifyWidget(widgetId: Int, entry: Active) {
        val time = if (entry.finished) {
            getString(R.string.widget_done)
        } else {
            formatHms(((entry.remainingMs + 999) / 1000).toInt())
        }
        val views = TimerWidgetViews.build(
            applicationContext,
            name = entry.name,
            time = time,
            click = TimerWidgetViews.togglePendingIntent(applicationContext, widgetId),
        )
        AppWidgetManager.getInstance(applicationContext).updateAppWidget(widgetId, views)
    }

    private fun foregroundNotification(): Notification {
        val running = active.values.filter { !it.finished }.minByOrNull { it.endAtMs }
        val (title, text) = if (running == null) {
            getString(R.string.timer_done_title) to (active.values.firstOrNull()?.name ?: "")
        } else {
            running.name to formatHms(((running.remainingMs + 999) / 1000).toInt())
        }
        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, TimerService::class.java).setAction(ACTION_CLEAR_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.cancel), cancel)
            .build()
    }

    private fun updateForegroundNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, foregroundNotification())
        }
    }

    private fun postAlarmNotification(widgetId: Int, entry: Active) {
        val openApp = PendingIntent.getActivity(
            this,
            widgetId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.timer_done_title))
            .setContentText(entry.name)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(ALARM_NOTIF_BASE + widgetId, notification)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.conorryan.timerwidget.action.TOGGLE"
        const val ACTION_CLEAR_ALL = "io.github.conorryan.timerwidget.action.CLEAR_ALL"
        const val EXTRA_WIDGET_ID = "widgetId"
        const val NOTIF_ID = 1
        const val ALARM_NOTIF_BASE = 1000
        const val CHANNEL_TIMER = "timer"
        const val CHANNEL_ALARM = "timer_alarm"

        val active = ConcurrentHashMap<Int, Active>()

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_TIMER) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_TIMER,
                        context.getString(R.string.channel_timer),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            if (nm.getNotificationChannel(CHANNEL_ALARM) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ALARM,
                    context.getString(R.string.channel_alarm),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                channel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                channel.enableVibration(true)
                nm.createNotificationChannel(channel)
            }
        }
    }
}
