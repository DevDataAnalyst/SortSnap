package com.sortsnap.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Gallery date header groups. The UI turns these into localised strings. */
sealed interface DateBucket {
    data object Today : DateBucket
    data object Yesterday : DateBucket
    data object ThisWeek : DateBucket
    data class Month(val year: Int, val month: Int) : DateBucket
}

object DateBuckets {
    fun of(epochMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): DateBucket {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0L -> DateBucket.Today // also covers clock-skewed future dates
            days == 1L -> DateBucket.Yesterday
            days < 7L -> DateBucket.ThisWeek
            else -> DateBucket.Month(date.year, date.monthValue)
        }
    }

    fun epochDay(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDay(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
}
