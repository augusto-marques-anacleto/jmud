package br.com.augusto.jmud.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.augusto.jmud.MainActivity
import br.com.augusto.jmud.R

class SoundPackNotifier(private val context: Context) {
    private val channelId = "SoundPackChannel"
    private val notificationId = 2
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.installing_sound_pack),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    fun update(progress: SoundPackProgress) {
        val stepLabel = context.getString(
            when (progress.step) {
                SoundPackStep.DOWNLOADING -> R.string.step_downloading
                SoundPackStep.EXTRACTING -> R.string.step_extracting
            }
        )
        val indeterminate = progress.step == SoundPackStep.DOWNLOADING && !progress.totalKnown
        val text = if (indeterminate) {
            stepLabel
        } else {
            context.getString(
                R.string.labeled_value,
                stepLabel,
                context.getString(R.string.percent_format, progress.percent)
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_PROGRESS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.installing_sound_pack))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (indeterminate) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(100, progress.percent, false)
                }
            }
            .build()

        try {
            manager.notify(notificationId, notification)
        } catch (e: Exception) {
        }
    }

    fun cancel() {
        manager.cancel(notificationId)
    }

    companion object {
        const val EXTRA_SHOW_PROGRESS = "show_sound_pack_progress"
    }
}
