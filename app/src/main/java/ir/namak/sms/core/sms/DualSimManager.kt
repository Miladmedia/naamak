package ir.namak.sms.core.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SimCardInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String
)

@Singleton
class DualSimManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getActiveSims(): List<SimCardInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val list = subManager?.activeSubscriptionInfoList.orEmpty()
            list.map { info ->
                SimCardInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex + 1,
                    displayName = info.displayName?.toString().orEmpty().ifBlank { "سیم‌کارت ${info.simSlotIndex + 1}" },
                    carrierName = info.carrierName?.toString().orEmpty()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSmsManagerForSubscription(subscriptionId: Int?): SmsManager {
        return if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }
}
