package io.github.conorryan.timerwidget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrenchStringsTest {

    @Test
    fun everyStringHasAFrenchTranslation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val french = context.createConfigurationContext(
            Configuration().apply { setLocale(Locale.FRANCE) },
        )

        val intentionallyShared = setOf(
            "app_name",
            "timer_emoji_hint",
            "ok",
            "minutes",
            "widget_preview_name",
            "widget_preview_time",
        )

        R.string::class.java.fields.forEach { field ->
            if (field.name in intentionallyShared) return@forEach
            val id = field.getInt(null)
            assertNotEquals(
                "missing French translation for ${field.name}",
                context.getString(id),
                french.getString(id),
            )
        }
    }
}
