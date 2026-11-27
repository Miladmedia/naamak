package ir.namak.sms.core.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.namak.sms.data.telephony.TelephonyDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupSmsItem(
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Int
)

@Serializable
data class NamakBackupPayload(
    val app: String = "NamakSMS",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val messages: List<BackupSmsItem>
)

@Singleton
class SmsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telephonyDataSource: TelephonyDataSource
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val rows = telephonyDataSource.queryAllSms(limit = 10000)
            val payload = NamakBackupPayload(
                messages = rows.map {
                    BackupSmsItem(it.address, it.body, it.date, it.type, it.read)
                }
            )
            val serialized = json.encodeToString(payload)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(serialized.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(Exception("امکان باز کردن فایل خروجی وجود ندارد."))
            Result.success(rows.size)
        } catch (e: Exception) {
            Result.failure(Exception("خطا در پشتیبان‌گیری: ${e.localizedMessage}"))
        }
    }

    suspend fun restoreFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("فایل پشتیبان قابل خواندن نیست."))

            val payload = json.decodeFromString<NamakBackupPayload>(content)
            var restoredCount = 0
            for (msg in payload.messages) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, msg.address)
                    put(Telephony.Sms.BODY, msg.body)
                    put(Telephony.Sms.DATE, msg.date)
                    put(Telephony.Sms.TYPE, msg.type)
                    put(Telephony.Sms.READ, msg.read)
                }
                if (context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null) {
                    restoredCount++
                }
            }
            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(Exception("خطا در بازیابی فایل پشتیبان: ${e.localizedMessage}"))
        }
    }
}
