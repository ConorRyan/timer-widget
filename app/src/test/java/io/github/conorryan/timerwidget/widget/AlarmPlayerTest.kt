package io.github.conorryan.timerwidget.widget

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmPlayerTest {

    private lateinit var player: AlarmPlayer
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        player = AlarmPlayer()
    }

    @Test
    fun ringsForPickedToneAndStopSilencesIt() {
        registerTone("content://media/internal/audio/media/42")

        player.start(context, 3, "content://media/internal/audio/media/42")

        assertTrue(player.isPlaying(3))
        player.stop(3)
        assertFalse(player.isPlaying(3))
    }

    @Test
    fun fallsBackToDefaultToneWhenPickedToneIsUnplayable() {
        val gone = Uri.parse("content://media/internal/audio/media/42")
        val fallback = Uri.parse("content://media/internal/audio/media/7")
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, gone), RuntimeException("tone removed"))
        registerTone(fallback.toString())
        Settings.System.putString(context.contentResolver, Settings.System.ALARM_ALERT, fallback.toString())

        player.start(context, 5, gone.toString())

        assertTrue(player.isPlaying(5))
    }

    @Test
    fun survivesUnplayableToneWithNoDefault() {
        val gone = Uri.parse("content://media/internal/audio/media/42")
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, gone), RuntimeException("tone removed"))

        player.start(context, 5, gone.toString())

        assertFalse(player.isPlaying(5))
    }

    @Test
    fun stopAllSilencesEveryRing() {
        registerTone("content://media/internal/audio/media/42")

        player.start(context, 1, "content://media/internal/audio/media/42")
        player.start(context, 2, "content://media/internal/audio/media/42")

        player.stopAll()

        assertFalse(player.isPlaying(1))
        assertFalse(player.isPlaying(2))
    }

    private fun registerTone(uri: String) {
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, Uri.parse(uri)),
            ShadowMediaPlayer.MediaInfo(),
        )
    }
}
