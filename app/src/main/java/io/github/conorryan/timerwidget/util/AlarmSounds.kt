package io.github.conorryan.timerwidget.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** A preloaded device ringtone the user can pick for the alarm. */
data class AlarmSound(val title: String, val uri: String)

object AlarmSounds {

    /** All alarm-type ringtones installed on the device. */
    fun available(context: Context): List<AlarmSound> {
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = manager.cursor
        val sounds = ArrayList<AlarmSound>()
        while (cursor.moveToNext()) {
            val title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: continue
            sounds.add(AlarmSound(title, manager.getRingtoneUri(cursor.position).toString()))
        }
        return sounds
    }

    /**
     * The URI the "Default" option previews and the alarm falls back to.
     * getActualDefaultRingtoneUri resolves the settings value to an openable
     * MediaStore URI; getDefaultUri may return a settings stub the app cannot
     * read, which would ring silence.
     */
    fun defaultUri(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
}
