package com.tushar.sharma.logic.todo.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tushar.sharma.logic.todo.MainActivity
import org.json.JSONArray
import org.json.JSONObject

object ReminderScheduler {

    const val CHANNEL_ID = "todo_reminders"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DESC = "desc"
    const val EXTRA_ID = "id"
    private const val PREFS = "todo_reminders_store"
    private const val KEY_PENDING = "pending_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for your tasks"
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun loadPending(context: Context): MutableList<JSONObject> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_PENDING, "[]") ?: "[]"
        val list = mutableListOf<JSONObject>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        } catch (_: Exception) {}
        return list
    }

    private fun savePending(context: Context, list: List<JSONObject>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING, arr.toString()).apply()
    }

    private fun addPending(context: Context, id: Long, title: String, desc: String, at: Long) {
        val list = loadPending(context)
        list.removeAll { it.optLong("id") == id }
        list.add(JSONObject().apply {
            put("id", id); put("title", title); put("desc", desc); put("at", at)
        })
        savePending(context, list)
    }

    private fun removePending(context: Context, id: Long) {
        val list = loadPending(context)
        list.removeAll { it.optLong("id") == id }
        savePending(context, list)
    }

    fun schedule(
        context: Context,
        id: Long,
        title: String,
        description: String,
        triggerAt: Long
    ) {
        addPending(context, id, title, description, triggerAt)

        if (triggerAt <= System.currentTimeMillis()) {
            fireNow(context, id, title, description)
            removePending(context, id)
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, id, title, description)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        removePending(context, id)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    private fun buildPendingIntent(
        context: Context, id: Long, title: String, desc: String
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESC, desc)
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Called by BootReceiver: re-arm future reminders and fire missed ones. */
    fun rescheduleAllAfterBoot(context: Context) {
        val pending = loadPending(context).toMutableList()
        val now = System.currentTimeMillis()
        val stillFuture = mutableListOf<JSONObject>()

        for (obj in pending) {
            val id = obj.optLong("id")
            val title = obj.optString("title")
            val desc = obj.optString("desc")
            val at = obj.optLong("at")
            if (at <= now) {
                fireNow(context, id, title, desc)
            } else {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = buildPendingIntent(context, id, title, desc)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    } else {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    }
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
                stillFuture.add(obj)
            }
        }
        savePending(context, stillFuture)
    }

    fun fireNow(context: Context, id: Long, title: String, desc: String) {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(desc)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.notify(id.toInt(), notif)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Task reminder"
        val desc = intent.getStringExtra(ReminderScheduler.EXTRA_DESC).orEmpty()

        ReminderScheduler.fireNow(context, id, title, desc)

        try {
            val sp = context.getSharedPreferences("todo_reminders_store", Context.MODE_PRIVATE)
            val raw = sp.getString("pending_reminders", "[]") ?: "[]"
            val arr = JSONArray(raw)
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optLong("id") != id) out.put(o)
            }
            sp.edit().putString("pending_reminders", out.toString()).apply()
        } catch (_: Exception) {}
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            ReminderScheduler.ensureChannel(context)
            ReminderScheduler.rescheduleAllAfterBoot(context)
        }
    }
}