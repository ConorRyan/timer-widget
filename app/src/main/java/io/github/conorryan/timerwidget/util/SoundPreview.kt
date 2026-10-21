package io.github.conorryan.timerwidget.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * Plays one preview of a candidate alarm ringtone while the user picks a
 * sound. Unlike the alarm ring ([io.github.conorryan.timerwidget.widget.AlarmPlayer]),
 * previews are one-shot and there is only ever one in flight: tapping another
 * sound replaces it, and closing the picker stops it.
 */
class SoundPreviewPlayer {

    private var player: MediaPlayer? = null

    fun isPlaying(): Boolean = player != null

    /** Plays [uri] once, replacing any preview already in flight. No-op if unplayable or null. */
    fun preview(context: Context, uri: Uri?) {
        stop()
        player = tryPlay(context, uri ?: return)
    }

    fun stop() {
        val current = player ?: return
        player = null
        runCatching {
            current.stop()
            current.release()
        }
    }

    private fun tryPlay(context: Context, uri: Uri): MediaPlayer? {
        val candidate = MediaPlayer()
        return try {
            candidate.setAudioAttributes(ALARM_ATTRIBUTES)
            candidate.setDataSource(context, uri)
            candidate.isLooping = false
            candidate.setOnCompletionListener { finished ->
                if (player === finished) player = null
                runCatching { finished.release() }
            }
            candidate.prepare()
            candidate.start()
            candidate
        } catch (e: Exception) {
            runCatching { candidate.release() }
            null
        }
    }

    private companion object {
        val ALARM_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
