// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

import com.github.se.amber.model.location.GeoLocation
import com.google.firebase.firestore.util.CustomClassMapper
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the validation and the Firestore mapping of [Appointment].
 *
 * The mapping tests use [CustomClassMapper], the SDK's own mapper behind set() and toObject(). It
 * is an internal class, so a Firebase update may move it. These tests then need the new location.
 */
class AppointmentTest {

  private val valid = Appointment(id = "a1", title = "Dr. Martin", startAt = 1_000L)

  private val stored =
      Appointment(
          id = "a1",
          title = "Dr. Martin",
          notes = "Bring the blood test results",
          startAt = 1_000L,
          durationMinutes = 30,
          location = GeoLocation(lat = 46.5, lng = 6.6, placeName = "CHUV"),
          attendeeIds = listOf("u1", "u2"),
          reminderMinutesBefore = listOf(60, 24 * 60),
          patientId = "pat",
          lastEndAt = 1_000L + 30.minutes.inWholeMilliseconds,
          createdBy = "u1",
          createdAt = 500L,
          updatedBy = "u2",
          updatedAt = 900L,
      )

  @Test
  fun aValidAppointmentHasNoError() {
    assertNull(valid.validationError())
    assertNull(valid.copy(startAt = 1L).validationError())
    assertNull(valid.copy(durationMinutes = 1).validationError())
    // One day, written out so that a wrong MAX_DURATION_MINUTES fails.
    assertNull(valid.copy(durationMinutes = 24 * 60).validationError())
    // No reminder, and the limits: 5 values, from 0 minutes to 7 days written out.
    assertNull(valid.copy(reminderMinutesBefore = emptyList()).validationError())
    assertNull(
        valid.copy(reminderMinutesBefore = listOf(0, 1, 2, 3, 7 * 24 * 60)).validationError()
    )
  }

  @Test
  fun aBlankTitleIsRefused() {
    listOf("", "  ", "\t\n").forEach { title ->
      assertEquals(
          AppointmentValidationError.BLANK_TITLE,
          valid.copy(title = title).validationError(),
      )
    }
  }

  @Test
  fun aMissingStartIsRefused() {
    assertEquals(
        AppointmentValidationError.MISSING_START,
        valid.copy(startAt = 0L).validationError(),
    )
    assertEquals(
        AppointmentValidationError.MISSING_START,
        valid.copy(startAt = -1L).validationError(),
    )
  }

  @Test
  fun aDurationOutsideTheRangeIsRefused() {
    listOf(0, -1, 24 * 60 + 1).forEach { minutes ->
      assertEquals(
          AppointmentValidationError.INVALID_DURATION,
          valid.copy(durationMinutes = minutes).validationError(),
      )
    }
  }

  @Test
  fun invalidRemindersAreRefused() {
    listOf(listOf(1, 2, 3, 4, 5, 6), listOf(-1), listOf(60, 7 * 24 * 60 + 1)).forEach { reminders ->
      assertEquals(
          AppointmentValidationError.INVALID_REMINDERS,
          valid.copy(reminderMinutesBefore = reminders).validationError(),
      )
    }
  }

  @Test
  fun checksRunInTheOrderOfTheEnum() {
    assertEquals(
        AppointmentValidationError.BLANK_TITLE,
        Appointment(title = " ", startAt = 0L, durationMinutes = 0).validationError(),
    )
    assertEquals(
        AppointmentValidationError.MISSING_START,
        valid.copy(startAt = 0L, durationMinutes = 0).validationError(),
    )
    assertEquals(
        AppointmentValidationError.INVALID_DURATION,
        valid.copy(durationMinutes = 0, reminderMinutesBefore = listOf(-1)).validationError(),
    )
  }

  @Test
  fun theStatusHelpersAreNotStoredByFirestore() {
    @Suppress("UNCHECKED_CAST")
    val map = CustomClassMapper.convertToPlainJavaTypes(stored) as Map<String, Any?>

    assertEquals(
        setOf(
            "id",
            "title",
            "notes",
            "startAt",
            "durationMinutes",
            "location",
            "attendeeIds",
            "reminderMinutesBefore",
            "patientId",
            "lastEndAt",
            "createdBy",
            "createdAt",
            "updatedBy",
            "updatedAt",
        ),
        map.keys,
    )
    assertEquals(setOf("lat", "lng", "placeName"), (map["location"] as Map<*, *>).keys)
  }

  @Test
  fun anAppointmentReadBackFromFirestoreIsUnchanged() {
    val map = CustomClassMapper.convertToPlainJavaTypes(stored)

    assertEquals(stored, CustomClassMapper.convertToCustomClass(map, Appointment::class.java, null))
  }

  @Test
  fun anEmptyReminderListIsReadBackEmptyNotAsTheDefault() {
    val noReminder = stored.copy(reminderMinutesBefore = emptyList())
    val map = CustomClassMapper.convertToPlainJavaTypes(noReminder)

    val read = CustomClassMapper.convertToCustomClass(map, Appointment::class.java, null)

    assertEquals(emptyList<Int>(), read.reminderMinutesBefore)
  }

  @Test
  fun remindersStoredAsLongsAreReadBackAsInts() {
    // Firestore gives back every whole number as a Long, so the mapper must turn them into Ints.
    @Suppress("UNCHECKED_CAST")
    val map = CustomClassMapper.convertToPlainJavaTypes(stored) as Map<String, Any?>
    val fromServer = map + ("reminderMinutesBefore" to listOf(60L, 1440L))

    val read = CustomClassMapper.convertToCustomClass(fromServer, Appointment::class.java, null)

    // An Integer never equals a Long, so this fails if the mapper kept the Longs.
    assertEquals(listOf(60, 1440), read.reminderMinutesBefore)
  }
}
