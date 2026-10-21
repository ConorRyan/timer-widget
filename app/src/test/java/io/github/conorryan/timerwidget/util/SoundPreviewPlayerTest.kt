package io.github.conorryan.timerwidget.util

import android.content.Context
import android.net.Uri
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
class SoundPreviewPlayerTest {

    private lateinit var previewPlayer: SoundPreviewPlayer
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        previewPlayer = SoundPreviewPlayer()
    }

    @Test
    fun previewsThePickedTone() {
        registerTone("content://media/internal/audio/media/42")

        previewPlayer.preview(context, Uri.parse("content://media/internal/audio/media/42"))

        assertTrue(previewPlayer.isPlaying())
    }

    @Test
    fun previewingAnotherToneReplacesTheOneInFlight() {
        registerTone("content://media/internal/audio/media/42")
        registerTone("content://media/internal/audio/media/7")

        previewPlayer.preview(context, Uri.parse("content://media/internal/audio/media/42"))
        previewPlayer.preview(context, Uri.parse("content://media/internal/audio/media/7"))

        assertTrue(previewPlayer.isPlaying())
    }

    @Test
    fun stopEndsThePreview() {
        registerTone("content://media/internal/audio/media/42")
        previewPlayer.preview(context, Uri.parse("content://media/internal/audio/media/42"))

        previewPlayer.stop()

        assertFalse(previewPlayer.isPlaying())
    }

    @Test
    fun anUnplayableToneStaysSilent() {
        val gone = Uri.parse("content://media/internal/audio/media/42")
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, gone), RuntimeException("tone removed"))

        previewPlayer.preview(context, gone)

        assertFalse(previewPlayer.isPlaying())
    }

    @Test
    fun aNullUriIsANoop() {
        previewPlayer.preview(context, null)

        assertFalse(previewPlayer.isPlaying())
    }

    private fun registerTone(uri: String) {
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, Uri.parse(uri)),
            ShadowMediaPlayer.MediaInfo(),
        )
    }
}
