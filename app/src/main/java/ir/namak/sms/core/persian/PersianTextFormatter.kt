package ir.namak.sms.core.persian

import android.text.BidiFormatter
import android.text.TextDirectionHeuristics

object PersianTextFormatter {
    private val bidiFormatter = BidiFormatter.getInstance(true)

    fun toPersianDigits(input: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizePhoneNumber(raw: String): String {
        val latin = raw.map {
            when (it) {
                in '۰'..'۹' -> ('0' + (it - '۰'))
                in '٠'..'٩' -> ('0' + (it - '٠'))
                else -> it
            }
        }.joinToString("")
        return latin.replace(" ", "").replace("-", "").replace("+98", "0")
    }

    fun isolateBidiText(text: String): String {
        val phoneOrCodeRegex = Regex("""(\+?\d[\d\s-]{3,}\d|https?://\S+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""")
        val isolated = phoneOrCodeRegex.replace(text) { match ->
            "\u2066${match.value}\u2069"
        }
        return bidiFormatter.unicodeWrap(isolated, TextDirectionHeuristics.FIRSTSTRONG_RTL)
    }

    fun formatPhoneLtr(phone: String): String = "\u2066$phone\u2069"
}
