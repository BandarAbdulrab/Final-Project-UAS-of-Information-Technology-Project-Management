package com.byteall.kharismart.util.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime

class WibClockTest {

    @After
    fun tearDown() {
        WibClock.clock = Clock.system(WibClock.zone)
    }

    @Test
    fun testTodayStringTransitionsAtMidnightWib() {
        val boundary = ZonedDateTime.of(2026, 3, 1, 23, 59, 59, 0, WibClock.zone)
        WibClock.clock = Clock.fixed(boundary.toInstant(), WibClock.zone)
        assertEquals("2026-03-01", WibClock.todayString())

        val nextSecond = boundary.plusSeconds(1)
        WibClock.clock = Clock.fixed(nextSecond.toInstant(), WibClock.zone)
        assertEquals("2026-03-02", WibClock.todayString())
    }

    @Test
    fun testWeekRangeFromWednesday() {
        val wednesday = LocalDate.of(2026, 3, 4)
        val range = WibClock.weekRange(wednesday)
        assertEquals("2026-03-02 00:00:00", range.start)
        assertEquals("2026-03-09 00:00:00", range.endExclusive)
    }

    @Test
    fun testMonthRangeLeapYear() {
        val febLeap = LocalDate.of(2028, 2, 15)
        val range = WibClock.monthRange(febLeap)
        assertEquals("2028-02-01 00:00:00", range.start)
        assertEquals("2028-03-01 00:00:00", range.endExclusive)
    }

    @Test
    fun testCustomRangeSingleDay() {
        val singleDay = LocalDate.of(2026, 3, 1)
        val range = WibClock.customRange(singleDay, singleDay)
        assertEquals("2026-03-01 00:00:00", range.start)
        assertEquals("2026-03-02 00:00:00", range.endExclusive)
    }
}