package ir.namak.sms.data.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.core.sms.DualSimManager
import ir.namak.sms.receivers.SmsDeliveredReceiver
import ir.namak.sms.receivers.SmsSentReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactInfo(
    val name: String,
    val phone: String,
    val photoUri: String?
)

@Singleton
class TelephonyDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dualSimManager: DualSimManager
) {
    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun loadContactsMap(): Map<String, ContactInfo> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext emptyMap()
        val map = mutableMapOf<String, ContactInfo>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val rawNum = cursor.getString(numIdx) ?: continue
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    val norm = PersianTextFormatter.normalizePhoneNumber(rawNum)
                    map[norm] = ContactInfo(name, rawNum, photo)
                }
            }
        } catch (_: Exception) {
        }
        map
    }

    data class RawSmsRow(
        val id: Long,
        val threadId: Long,
        val address: String,
        val body: String,
        val date: Long,
        val read: Int,
        val type: Int,
        val subId: Int
    )

    suspend fun queryAllSms(limit: Int = 1500): List<RawSmsRow> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) return@withContext emptyList()
        val result = mutableListOf<RawSmsRow>()
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                Telephony.Sms.TYPE,
                Telephony.Sms.SUBSCRIPTION_ID
            )
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdx = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val subIdx = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (c.moveToNext()) {
                    result.add(
                        RawSmsRow(
                            id = c.getLong(idIdx),
                            threadId = c.getLong(threadIdx),
                            address = c.getString(addrIdx).orEmpty().ifBlank { "ناشناس" },
                            body = c.getString(bodyIdx).orEmpty(),
                            date = c.getLong(dateIdx),
                            read = c.getInt(readIdx),
                            type = c.getInt(typeIdx),
                            subId = if (subIdx >= 0) c.getInt(subIdx) else -1
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    @SuppressLint("MissingPermission")
    suspend fun sendSms(
        address: String,
        body: String,
        subscriptionId: Int?,
        isDefaultSmsApp: Boolean
    ): Result<Uri?> = withContext(Dispatchers.IO) {
        try {
            if (address.isBlank() || body.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("شماره گیرنده یا متن پیام خالی است."))
            }
            val smsManager = dualSimManager.getSmsManagerForSubscription(subscriptionId)
            val parts = smsManager.divideMessage(body)

            var insertedUri: Uri? = null
            if (isDefaultSmsApp) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                    if (subscriptionId != null) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
                insertedUri = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            }

            val sentIntent = Intent(context, SmsSentReceiver::class.java).apply {
                putExtra("sms_uri", insertedUri?.toString())
            }
            val sentPI = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredPI = PendingIntent.getBroadcast(
                context,
                (System.currentTimeMillis() + 1).toInt(),
                Intent(context, SmsDeliveredReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (parts.size > 1) {
                val sentList = ArrayList<PendingIntent>(parts.size).apply { repeat(parts.size) { add(sentPI) } }
                val delList = ArrayList<PendingIntent>(parts.size).apply { repeat(parts.size) { add(deliveredPI) } }
                smsManager.sendMultipartTextMessage(address, null, parts, sentList, delList)
            } else {
                smsManager.sendTextMessage(address, null, body, sentPI, deliveredPI)
            }
            Result.success(insertedUri)
        } catch (_: SecurityException) {
            Result.failure(Exception("مجوز ارسال پیامک صادر نشده است."))
        } catch (e: Exception) {
            Result.failure(Exception("خطا در ارسال پیامک: ${e.localizedMessage ?: "عدم دسترسی به شبکه سیم‌کارت"}"))
        }
    }

    suspend fun markThreadAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) {
        }
    }

    suspend fun deleteMessages(ids: List<Long>) = withContext(Dispatchers.IO) {
        try {
            ids.forEach { id ->
                context.contentResolver.delete(
                    Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()),
                    null,
                    null
                )
            }
        } catch (_: Exception) {
        }
    }

    suspend fun deleteThreads(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        try {
            threadIds.forEach { tid ->
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(tid.toString())
                )
            }
        } catch (_: Exception) {
        }
    }
}
