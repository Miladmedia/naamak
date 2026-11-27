package ir.namak.sms.receivers

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.RemoteInput

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uriStr = intent.getStringExtra("sms_uri") ?: return
        val uri = Uri.parse(uriStr)
        val newType = if (resultCode == Activity.RESULT_OK) {
            Telephony.Sms.MESSAGE_TYPE_SENT
        } else {
            Telephony.Sms.MESSAGE_TYPE_FAILED
        }
        try {
            val values = ContentValues().apply { put(Telephony.Sms.TYPE, newType) }
            context.contentResolver.update(uri, values, null, null)
        } catch (_: Exception) {
        }
    }
}

class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ثبت گزارش تحویل پیامک
    }
}

class QuickReplyReceiver : BroadcastReceiver() {
    companion object {
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_REPLY = "ir.namak.sms.ACTION_QUICK_REPLY"
        const val ACTION_MARK_READ = "ir.namak.sms.ACTION_MARK_READ"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra("thread_id", -1L)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            ACTION_MARK_READ -> {
                markRead(context, threadId)
                nm.cancel(threadId.toInt())
            }
            ACTION_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT_REPLY)?.toString()
                val address = intent.getStringExtra("address").orEmpty()
                if (!replyText.isNullOrBlank() && address.isNotBlank()) {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(address, null, replyText, null, null)
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, replyText)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.READ, 1)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    }
                    context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    markRead(context, threadId)
                }
                nm.cancel(threadId.toInt())
            }
        }
    }

    private fun markRead(context: Context, threadId: Long) {
        if (threadId == -1L) return
        try {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) {
        }
    }
}
