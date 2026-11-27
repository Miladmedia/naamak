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
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import ir.namak.sms.MainActivity
import ir.namak.sms.R
import ir.namak.sms.core.persian.PersianTextFormatter

class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val address = messages[0].displayOriginatingAddress.orEmpty()
        val fullBody = messages.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = messages[0].timestampMillis
        val subId = intent.getIntExtra("subscription", -1)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, fullBody)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            if (subId != -1) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        val threadId = Telephony.Threads.getOrCreateThreadId(context, address)

        showIncomingNotification(context, threadId, address, fullBody)
    }

    private fun showIncomingNotification(
        context: Context,
        threadId: Long,
        address: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "namak_incoming_sms"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "پیامک‌های دریافتی نامک",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_thread_id", threadId)
            putExtra("open_address", address)
        }
        val openPI = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(QuickReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("پاسخ سریع...")
            .build()

        val replyIntent = Intent(context, QuickReplyReceiver::class.java).apply {
            action = QuickReplyReceiver.ACTION_REPLY
            putExtra("address", address)
            putExtra("thread_id", threadId)
        }
        val replyPI = PendingIntent.getBroadcast(
            context,
            (threadId + 10000).toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "پاسخ سریع",
            replyPI
        ).addRemoteInput(remoteInput).build()

        val readIntent = Intent(context, QuickReplyReceiver::class.java).apply {
            action = QuickReplyReceiver.ACTION_MARK_READ
            putExtra("thread_id", threadId)
        }
        val readPI = PendingIntent.getBroadcast(
            context,
            (threadId + 20000).toInt(),
            readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(PersianTextFormatter.formatPhoneLtr(address))
            .setContentText(PersianTextFormatter.isolateBidiText(body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(PersianTextFormatter.isolateBidiText(body)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPI)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_view, "خوانده شد", readPI)
            .build()

        nm.notify(threadId.toInt(), notification)
    }
}
