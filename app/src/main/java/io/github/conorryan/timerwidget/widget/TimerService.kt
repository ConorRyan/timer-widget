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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.conorryan.timerwidget.MainActivity
import io.github.conorryan.timerwidget.R
import io.github.conorryan.timerwidget.data.TimerStore
import io.github.conorryan.timerwidget.util.formatHms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TimerService : Service() {

    class Active(
        val timerId: String,
        val name: String,
        val emoji: String,
        val endAtMs: Long,
        val totalMs: Long,
        val soundUri: String? = null,
        val soundName: String? = null,
    ) {
        var finished = false
        val remainingMs: Long get() = remainingMsAt(System.currentTimeMillis())
        val totalSeconds: Int get() = (totalMs / 1000).toInt()

        /** Seconds to show while counting down: one less than the ceiling, so a just-started timer visibly drops from its total. */
        val displaySeconds: Int get() = displaySecondsAt(System.currentTimeMillis())

        fun remainingMsAt(nowMs: Long): Long = (endAtMs - nowMs).coerceAtLeast(0)

        fun displaySecondsAt(nowMs: Long): Int =
            (((remainingMsAt(nowMs) + 999) / 1000).toInt() - 1).coerceAtLeast(0)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: kotlinx.coroutines.Job? = null

    /** Widget whose alarm last finished; its ring is shown in the foreground slot. */
    private var lastFinishedWidgetId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The widget tap arrives via PendingIntent.getForegroundService, which
        // obligates the service to startForeground on EVERY delivery - also the
        // ones that stop or clear a timer - or the system raises
        // RemoteServiceException.
        ensureChannels(applicationContext)
        startForegroundCompat()
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
            ACTION_TOGGLE_TIMER -> intent.getStringExtra(EXTRA_TIMER_ID)
                ?.let(::toggleTimer)
                ?: stopIfEmpty()
            ACTION_CLEAR_ALL -> clearAll()
            ACTION_STOP_ALARM -> stopAlarm(widgetId)
            else -> stopIfEmpty()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        alarmPlayer.stopAll()
        scope.cancel()
        super.onDestroy()
    }

    private fun start(widgetId: Int) {
        scope.launch {
            val timerId = WidgetPrefs.timerIdFor(applicationContext, widgetId)
                ?: run { stopIfEmpty(); return@launch }
            startRun(widgetId, timerId)
        }
    }

    /**
     * Tap on a timer in the app: stops every run of [timerId] (app- or
     * widget-started, ringing or not) if there is one, otherwise starts a new
     * app run under a fresh [isAppRun] key.
     */
    private fun toggleTimer(timerId: String) {
        val keys = active.filterValues { it.timerId == timerId }.keys
        if (keys.isEmpty()) {
            scope.launch { startRun(nextAppKey.incrementAndGet(), timerId) }
        } else {
            keys.forEach(::stopAlarm)
        }
    }

    private suspend fun startRun(key: Int, timerId: String) {
        val def = TimerStore(applicationContext).timerById(timerId)
            ?: run { stopIfEmpty(); return }
        active[key] = Active(
            timerId = timerId,
            name = def.name.ifBlank { getString(R.string.timer_fallback) },
            emoji = def.emoji,
            endAtMs = System.currentTimeMillis() + def.totalSeconds * 1000L,
            totalMs = def.totalSeconds * 1000L,
            soundUri = def.soundUri,
            soundName = def.soundName,
        )
        notifyWidget(key, active.getValue(key))
        bumpVersion()
        ensureTicker()
    }

    private fun clear(widgetId: Int) {
        active.remove(widgetId)
        if (lastFinishedWidgetId == widgetId) lastFinishedWidgetId = null
        alarmPlayer.stop(widgetId)
        bumpVersion()
        // Refresh before stopping: stopping cancels the scope, which would kill this
        // coroutine mid-render and leave the widget stuck at the paused countdown.
        scope.launch {
            if (!isAppRun(widgetId)) TimerWidgetViews.update(applicationContext, widgetId)
            stopIfEmpty()
        }
    }

    private fun clearAll() {
        val ids = active.keys.toList()
        active.clear()
        lastFinishedWidgetId = null
        alarmPlayer.stopAll()
        bumpVersion()
        scope.launch {
            ids.filterNot(::isAppRun).forEach { TimerWidgetViews.update(applicationContext, it) }
            stopIfEmpty()
        }
    }

    /**
     * Silences a ringing alarm, dismisses its notification, and clears the
     * finished timer. Without the clear, the stale entry in [active] makes the
     * next widget tap toggle the leftover off instead of starting a new run.
     */
    private fun stopAlarm(widgetId: Int) {
        alarmPlayer.stop(widgetId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            stopIfEmpty()
            return
        }
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(ALARM_NOTIF_BASE + widgetId)
        }
        clear(widgetId)
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
                bumpVersion()
                delay(1000 - System.currentTimeMillis() % 1000)
            }
        }
    }

    private fun finishTimer(widgetId: Int, entry: Active) {
        entry.finished = true
        lastFinishedWidgetId = widgetId
        notifyWidget(widgetId, entry)
        alarmPlayer.start(applicationContext, widgetId, entry.soundUri)
        postAlarmNotification(widgetId, entry)
    }

    private fun notifyWidget(widgetId: Int, entry: Active) {
        if (isAppRun(widgetId)) return
        val time = if (entry.finished) {
            formatHms(entry.totalSeconds)
        } else {
            formatHms(entry.displaySeconds)
        }
        val views = TimerWidgetViews.build(
            applicationContext,
            name = entry.emoji.ifBlank { entry.name },
            time = time,
            click = TimerWidgetViews.togglePendingIntent(applicationContext, widgetId),
        )
        AppWidgetManager.getInstance(applicationContext).updateAppWidget(widgetId, views)
    }

    private fun startForegroundCompat() {
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
    }

    private fun foregroundNotification(): Notification {
        val running = active.values.filter { !it.finished }.minByOrNull { it.endAtMs }
        if (running == null) {
            // Nothing counts down anymore: the foreground slot shows the ringing
            // alarm instead, so the finished timer is a single notification.
            // The service must stay foreground while the ring plays, and a
            // foreground service needs a notification to occupy that slot.
            val alarmId = lastFinishedWidgetId
            if (alarmId != null) {
                val ringing = active[alarmId]?.takeIf { it.finished }
                if (ringing != null) {
                    return alarmNotification(alarmId, ringing, ongoing = true)
                }
            }
        }
        val (title, text) = if (running == null) {
            getString(R.string.timer_done_title) to (active.values.firstOrNull()?.name ?: "")
        } else {
            running.name to formatHms(running.displaySeconds)
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
        if (active.values.none { !it.finished }) {
            // No countdown left to show: the foreground notification is the
            // alarm, so re-render it rather than post a duplicate.
            updateForegroundNotification()
            return
        }
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(ALARM_NOTIF_BASE + widgetId, alarmNotification(widgetId, entry, ongoing = false))
        }
    }

    private fun alarmNotification(widgetId: Int, entry: Active, ongoing: Boolean): Notification {
        val stop = PendingIntent.getService(
            this,
            widgetId,
            Intent(this, TimerService::class.java)
                .setAction(ACTION_STOP_ALARM)
                .putExtra(EXTRA_WIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openApp = PendingIntent.getActivity(
            this,
            widgetId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ALARM_QUIET)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.timer_done_title))
            .setContentText(entry.name)
            .setContentIntent(openApp)
            // The ring plays via AlarmPlayer; every dismissal path must stop it.
            .setDeleteIntent(stop)
            .addAction(0, getString(R.string.stop_alarm), stop)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.conorryan.timerwidget.action.TOGGLE"
        const val ACTION_CLEAR_ALL = "io.github.conorryan.timerwidget.action.CLEAR_ALL"
        const val ACTION_STOP_ALARM = "io.github.conorryan.timerwidget.action.STOP_ALARM"
        const val EXTRA_WIDGET_ID = "widgetId"
        const val NOTIF_ID = 1
        const val ALARM_NOTIF_BASE = 1000
        const val CHANNEL_TIMER = "timer"
        const val CHANNEL_ALARM_QUIET = "timer_alarm_quiet"

        const val ACTION_TOGGLE_TIMER = "io.github.conorryan.timerwidget.action.TOGGLE_TIMER"
        const val EXTRA_TIMER_ID = "timerId"

        /**
         * Running timers by run key: a widget ID (always positive) for widget
         * taps, or a negative key from [nextAppKey] for runs started in the app.
         */
        val active = ConcurrentHashMap<Int, Active>()
        val alarmPlayer = AlarmPlayer()

        // Counts up from Int.MIN_VALUE so app keys stay negative and their
        // alarm notification IDs (ALARM_NOTIF_BASE + key) never meet a widget's.
        private val nextAppKey = AtomicInteger(Int.MIN_VALUE)

        private val _version = MutableStateFlow(0L)

        /** Bumps whenever [active] changes and on every ticker second, so the app can redraw. */
        val version: StateFlow<Long> = _version.asStateFlow()

        private fun bumpVersion() = _version.update { it + 1 }

        fun isAppRun(key: Int): Boolean = key < 0

        /** Starts [timerId] from the app, or stops every run of it if one is going. */
        fun toggleTimer(context: Context, timerId: String) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_TOGGLE_TIMER)
                .putExtra(EXTRA_TIMER_ID, timerId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops one run (and its alarm, if ringing) by its key in [active]. */
        fun stopRun(context: Context, key: Int) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_STOP_ALARM)
                .putExtra(EXTRA_WIDGET_ID, key)
            ContextCompat.startForegroundService(context, intent)
        }

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
            // Legacy alarm channels carried their sound on the channel, which was
            // unreliable; the ring is now played by AlarmPlayer. A channel's sound
            // is immutable, so delete them instead of leaving dead categories
            // (or double sounds) in system settings.
            nm.notificationChannels
                .filter { it.id != CHANNEL_ALARM_QUIET && it.id.startsWith("timer_alarm") }
                .forEach { nm.deleteNotificationChannel(it.id) }
            if (nm.getNotificationChannel(CHANNEL_ALARM_QUIET) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ALARM_QUIET,
                    context.getString(R.string.channel_alarm),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                channel.setSound(null, null)
                channel.enableVibration(true)
                nm.createNotificationChannel(channel)
            }
        }
    }
}
