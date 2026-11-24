package com.lockcanvas.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lockcanvas.app.data.PresetStore
import com.lockcanvas.app.render.applyPresetFromStorage

/**
 * Schedules periodic lock screen re-renders with exact alarms.
 * WorkManager has a 15-minute floor, so sub-15-minute intervals rely on
 * setExactAndAllowWhileIdle (requires SCHEDULE_EXACT_ALARM on Android 12+).
 */
object RenderScheduler {

    val INTERVAL_CHOICES_MINUTES = listOf(1, 5, 10, 15, 30, 60, 180, 1440)

    fun intent(context: Context): PendingIntent {
        val intent = Intent(context, RenderAlarmReceiver::class.java).apply {
            action = "com.lockcanvas.app.RENDER_TICK"
        }
        return PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    }

    fun requestExactPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }

    fun start(context: Context) {
        val store = PresetStore(context)
        store.enabled = true
        scheduleNext(context, store.intervalMinutes)
        Thread { applyPresetFromStorage(context) }.start()
    }

    fun stop(context: Context) {
        PresetStore(context).enabled = false
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(intent(context))
    }

    /** Immediately renders once and re-arms the next alarm. */
    fun tick(context: Context) {
        applyPresetFromStorage(context)
        val store = PresetStore(context).apply { lastAppliedAt = System.currentTimeMillis() }
        if (store.enabled) {
            scheduleNext(context, store.intervalMinutes)
        }
    }

    fun scheduleNext(context: Context, intervalMinutes: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + intervalMinutes * 60_000L
        val pi = intent(context)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (security: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class RenderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                RenderScheduler.tick(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val store = PresetStore(context)
        if (store.enabled) {
            RenderScheduler.scheduleNext(context, store.intervalMinutes)
        }
    }
}
