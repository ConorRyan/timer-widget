package io.github.conorryan.timerwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.conorryan.timerwidget.MainActivity
import io.github.conorryan.timerwidget.R
import io.github.conorryan.timerwidget.data.TimerDef
import io.github.conorryan.timerwidget.data.TimerStore
import io.github.conorryan.timerwidget.util.formatHms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val job = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { TimerWidgetViews.update(context, it) }
            } finally {
                job.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.clearTimers(context, appWidgetIds.toList())
    }
}

object TimerWidgetViews {

    suspend fun update(context: Context, widgetId: Int) {
        val def = WidgetPrefs.timerIdFor(context, widgetId)
            ?.let { TimerStore(context).timerById(it) }
        update(context, widgetId, def)
    }

    /**
     * Re-renders every installed instance of [TimerWidget] from current data.
     * Called after timer edits so a widget's emoji/name/time changes show up
     * without needing a tap; running timers keep their live countdowns
     * ([build] prefers the running entry over the stored definition).
     */
    suspend fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidget::class.java))
        ids.forEach { update(context, it) }
    }

    fun update(context: Context, widgetId: Int, def: TimerDef?) {
        val views = build(context, widgetId, def)
        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
    }

    fun build(context: Context, widgetId: Int, def: TimerDef?): RemoteViews {
        val entry = TimerService.active[widgetId]
        val time = when {
            entry?.finished == true -> formatHms(entry.totalSeconds)
            entry != null -> formatHms(entry.displaySeconds)
            def != null -> formatHms(def.totalSeconds)
            else -> context.getString(R.string.configure_hint)
        }
        val name = def?.label(context.getString(R.string.app_name))
            ?: context.getString(R.string.no_timer)
        val click = if (def == null) {
            openAppPendingIntent(context, widgetId)
        } else {
            togglePendingIntent(context, widgetId)
        }
        return RemoteViews(context.packageName, R.layout.timer_widget).apply {
            setTextViewText(R.id.timer_name, name)
            setTextViewText(R.id.timer_time, time)
            setOnClickPendingIntent(R.id.widget_root, click)
        }
    }

    fun build(
        context: Context,
        name: String,
        time: String,
        click: PendingIntent,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.timer_widget).apply {
        setTextViewText(R.id.timer_name, name)
        setTextViewText(R.id.timer_time, time)
        setOnClickPendingIntent(R.id.widget_root, click)
    }

    // Must be a foreground-service PendingIntent: when the app has been force
    // closed it counts as background, and the system rejects a plain
    // startService dispatch ("Background start not allowed"). The launcher is
    // visible, so a foreground-service start is exempt from the background
    // FGS restrictions and works even with the process dead.
    fun togglePendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_TOGGLE)
            .putExtra(TimerService.EXTRA_WIDGET_ID, widgetId)
        return PendingIntent.getForegroundService(context, widgetId, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppPendingIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, widgetId, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
