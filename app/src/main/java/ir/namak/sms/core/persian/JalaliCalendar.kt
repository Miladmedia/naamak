package ir.namak.sms.core.persian

import java.util.Calendar
import java.util.GregorianCalendar

data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int = 0,
    val minute: Int = 0
) {
    val monthName: String
        get() = MONTH_NAMES.getOrElse(month - 1) { "" }

    fun formatFull(): String =
        PersianTextFormatter.toPersianDigits("$day $monthName $year - %02d:%02d".format(hour, minute))

    fun formatShortDate(): String =
        PersianTextFormatter.toPersianDigits("$year/%02d/%02d".format(month, day))

    companion object {
        val MONTH_NAMES = listOf(
            "فروردین", "اردیبهشت", "خرداد",
            "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر",
            "دی", "بهمن", "اسفند"
        )
    }
}

object JalaliCalendar {

    fun fromTimestamp(timestampMillis: Long): JalaliDate {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val (jYear, jMonth, jDay) = gregorianToJalali(gYear, gMonth, gDay)
        return JalaliDate(jYear, jMonth, jDay, hour, minute)
    }

    fun toTimestamp(jalali: JalaliDate): Long {
        val (gYear, gMonth, gDay) = jalaliToGregorian(jalali.year, jalali.month, jalali.day)
        return GregorianCalendar(gYear, gMonth - 1, gDay, jalali.hour, jalali.minute, 0).timeInMillis
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) (gy + 1) else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + (days / 31) else 7 + ((days - 186) / 30)
        val jd = 1 + (if (days < 186) (days % 31) else ((days - 186) % 30))
        return Triple(jy, jm, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy1 = jy + 1595
        var days = -355668 + (365 * jy1) + ((jy1 / 33) * 8) + (((jy1 % 33) + 3) / 4) + jd +
                (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val salA = intArrayOf(0, 31, if ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }

    fun isLeapJalaliYear(year: Int): Boolean {
        val mod = year % 33
        return mod == 1 || mod == 5 || mod == 9 || mod == 13 || mod == 17 || mod == 22 || mod == 26 || mod == 30
    }

    fun getDaysInMonth(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeapJalaliYear(year) -> 30
        else -> 29
    }
}
