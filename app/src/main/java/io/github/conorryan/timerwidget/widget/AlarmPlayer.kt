package io.github.conorryan.timerwidget.widget

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays the alarm ringtone directly on the alarm audio stream, looping until
 * stopped. Notification channel sound proved unreliable in the field: it is
 * one-shot, follows the notification stream on some OEM ROMs, and a channel
 * created with a sound the app cannot read plays nothing at all. Owning the
 * playback keeps the ring on the alarm stream and stoppable on any
 * dismissal path. One ring per widget ID.
 */
class AlarmPlayer {

    private val players = ConcurrentHashMap<Int, MediaPlayer>()

    /**
     * Starts ringing for [widgetId], replacing any ring already in flight.
     * Falls back to the system default alarm tone if the picked one is gone.
     */
    fun start(context: Context, widgetId: Int, soundUri: String?) {
        stop(widgetId)
        val uris = buildList {
            soundUri?.let { add(Uri.parse(it)) }
            defaultAlarmUri(context)?.let { add(it) }
        }
        for (uri in uris) {
            tryPlay(context, uri)?.let { player ->
                players[widgetId] = player
                return
            }
        }
    }

    fun stop(widgetId: Int) {
        players.remove(widgetId)?.let { player ->
            runCatching {
                player.stop()
                player.release()
            }
        }
    }

    fun stopAll() {
        players.keys.toList().forEach(::stop)
    }

    fun isPlaying(widgetId: Int): Boolean = players.containsKey(widgetId)

    private fun tryPlay(context: Context, uri: Uri): MediaPlayer? = runCatching {
        val player = MediaPlayer()
        player.setAudioAttributes(ALARM_ATTRIBUTES)
        player.setDataSource(context, uri)
        player.isLooping = true
        player.prepare()
        player.start()
        player
    }.getOrNull()

    /**
     * getActualDefaultRingtoneUri resolves the settings value to an openable
     * MediaStore URI; getDefaultUri may return a settings stub the app cannot
     * read, which would ring silence.
     */
    private fun defaultAlarmUri(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    private companion object {
        val ALARM_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
