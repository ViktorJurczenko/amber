// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.calendar.CalendarEvent
import com.github.se.amber.model.location.GeoLocation

/**
 * An appointment of the patient (doctor, physio, hairdresser...), shown to them so they know what
 * is planned and who will come along. Stored at careCircles/{circleId}/appointments/{id}.
 *
 * Written: created, edited and deleted by every member of the circle, the patient included. Each
 * member can edit or delete any appointment, not only its creator. Created once with set(). Later
 * edits update only the editable fields. A cancelled appointment is deleted. [location] is picked
 * from CareCircle.places (a copy, so later edits to the place don't change it) or entered for a
 * one-off place. The repository fills in [lastEndAt] and the update fields itself. On create, it
 * also fills in the creator fields. It never trusts the values it is given for them.
 *
 * Read: the calendar queries the appointments of a window of days (lastEndAt > its start and
 * startAt < its end) and keeps a snapshot listener, so changes made by other members show up live.
 * An appointment that started before the window and isn't over is listed too.
 *
 * Status: planned, soon, ongoing and passed are computed on the phone with the helpers of
 * CalendarStatus.kt, such as `statusAt`. They are never stored.
 *
 * Rules: readable and writable by every member of the circle (CareCircle.memberIds). On every save,
 * the rules check that:
 * - the document holds only the fields of this class, and [id] is the document id;
 * - it passes the checks of `validationError`: [title] isn't blank, [startAt] is set,
 *   [durationMinutes] is null or between 1 and 1440, and [reminderMinutesBefore] holds at most 5
 *   values from 0 to 7 days;
 * - [lastEndAt] is [startAt] plus [durationMinutes], or plus 60 minutes when it is null;
 * - [recurrence] is null. The check of [lastEndAt] above only fits a one-off appointment, so the
 *   rules refuse a series;
 * - every field has the type this class expects, so the app can read it back;
 * - [updatedBy] is the user saving it;
 * - every attendee is a member of the circle.
 *
 * On create, [createdBy] must be the user saving it, and [patientId] must be the circle's
 * patientId, which can't be "". An edit can't change [createdBy], [createdAt] or [patientId].
 *
 * Account deletion: the backend deletes the appointments the user created. On the others, it
 * removes the user from [attendeeIds], and sets [updatedBy] to "" if they saved it last. When the
 * user is the patient, it deletes every appointment of the circle, since [patientId] can't change.
 *
 * Reminders: [reminderMinutesBefore] says when they come. Everyone linked to the appointment gets
 * them: [patientId] and the attendees (see `isLinkedTo`).
 *
 * Recurrence: an appointment with a [recurrence] repeats, such as physio every Tuesday. It is
 * stored once, as one document for the whole series. Read appointments through `occurrencesIn`,
 * which gives each occurrence in a window of days as a copy with its own [startAt]. Edit or delete
 * the appointment itself, never a copy (see `occurrencesIn`).
 *
 * New fields must get a default value, so documents written before they existed can still be read.
 * Computed values must be extension functions, like [validationError]. Firestore stores every
 * public getter, so a property would be written to each document.
 */
data class Appointment(
    // Shown on the calendar. Each field is described in CalendarEvent; only what is specific to
    // appointments is noted here. Members edit these.

    // What it is
    override val id: String = "", // the document id
    override val title: String = "", // the doctor goes here, not in attendeeIds
    override val notes: String = "",

    // When it happens
    override val startAt: Long = 0L,
    override val durationMinutes: Int? = null,
    // How it repeats; null means it happens once. For a series, startAt is its first occurrence.
    val recurrence: Recurrence? = null,

    // Where, and who comes along
    override val location: GeoLocation? = null, // copied from a Place, or entered
    override val attendeeIds: List<String> = emptyList(),

    // When the reminders come

    // In minutes before startAt. For example [60, 1440] means 1 hour and 1 day before. An empty
    // list means no reminder. Every linked person gets the same reminders, since there is one list
    // per appointment.
    val reminderMinutesBefore: List<Int> = DEFAULT_REMINDER_MINUTES,

    // The circle's patient (CareCircle.patientId). Set by the repository on create. It never
    // changes after.
    val patientId: String = "",

    // Stored for queries. Set by the repository.

    // End time of the last occurrence, in epoch milliseconds (see computeLastEndAt). The "not over
    // yet" query filters on it. For a one-off appointment, it is its end. A series without an end
    // stores Long.MAX_VALUE, so it stays in every later window. That is why it is stored, and why
    // it is named lastEndAt rather than endAt.
    val lastEndAt: Long = 0L,

    // Record keeping. Set by the repository: the created fields on create, the updated fields on
    // every save. Times are in epoch milliseconds.
    val createdBy: String = "", // the member who created it
    val createdAt: Long = 0L,
    val updatedBy: String = "", // the last member who saved it; "" once their account is deleted
    val updatedAt: Long = 0L,
) : CalendarEvent
