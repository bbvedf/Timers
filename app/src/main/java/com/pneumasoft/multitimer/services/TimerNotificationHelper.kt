// ✅ FULL FILE VERSION
// Path: C:/local/Android/Timers/app/src/main/java/com/pneumasoft/multitimer/services/TimerNotificationHelper.kt

package com.pneumasoft.multitimer.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.pneumasoft.multitimer.MainActivity
import com.pneumasoft.multitimer.R
import com.pneumasoft.multitimer.model.TimerItem
import com.pneumasoft.multitimer.receivers.TimerAlarmReceiver
import com.pneumasoft.multitimer.repository.TimerRepository

class TimerNotificationHelper(
    private val context: Context,
    private val repository: TimerRepository
) {
    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val ALARM_CHANNEL_ID = "TimerAlarmChannel"
        const val NOTIFICATION_ID = 1
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal para el servicio de timers"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Timer Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de timers finalizados"
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    fun getForegroundNotification(): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val activeTimers = repository.loadTimers().filter { it.isRunning }

        val contentText = when {
            activeTimers.isEmpty() -> "No hay timers activos"
            activeTimers.size == 1 -> {
                val timer = activeTimers.first()
                val remaining = calculateRemainingSeconds(timer)
                "${timer.name}: ${formatTime(remaining)}"
            }
            else -> "${activeTimers.size} timers funcionando"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("MultiTimer")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_timer_simple)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun updateForegroundNotification() {
        notificationManager.notify(NOTIFICATION_ID, getForegroundNotification())
    }

    @SuppressLint("InlinedApi") // ✅ Silencia el warning de Full Screen Intent
    fun showTimerCompletionNotification(timerId: String, timerName: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val shortSnoozeSec = prefs.getString("snooze_short_duration", "30")?.toLongOrNull() ?: 30L
        val longSnoozeSec = prefs.getString("snooze_long_duration", "300")?.toLongOrNull() ?: 300L

        val shortLabel = "+${formatSnoozeLabel(shortSnoozeSec)}"
        val longLabel = "+${formatSnoozeLabel(longSnoozeSec)}"

        // 🔄 MODIFICADO: Ahora usamos la función de ayuda para TODOS los botones, eliminando el warning de redundancia
        val dismissPI = createActionIntent(timerId, TimerAlarmReceiver.ACTION_DISMISS_ALARM, 0)
        val shortSnoozePI = createActionIntent(timerId, TimerAlarmReceiver.ACTION_SNOOZE_ALARM, 1, shortSnoozeSec)
        val longSnoozePI = createActionIntent(timerId, TimerAlarmReceiver.ACTION_SNOOZE_ALARM, 2, longSnoozeSec)

        val fullScreenIntent = Intent(context, com.pneumasoft.multitimer.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timerId)
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_NAME, timerName)
        }
        val fullScreenPI = PendingIntent.getActivity(
            context, timerId.hashCode() + 10, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setContentTitle("¡Tiempo agotado!")
            .setContentText(timerName)
            .setSmallIcon(R.drawable.ic_timer_simple)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPI, true)
            .setContentIntent(fullScreenPI)
            .addAction(R.drawable.ic_close, "Done", dismissPI)
            .addAction(R.drawable.ic_snooze, shortLabel, shortSnoozePI)
            .addAction(R.drawable.ic_snooze, longLabel, longSnoozePI)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 1000, 1000, 1000))
            .build()

        notificationManager.notify(timerId.hashCode(), notification)
    }

    // ✅ REFACTORIZADO: Ahora esta función se usa para todo y no genera warnings
    private fun createActionIntent(
        timerId: String,
        actionStr: String,
        reqCodeOffset: Int,
        snoozeDuration: Long? = null
    ): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = actionStr
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timerId)
            snoozeDuration?.let { putExtra(TimerAlarmReceiver.EXTRA_SNOOZE_DURATION, it) }
        }
        return PendingIntent.getBroadcast(
            context, timerId.hashCode() + reqCodeOffset, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatSnoozeLabel(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds % 60 == 0L -> "${seconds / 60} min"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun calculateRemainingSeconds(timer: TimerItem): Int {
        val endTime = timer.absoluteEndTimeMillis
        if (!timer.isRunning || endTime == null) return timer.remainingSeconds
        val now = System.currentTimeMillis()
        val diff = (endTime - now) / 1000
        return diff.toInt().coerceAtLeast(0)
    }

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
        else "%d:%02d".format(minutes, secs)
    }
}