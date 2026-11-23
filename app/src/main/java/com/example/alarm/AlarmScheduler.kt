package com.example.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.util.Calendar

const val EXTRA_ALARM_TRIGGERED = "actake_alarm_triggered"
const val EXTRA_ALARM_MISSION_TITLE = "actake_alarm_mission_title"
const val EXTRA_ALARM_SOUND = "actake_alarm_sound"
private const val CHANNEL_ID = "actake_wake_alarms"

object AlarmScheduler {

    fun scheduleDailyWakeAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        missionTitle: String,
        alarmSound: String
    ): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false

            val triggerCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_ALARM_MISSION_TITLE, missionTitle)
                putExtra(EXTRA_ALARM_SOUND, alarmSound)
            }

            val requestCode = ((missionTitle.hashCode() * 31) + (hour * 60) + minute) and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerCal.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerCal.timeInMillis,
                    pendingIntent
                )
            }
            true
        } catch (e: Exception) {
            Log.w("AlarmScheduler", "Could not schedule exact alarm: ${e.message}")
            false
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val missionTitle = intent.getStringExtra(EXTRA_ALARM_MISSION_TITLE) ?: "Toothbrush Hunt"
        val alarmSound = intent.getStringExtra(EXTRA_ALARM_SOUND) ?: "Siren"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actake Wake Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority accountability wake-up alarms."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALARM_TRIGGERED, true)
            putExtra(EXTRA_ALARM_MISSION_TITLE, missionTitle)
            putExtra(EXTRA_ALARM_SOUND, alarmSound)
        }
        val pendingLaunch = PendingIntent.getActivity(
            context,
            2002,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Actake Wake-Up Proof Required")
            .setContentText("Snap live proof for $missionTitle before your stake slashes!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingLaunch)
            .setFullScreenIntent(pendingLaunch, true)
            .build()

        try {
            manager.notify(3003, notification)
        } catch (e: SecurityException) {
            Log.w("AlarmReceiver", "Notification permission missing: ${e.message}")
        }
    }
}
