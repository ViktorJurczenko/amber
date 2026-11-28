// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.calendar

import com.github.se.amber.model.location.GeoLocation

/** The duration used when an event doesn't say how long it lasts. */
const val DEFAULT_DURATION_MINUTES = 60

/** The longest duration an event may have: one day. A longer duration is refused. */
const val MAX_DURATION_MINUTES = 24 * 60

/**
 * Something on the patient's calendar: what it is, when it happens, where, and who comes along.
 *
 * It holds everything the calendar shows. A screen can draw any event from this interface alone.
 * The fields used only for storage stay in the implementing class.
 *
 * An appointment implements it. Each occurrence of a repeating appointment, from `occurrencesIn`,
 * is an appointment too. Other kinds of events might implement it later. The helpers in
 * CalendarStatus.kt work the same way for all of them.
 *
 * An interface can't hold values, so each implementing class declares these fields itself. Computed
 * values are extension functions, not properties. Firestore stores every public getter of a class,
 * so a property here would end up in each document.
 */
interface CalendarEvent {

  // What it is

  /**
   * Identifies the event. For an appointment, it is the document id. The occurrences of a repeating
   * appointment share it, so the key of a list item is [occurrenceKey], not the id alone.
   */
  val id: String

  /** What the calendar shows for it, such as "Dr. Martin, neurologist". */
  val title: String

  /** Free text shown with it, such as "Bring the blood test results". Empty when there is none. */
  val notes: String

  // When it happens

  /** When it starts, in epoch milliseconds. It is shown in the device's time zone. */
  val startAt: Long

  /**
   * How long it lasts, in minutes. Null means unknown. An unknown duration counts as
   * [DEFAULT_DURATION_MINUTES].
   */
  val durationMinutes: Int?

  // Where, and who comes along

  /** Where it takes place. Null when it isn't set. */
  val location: GeoLocation?

  /**
   * The uids of the circle members who come along (CareCircle.memberIds). The patient always takes
   * part and is not listed.
   */
  val attendeeIds: List<String>
}

/**
 * Tells one occurrence apart from the others, such as "a1@1764000000000". The occurrences of a
 * repeating appointment share their [CalendarEvent.id] and differ by [CalendarEvent.startAt], so
 * the key holds both. The screens use it as the key of a list item, and the reminders as the tag of
 * the notification.
 *
 * The reminders store it on the phone, so changing its format would show their reminders again.
 *
 * @return the id and the start, joined by "@".
 */
fun CalendarEvent.occurrenceKey(): String = "$id@$startAt"
