// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When the event starts, as the date and the clock time in [zone]. The screens and the notification
 * text show it.
 *
 * @param zone the time zone to show it in, usually the phone's.
 * @return the start, as a clock time in [zone].
 */
fun CalendarEvent.startDateTime(zone: ZoneId): LocalDateTime = startAt.toLocalDateTime(zone)

/**
 * When the event ends, as the date and the clock time in [zone]. It comes from [endAt], so an
 * unknown duration counts as [DEFAULT_DURATION_MINUTES].
 *
 * @param zone the time zone to show it in, usually the phone's.
 * @return the end, as a clock time in [zone].
 */
fun CalendarEvent.endDateTime(zone: ZoneId): LocalDateTime = endAt().toLocalDateTime(zone)

/**
 * This clock time in [zone], in epoch milliseconds. The add and edit form uses it to turn the
 * picked date and time into [CalendarEvent.startAt].
 *
 * It follows `ZonedDateTime.of`. On a normal day, it gives back the time that [startDateTime]
 * showed. Two cases differ, on the days of a daylight saving change:
 * - in spring, a time in the gap doesn't exist. It moves forward by the length of the gap. In
 *   Zurich, 02:30 on the day clocks go from 02:00 to 03:00 becomes 03:30;
 * - in autumn, a time in the overlap happens twice. It takes the earlier one, still on summer time.
 *
 * @param zone the time zone the clock time is in, usually the phone's.
 * @return the time, in epoch milliseconds.
 */
fun LocalDateTime.toEpochMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()

/** @return this time in epoch milliseconds, as a clock time in [zone]. */
private fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
