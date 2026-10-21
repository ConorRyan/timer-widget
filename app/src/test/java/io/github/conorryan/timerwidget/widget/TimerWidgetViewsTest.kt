package io.github.conorryan.timerwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.timerwidget.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerWidgetViewsTest {

    @Test
    fun widgetToggleDispatchesAsForegroundServiceStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pendingIntent = TimerWidgetViews.togglePendingIntent(context, 7)

        val shadow = shadowOf(pendingIntent)
        assertTrue(shadow.isForegroundServiceIntent())
        val intent = shadow.savedIntent
        assertEquals(TimerService.ACTION_TOGGLE, intent.action)
        assertEquals(
            7,
            intent.getIntExtra(TimerService.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        )
    }

    @Test
    fun widgetRootShowsPressFeedback() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val root = LayoutInflater.from(context).inflate(R.layout.timer_widget, null) as View

        val background = root.background
        assertTrue(
            "widget background must be a ripple so a tap visibly registers",
            background is RippleDrawable,
        )
        assertEquals(
            "widget background must be the logo navy",
            0xFF10213F.toInt(),
            (background as RippleDrawable)
                .findDrawableByLayerId(android.R.id.background)
                .let { shape ->
                    (shape as android.graphics.drawable.GradientDrawable).color?.defaultColor
                },
        )
    }
}
