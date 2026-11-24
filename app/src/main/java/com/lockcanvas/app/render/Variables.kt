package com.lockcanvas.app.render

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Calendar
import java.util.Locale

/**
 * Provides live variables that templates can reference with $NAME$.
 */
object Variables {

    const val DEFAULT_TEMPLATE_HELP =
        "Variables: \$TIME_HH\$ \$TIME_H\$ \$TIME_H12\$ \$TIME_AMPM\$ \$TIME_MM\$ " +
        "\$TIME_SS\$ \$DATE_DD\$ \$DATE_MM\$ \$DATE_YYYY\$ \$DATE_WEEKDAY\$ " +
        "\$DATE_MONTH\$ \$BATT\$ \$BATT_STATE\$"

    fun current(context: Context, now: Calendar = Calendar.getInstance()): Map<String, String> {
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)
        val h12 = now.get(Calendar.HOUR)
        val battery = batteryStatus(context)

        return mapOf(
            "TIME_HH" to String.format(Locale.US, "%02d", h),
            "TIME_H" to h.toString(),
            "TIME_H12" to if (h12 == 0) "12" else h12.toString(),
            "TIME_AMPM" to if (h < 12) "AM" else "PM",
            "TIME_MM" to String.format(Locale.US, "%02d", m),
            "TIME_SS" to String.format(Locale.US, "%02d", s),
            "DATE_DD" to String.format(Locale.US, "%02d", now.get(Calendar.DAY_OF_MONTH)),
            "DATE_MM" to String.format(Locale.US, "%02d", now.get(Calendar.MONTH) + 1),
            "DATE_YYYY" to now.get(Calendar.YEAR).toString(),
            "DATE_WEEKDAY" to java.text.SimpleDateFormat("EEEE", Locale.getDefault())
                .format(now.time).uppercase(Locale.US),
            "DATE_MONTH" to java.text.SimpleDateFormat("MMMM", Locale.getDefault())
                .format(now.time).uppercase(Locale.US),
            "BATT" to battery.first.toString(),
            "BATT_STATE" to battery.second,
        )
    }

    fun expand(template: String, vars: Map<String, String>): String {
        var out = template
        for ((k, v) in vars) {
            out = out.replace("\$$k\$", v)
        }
        return out
    }

    /** Returns (percent, state-string). Safe when called from any process. */
    fun batteryStatus(context: Context): Pair<Int, String> {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return 0 to "unknown"
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0) level * 100 / scale.coerceAtLeast(1) else 0
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> "charging"
            else -> "discharging"
        }
        return percent to state
    }
}
