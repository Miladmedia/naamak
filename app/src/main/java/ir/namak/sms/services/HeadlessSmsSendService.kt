package ir.namak.sms.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val message = intent.getStringExtra(Intent.EXTRA_TEXT)
            val destination = intent.data?.schemeSpecificPart
            if (!destination.isNullOrBlank() && !message.isNullOrBlank()) {
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(destination, null, message, null, null)
                } catch (_: Exception) {
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
