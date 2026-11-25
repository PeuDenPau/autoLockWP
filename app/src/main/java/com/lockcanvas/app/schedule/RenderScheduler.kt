package com.lockcanvas.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lockcanvas.app.data.PresetStore
import com.lockcanvas.app.render.applyPresetFromStorage

/**
 * Schedules periodic lock screen re-renders.
 *
 * Uses AlarmManager.setAlarmClock: alarm-clock alarms fire on time even in
 * Doze (screen locked), are exempt from the once-per-~9-minutes Doze
 * throttling that setExactAndAllowWhileIdle suffers, and need no
 * SCHEDULE_EXACT_ALARM permission. Trade-off: the system shows the standard
 * alarm icon in the status bar while updates are enabled.
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
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val showIntent = if (launch == null) null else PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val info = AlarmManager.AlarmClockInfo(at, showIntent)
        am.setAlarmClock(info, intent(context))
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

/** Re-renders immediately when the user unlocks, so the wallpaper is fresh on wake-up. */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        if (!PresetStore(context).enabled) return
        RenderScheduler.tick(context.applicationContext)
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
