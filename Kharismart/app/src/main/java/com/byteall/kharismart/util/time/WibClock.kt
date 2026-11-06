package com.byteall.kharismart.util.time

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object WibClock {
    val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    @Volatile
    var clock: Clock = Clock.system(zone)

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val displayDateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val receiptDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun nowString(): String = LocalDateTime.now(clock).format(dateTimeFormatter)

    fun todayString(): String = LocalDate.now(clock).toString()

    fun today(): LocalDate = LocalDate.now(clock)

    fun parse(text: String): LocalDateTime = LocalDateTime.parse(text, dateTimeFormatter)

    fun displayDateTime(text: String): String = parse(text).format(displayDateTimeFormatter)

    fun displayDate(text: String): String {
        val datePart = if (text.length >= 10) text.substring(0, 10) else text
        return LocalDate.parse(datePart).format(displayDateFormatter)
    }

    fun receiptDatePart(): String = today().format(receiptDateFormatter)

    data class DateRange(val start: String, val endExclusive: String)

    fun dayRange(date: LocalDate): DateRange {
        val start = "${date} 00:00:00"
        val end = "${date.plusDays(1)} 00:00:00"
        return DateRange(start, end)
    }

    fun weekRange(date: LocalDate): DateRange {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val nextMonday = monday.plusWeeks(1)
        return DateRange("${monday} 00:00:00", "${nextMonday} 00:00:00")
    }

    fun monthRange(date: LocalDate): DateRange {
        val firstDay = date.withDayOfMonth(1)
        val nextMonth = firstDay.plusMonths(1)
        return DateRange("${firstDay} 00:00:00", "${nextMonth} 00:00:00")
    }

    fun customRange(from: LocalDate, to: LocalDate): DateRange {
        val start = "${from} 00:00:00"
        val end = "${to.plusDays(1)} 00:00:00"
        return DateRange(start, end)
    }
}