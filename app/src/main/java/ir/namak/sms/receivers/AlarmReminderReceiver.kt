package ir.namak.sms.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.namak.sms.MainActivity
import ir.namak.sms.R

class AlarmReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REMINDER = "ir.namak.sms.ACTION_REMINDER"
        const val ACTION_SCHEDULED_SMS = "ir.namak.sms.ACTION_SCHEDULED_SMS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMINDER -> {
                val threadId = intent.getLongExtra("thread_id", -1L)
                val address = intent.getStringExtra("address").orEmpty()
                val preview = intent.getStringExtra("preview").orEmpty()
                showReminderNotification(context, threadId, address, preview)
            }
            ACTION_SCHEDULED_SMS -> {
                val address = intent.getStringExtra("address").orEmpty()
                val body = intent.getStringExtra("body").orEmpty()
                val confirmed = intent.getBooleanExtra("user_confirmed", false)
                if (confirmed && address.isNotBlank() && body.isNotBlank()) {
                    try {
                        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.getSystemService(SmsManager::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsManager.getDefault()
                        }
                        smsManager.sendTextMessage(address, null, body, null, null)
                        val values = ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, address)
                            put(Telephony.Sms.BODY, body)
                            put(Telephony.Sms.DATE, System.currentTimeMillis())
                            put(Telephony.Sms.READ, 1)
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                        }
                        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun showReminderNotification(context: Context, threadId: Long, address: String, preview: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "namak_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "یادآوری‌های نامک", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_thread_id", threadId)
            putExtra("open_address", address)
        }
        val pi = PendingIntent.getActivity(
            context,
            (threadId + 50000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("یادآوری پاسخ به $address")
            .setContentText(preview)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify((threadId + 50000).toInt(), notif)
    }
}
