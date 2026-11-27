package ir.namak.sms.domain.model

enum class SmsTab(val key: String, val titleFa: String) {
    ALL("ALL", "همه"),
    CONTACTS("CONTACTS", "مخاطبین"),
    UNKNOWN("UNKNOWN", "ناشناس"),
    ADS("ADS", "تبلیغاتی"),
    GROUPS("GROUPS", "گروه‌ها"),
    OTP("OTP", "کد تایید"),
    SPAM("SPAM", "اسپم")
}

data class ClassificationResult(
    val category: SmsTab,
    val reasonFa: String,
    val extractedOtp: String? = null
)

data class SmsThreadUi(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val contactPhotoUri: String?,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isMuted: Boolean,
    val isBlocked: Boolean,
    val preferredSimSubId: Int?,
    val classification: ClassificationResult
)

data class SmsMessageUi(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val type: Int,
    val subId: Int,
    val isStarred: Boolean,
    val classification: ClassificationResult
)
