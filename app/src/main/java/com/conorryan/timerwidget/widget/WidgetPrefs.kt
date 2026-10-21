package com.conorryan.timerwidget.widget

import android.content.Context

object WidgetPrefs {

    private const val FILE = "widgets"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun timerIdFor(context: Context, widgetId: Int): String? =
        prefs(context).getString("widget_$widgetId", null)

    fun setTimer(context: Context, widgetId: Int, timerId: String) {
        prefs(context).edit().putString("widget_$widgetId", timerId).apply()
    }

    fun clearTimer(context: Context, widgetId: Int) {
        prefs(context).edit().remove("widget_$widgetId").apply()
    }

    fun clearTimers(context: Context, widgetIds: List<Int>) {
        val editor = prefs(context).edit()
        widgetIds.forEach { editor.remove("widget_$it") }
        editor.apply()
    }
}
