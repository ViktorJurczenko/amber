// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.occurrencesIn
import com.github.se.amber.model.calendar.dayWindow
import com.github.se.amber.model.calendar.localDateOf
import com.github.se.amber.ui.theme.CAREGIVER_ICON_SIZE
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId

/**
 * Text style of the month and year at the top of the calendar.
 *
 * Patient screens need larger text than caregiver ones.
 *
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient.
 * @return the typography style to apply to the month and year.
 */
@Composable
internal fun calendarTitleTextStyle(isPatient: Boolean = true): TextStyle =
    if (isPatient) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge

/**
 * Text style of the names of the days of the week and of the day numbers of the calendar.
 *
 * Patient screens need larger text than caregiver ones.
 *
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient.
 * @return the typography style to apply to the weekday names and to the day numbers.
 */
@Composable
internal fun calendarBodyTextStyle(isPatient: Boolean = true): TextStyle =
    if (isPatient) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium

/**
 * Size of the month arrows of the calendar.
 *
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient.
 * @return the patient icon size for a patient, the caregiver icon size otherwise.
 */
internal fun calendarIconSize(isPatient: Boolean = true): Dp =
    if (isPatient) PATIENT_ICON_SIZE else CAREGIVER_ICON_SIZE

/**
 * Lays out the days of [month] as the cells of a calendar grid, week after week.
 *
 * @param month the month to lay out.
 * @param firstDayOfWeek the day shown in the first column (Monday in France, Sunday in the US...).
 * @return the cells, row by row. A cell is the day of the month, or null for an empty cell before
 *   the first day or after the last one. The size is always a multiple of the number of days in a
 *   week.
 */
internal fun calendarCells(
    month: YearMonth = YearMonth.now(),
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): List<Int?> {
  val daysInWeek = DayOfWeek.entries.size

  // Represent the empty cells before the 1st.
  // In other words, how many columns the 1st is after the first column.
  // DayOfWeek values go from 1 (Monday) to 7 (Sunday). The difference can be negative
  // (a month starting on a Monday, with Sunday as first column), so daysInWeek is added before
  // the modulo to get a value between 0 and 6.
  val leadingBlanks =
      (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + daysInWeek) % daysInWeek
  val days: List<Int?> = List(month.lengthOfMonth()) { it + 1 }
  val beforeFirstDay: List<Int?> = List(leadingBlanks) { null }

  // Empty cells after the last day, to complete the last week.
  val trailingBlanks = (daysInWeek - (leadingBlanks + days.size) % daysInWeek) % daysInWeek
  val afterLastDay: List<Int?> = List(trailingBlanks) { null }

  return beforeFirstDay + days + afterLastDay
}

/**
 * The days of [month] on which at least one of the [appointments] starts. Only the start counts: an
 * appointment ending after midnight does not mark the next day.
 *
 * @param appointments the appointments to look at. Those starting in another month are ignored.
 * @param month the month to look at.
 * @param zone the time zone the days are counted in. Defaults to the phone's.
 * @return the days of the month (1 to 31) that have an appointment.
 */
internal fun appointmentDays(
    appointments: List<Appointment> = emptyList(),
    month: YearMonth = YearMonth.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Set<Int> {
  val window = dayWindow(month.atDay(1), month.lengthOfMonth(), zone)
  return appointments
      .flatMap { it.occurrencesIn(window, zone) }
      .map { localDateOf(it.startAt, zone) }
      .filter { YearMonth.from(it) == month }
      .map { it.dayOfMonth }
      .toSet()
}
