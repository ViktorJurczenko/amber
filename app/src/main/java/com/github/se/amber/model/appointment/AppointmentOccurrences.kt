// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.DayWindow
import com.github.se.amber.model.calendar.overlaps
import java.time.ZoneId

/**
 * The occurrences of this appointment that overlap [window], sorted by start. Each one is a copy of
 * the appointment with its own [Appointment.startAt]. The calendar screens and the reminders read
 * appointments through this function.
 *
 * For now, appointments don't repeat. It gives the appointment itself if it overlaps the window,
 * and nothing otherwise. One that started before the window and isn't over is given too. Repeating
 * appointments might be added later. This function would then give each of their occurrences, and
 * its callers wouldn't change.
 *
 * Each occurrence is a copy, for display and reminders only. To edit or delete, take the
 * appointment from `observeAppointments`, never an occurrence. Once appointments can repeat, saving
 * an occurrence would move the whole series to that day.
 *
 * The occurrences of one repeating appointment would share its [Appointment.id]. So the key of a
 * list item must be the id plus [Appointment.startAt], not the id alone.
 *
 * @param window the days to show.
 * @param zone the time zone the days are counted in, usually the phone's. It isn't used yet.
 *   Repeating appointments would need it to keep their clock time across a daylight saving change.
 * @return the occurrences that overlap [window]. The list is empty when there are none.
 */
@Suppress("UNUSED_PARAMETER")
fun Appointment.occurrencesIn(window: DayWindow, zone: ZoneId): List<Appointment> =
    if (overlaps(window)) listOf(this) else emptyList()
