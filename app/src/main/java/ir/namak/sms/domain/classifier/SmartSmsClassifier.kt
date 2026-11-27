package ir.namak.sms.domain.classifier

import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.data.local.ClassificationRuleEntity
import ir.namak.sms.data.local.ThreadMetaEntity
import ir.namak.sms.domain.model.ClassificationResult
import ir.namak.sms.domain.model.SmsTab
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartSmsClassifier @Inject constructor() {

    private val otpKeywords = listOf("کد تایید", "رمز یکبار مصرف", "کد ورود", "رمز پویا", "otp", "verification code", "code:")
    private val otpCodeRegex = Regex("""\b\d{4,8}\b""")
    private val adKeywords = listOf("لغو11", "لغو ۱۱", "تخفیف", "جشنواره", "فروش ویژه", "قرعه‌کشی", "رایگان", "خرید")

    fun classify(
        address: String,
        body: String,
        isKnownContact: Boolean,
        isGroupThread: Boolean,
        meta: ThreadMetaEntity?,
        rules: List<ClassificationRuleEntity>
    ): ClassificationResult {
        val normalizedAddress = PersianTextFormatter.normalizePhoneNumber(address)
        val extractedOtp = extractOtpCode(body)

        if (meta?.isSpam == true || meta?.isBlocked == true) {
            return ClassificationResult(
                category = SmsTab.SPAM,
                reasonFa = if (meta.isBlocked) "شماره مسدود شده توسط کاربر" else "علامت‌گذاری‌شده به‌عنوان اسپم",
                extractedOtp = extractedOtp
            )
        }

        meta?.manualCategory?.let { manual ->
            val tab = SmsTab.entries.find { it.key == manual }
            if (tab != null) {
                return ClassificationResult(
                    category = tab,
                    reasonFa = "تغییر دستی توسط شما (یادگیری ترجیح)",
                    extractedOtp = extractedOtp
                )
            }
        }

        for (rule in rules.filter { it.isEnabled }.sortedByDescending { it.priority }) {
            val matched = when (rule.matchType) {
                "EXACT_NUMBER" -> normalizedAddress == PersianTextFormatter.normalizePhoneNumber(rule.pattern)
                "PREFIX" -> normalizedAddress.startsWith(PersianTextFormatter.normalizePhoneNumber(rule.pattern))
                "SENDER_ID" -> address.contains(rule.pattern, ignoreCase = true)
                "KEYWORD" -> body.contains(rule.pattern, ignoreCase = true)
                else -> false
            }
            if (matched) {
                val tab = SmsTab.entries.find { it.key == rule.targetCategory } ?: SmsTab.UNKNOWN
                return ClassificationResult(
                    category = tab,
                    reasonFa = "مطابق با قانون «${rule.name}»",
                    extractedOtp = extractedOtp
                )
            }
        }

        if (extractedOtp != null) {
            return ClassificationResult(
                category = SmsTab.OTP,
                reasonFa = "دارای الگوی کد تایید (${PersianTextFormatter.toPersianDigits(extractedOtp)})",
                extractedOtp = extractedOtp
            )
        }

        if (isGroupThread || address.contains(",")) {
            return ClassificationResult(
                category = SmsTab.GROUPS,
                reasonFa = "گفتگوی چندنفره / گروهی"
            )
        }

        if (isKnownContact) {
            return ClassificationResult(
                category = SmsTab.CONTACTS,
                reasonFa = "ذخیره‌شده در مخاطبین دستگاه"
            )
        }

        val isServiceShortCode = normalizedAddress.startsWith("1000") ||
                normalizedAddress.startsWith("2000") ||
                normalizedAddress.startsWith("3000") ||
                normalizedAddress.startsWith("5000") ||
                normalizedAddress.startsWith("9000") ||
                normalizedAddress.startsWith("021") ||
                address.any { it.isLetter() }

        if (isServiceShortCode || adKeywords.any { body.contains(it, ignoreCase = true) }) {
            return ClassificationResult(
                category = SmsTab.ADS,
                reasonFa = if (isServiceShortCode) "سرشماره خدماتی / سازمانی" else "دارای کلیدواژه تبلیغاتی"
            )
        }

        return ClassificationResult(
            category = SmsTab.UNKNOWN,
            reasonFa = "شماره خارج از فهرست مخاطبین"
        )
    }

    fun extractOtpCode(body: String): String? {
        val hasKeyword = otpKeywords.any { body.contains(it, ignoreCase = true) }
        if (!hasKeyword) return null
        val latinBody = body.map { if (it in '۰'..'۹') ('0' + (it - '۰')) else it }.joinToString("")
        return otpCodeRegex.find(latinBody)?.value
    }
}
