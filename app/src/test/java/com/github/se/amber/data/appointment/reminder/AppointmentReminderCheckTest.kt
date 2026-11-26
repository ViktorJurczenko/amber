// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

import com.github.se.amber.data.appointment.AppointmentError
import com.github.se.amber.data.appointment.AppointmentRepository
import com.github.se.amber.data.appointment.AppointmentsRead
import com.github.se.amber.data.appointment.FakeAppointmentRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.appointment.Appointment
import com.github.se.amber.model.appointment.PlannedReminder
import com.github.se.amber.model.authentication.AuthRepository
import com.github.se.amber.model.authentication.AuthUser
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.user.UserProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Checks what the reminder check shows, what it stores and when it sets the timer, from the
 * appointments in a fake repository.
 */
class AppointmentReminderCheckTest {

  private val appointments = FakeAppointmentRepository(ZoneOffset.UTC)
  private val profiles: ProfileRepository = mockk()
  private val auth: AuthRepository = mockk()
  private val poster: AppointmentReminderPoster = mockk()
  private val store = MemoryStore()
  private val timer = RecordingTimer()
  private var circles = listOf(CIRCLE)
  private var now = NOW
  private val posted = mutableListOf<PlannedReminder>()
  private val postedIn = mutableListOf<String>()

  private val check = checkOn(appointments, store)

  private fun checkOn(repository: AppointmentRepository, store: ReminderCheckStore) =
      AppointmentReminderCheck(
          repository,
          profiles,
          auth,
          poster,
          store,
          timer,
          zone = { ZoneOffset.UTC },
          nowMillis = { now },
      )

  init {
    signIn(PATIENT)
    coEvery { poster.post(any(), any()) } answers
        {
          posted += secondArg<PlannedReminder>()
          postedIn += firstArg<String>()
          true
        }
  }

  /**
   * Signs [uid] in, with [circles] on their profile. The store then reads and writes their time.
   */
  private fun signIn(uid: String?) {
    every { auth.currentUser } returns uid?.let(::AuthUser)
    coEvery { profiles.getCurrentProfile() } answers
        {
          Result.success(uid?.let { UserProfile(uid = it, circleIds = circles) })
        }
    uid?.let { store.user = it }
  }

  private fun save(vararg appointment: Appointment) = runBlocking {
    appointment.forEach { appointments.addAppointment(CIRCLE_WITH_PATIENT, it) }
  }

  private fun saveIn(circle: CareCircle, vararg appointment: Appointment) = runBlocking {
    appointment.forEach { appointments.addAppointment(circle, it) }
  }

  private fun run() = runBlocking { check.run() }

  @Test
  fun showsWhatIsDueSinceTheLastCheckSavesTheTimeAndSetsTheTimerForTheNextOne() {
    store.last = NOW - 10 * MINUTE
    save(
        // due 5 minutes ago: shown
        appointment("due", start = NOW + 55 * MINUTE),
        // due 30 minutes ago, before the last check: shown then already
        appointment("old", start = NOW + 30 * MINUTE),
        // due in 2 hours: the next one
        appointment("next", start = NOW + 3 * HOUR),
        appointment("later", start = NOW + 5 * HOUR),
    )

    run()

    assertEquals(listOf("due" to 60), posted.map { it.occurrence.id to it.minutesBefore })
    assertEquals(NOW, store.last)
    assertEquals(NOW + 2 * HOUR, timer.setAt)
  }

  @Test
  fun aReminderDueRightNowIsShownAndNotPlannedAgain() {
    store.last = NOW - MINUTE
    save(appointment("now", start = NOW + HOUR, reminders = listOf(60, 30)))

    run()

    assertEquals(listOf(60), posted.map { it.minutesBefore })
    assertEquals(NOW + 30 * MINUTE, timer.setAt)
  }

  @Test
  fun withoutAReminderWithinSixHoursTheTimerComesBackInSixHours() {
    save(
        // its reminder is in 7 hours
        appointment("later", start = NOW + 8 * HOUR),
        appointment("none", start = NOW + HOUR, reminders = emptyList()),
    )

    run()

    assertEquals(NOW + 6 * HOUR, timer.setAt)
    assertEquals(NOW, store.last)
  }

  @Test
  fun aReminderSevenDaysAheadOfAnAppointmentOnTheNinthDaySetsTheTimer() {
    // 23:00, so 7 days and 5 hours from now is on the 9th day, counting today
    now = NOW + 11 * HOUR
    save(appointment("trip", start = now + 7 * DAY + 5 * HOUR, reminders = listOf(7 * 24 * 60)))

    run()

    assertEquals(now + 5 * HOUR, timer.setAt)
  }

  @Test
  fun onFirstUseNothingOldIsShown() {
    save(appointment("due", start = NOW + 55 * MINUTE), appointment("next", start = NOW + 3 * HOUR))

    run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW, store.last)
    assertEquals(NOW + 2 * HOUR, timer.setAt)
  }

  @Test
  fun anAppointmentSavedAfterItsReminderTimeGetsItAtTheNextCheck() {
    store.last = NOW - 10 * MINUTE
    // "1 day before" had passed when it was saved, a minute ago
    save(
        appointment(
            "late",
            start = NOW + 2 * HOUR,
            reminders = listOf(24 * 60),
            saved = NOW - MINUTE,
        )
    )

    run()

    assertEquals(listOf("late"), posted.map { it.occurrence.id })
    assertEquals(NOW - MINUTE, posted.single().fireAt)
  }

  @Test
  fun nothingForAnAppointmentDeletedMovedOrWithoutThatReminderTime() = runBlocking {
    store.last = NOW - 10 * MINUTE
    val deleted = appointment("deleted", start = NOW + 55 * MINUTE)
    val moved = appointment("moved", start = NOW + 55 * MINUTE)
    val changed = appointment("changed", start = NOW + 55 * MINUTE, reminders = listOf(60, 10))
    save(deleted, moved, changed)

    appointments.deleteAppointment(CIRCLE, deleted.id)
    appointments.updateAppointment(CIRCLE, moved.copy(startAt = NOW + DAY + 2 * HOUR))
    appointments.updateAppointment(CIRCLE, changed.copy(reminderMinutesBefore = listOf(10)))
    check.run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    // the moved one comes the day before, and the changed one at its new time
    assertEquals(NOW + 45 * MINUTE, timer.setAt)
  }

  @Test
  fun thePatientGetsEveryAppointmentACaregiverOnlyTheOnesTheyAttend() {
    store.last = NOW - 10 * MINUTE
    save(
        appointment("withAnna", start = NOW + 55 * MINUTE, attendees = listOf(ANNA)),
        appointment("alone", start = NOW + 56 * MINUTE),
    )

    run()
    assertEquals(listOf("withAnna", "alone"), posted.map { it.occurrence.id })

    for ((uid, expected) in listOf(ANNA to listOf("withAnna"), BEN to emptyList())) {
      posted.clear()
      signIn(uid)
      store.last = NOW - 10 * MINUTE
      run()
      assertEquals(uid, expected, posted.map { it.occurrence.id })
    }
  }

  @Test
  fun aCaregiverRemovedFromTheAttendeesGetsNothing() = runBlocking {
    store.last = NOW - 10 * MINUTE
    val doctor = appointment("doctor", start = NOW + 55 * MINUTE, attendees = listOf(ANNA, BEN))
    save(doctor)
    signIn(ANNA)

    appointments.updateAppointment(CIRCLE, doctor.copy(attendeeIds = listOf(BEN)))
    check.run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW + 6 * HOUR, timer.setAt)
  }

  @Test
  fun nothingForAnAppointmentThatHasAlreadyStarted() {
    store.last = NOW - 2 * HOUR
    // its reminder came 70 minutes ago, while the phone was off
    save(appointment("started", start = NOW - 10 * MINUTE))

    run()

    assertEquals(emptyList<PlannedReminder>(), posted)
  }

  @Test
  fun ofSeveralDueRemindersOfOneAppointmentOnlyTheLatestIsShown() {
    store.last = NOW - 2 * HOUR
    save(appointment("doctor", start = NOW + 20 * MINUTE, reminders = listOf(60, 30)))

    run()

    assertEquals(listOf(30), posted.map { it.minutesBefore })
  }

  @Test
  fun signedOutNothingIsShownAndTheTimerComesBackInSixHours() {
    store.last = NOW - 10 * MINUTE
    timer.setAt = NOW + HOUR
    save(appointment("due", start = NOW + 55 * MINUTE))
    signIn(null)

    run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW + 6 * HOUR, timer.setAt)
    assertEquals(NOW - 10 * MINUTE, store.last)
  }

  @Test
  fun withoutACircleNothingIsShownAndTheTimerComesBackInSixHours() {
    store.last = NOW - 10 * MINUTE
    timer.setAt = NOW + HOUR
    save(appointment("due", start = NOW + 55 * MINUTE))
    circles = emptyList()

    run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW + 6 * HOUR, timer.setAt)
  }

  @Test
  fun withoutAProfileNothingIsShownAndTheTimerComesBackInSixHours() {
    store.last = NOW - 10 * MINUTE
    save(appointment("due", start = NOW + 55 * MINUTE))
    coEvery { profiles.getCurrentProfile() } returns Result.success(null)

    run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW + 6 * HOUR, timer.setAt)
  }

  @Test
  fun aProfileThatCantBeReadIsTriedAgainInFifteenMinutes() {
    store.last = NOW - 10 * MINUTE
    timer.setAt = NOW + HOUR
    save(appointment("due", start = NOW + 55 * MINUTE))
    coEvery { profiles.getCurrentProfile() } returns
        Result.failure(IllegalStateException("offline"))

    run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW + 15 * MINUTE, timer.setAt)
    assertEquals(NOW - 10 * MINUTE, store.last)
  }

  @Test
  fun appointmentsThatCantBeReadAreTriedAgainInFifteenMinutes() {
    store.last = NOW - 10 * MINUTE
    save(appointment("due", start = NOW + 55 * MINUTE))
    appointments.readFailWith = AppointmentError.Unknown(IllegalStateException("server error"))

    run()

    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW + 15 * MINUTE, timer.setAt)
    assertEquals(NOW - 10 * MINUTE, store.last)
  }

  @Test
  fun aReminderOfACircleThatIsntOnScreenIsShown() {
    store.last = NOW - 10 * MINUTE
    circles = listOf(CIRCLE, OTHER_CIRCLE)
    save(appointment("here", start = NOW + 55 * MINUTE))
    saveIn(OTHER_CIRCLE_WITH_PATIENT, appointment("there", start = NOW + 56 * MINUTE))
    // the next reminder of each circle: the other circle's comes first
    save(appointment("hereLater", start = NOW + 4 * HOUR))
    saveIn(OTHER_CIRCLE_WITH_PATIENT, appointment("thereLater", start = NOW + 3 * HOUR))

    run()

    assertEquals(listOf("here", "there"), posted.map { it.occurrence.id })
    assertEquals(listOf(CIRCLE, OTHER_CIRCLE), postedIn)
    assertEquals(NOW + 2 * HOUR, timer.setAt)
    assertEquals(NOW, store.last)
  }

  @Test
  fun aCircleTheUserCantReadIsSkippedAndTheOthersStillCome() {
    store.last = NOW - 10 * MINUTE
    circles = listOf(CIRCLE, OTHER_CIRCLE, "bad/id")
    save(appointment("here", start = NOW + 55 * MINUTE))
    val repository: AppointmentRepository = mockk {
      coEvery { getAppointments(CIRCLE, any(), any()) } coAnswers
          {
            appointments.getAppointments(CIRCLE, secondArg(), thirdArg())
          }
      coEvery { getAppointments(OTHER_CIRCLE, any(), any()) } returns
          Result.failure(AppointmentError.PermissionDenied())
      coEvery { getAppointments("bad/id", any(), any()) } returns
          Result.failure(AppointmentError.InvalidCircleId())
    }

    runBlocking { checkOn(repository, store).run() }

    assertEquals(listOf("here"), posted.map { it.occurrence.id })
    assertEquals(NOW + 6 * HOUR, timer.setAt)
    assertEquals(NOW, store.last)
  }

  @Test
  fun aCacheAnswerIsShownAndTriedAgainInFifteenMinutes() {
    store.last = NOW - 10 * MINUTE
    save(appointment("due", start = NOW + 55 * MINUTE))
    appointments.readFromCache = true

    run()

    // shown and stored, so the next try doesn't show it again
    assertEquals(listOf("due"), posted.map { it.occurrence.id })
    assertEquals(NOW, store.last)
    assertEquals(NOW + 15 * MINUTE, timer.setAt)
  }

  @Test
  fun aCacheAnswerKeepsAReminderDueSoonerThanTheRetry() {
    store.last = NOW - 10 * MINUTE
    save(appointment("soon", start = NOW + 65 * MINUTE))
    appointments.readFromCache = true

    run()

    assertEquals(NOW + 5 * MINUTE, timer.setAt)
  }

  @Test
  fun eachUserHasTheirOwnLastCheck() {
    store.last = NOW - 10 * MINUTE
    save(appointment("due", start = NOW + 55 * MINUTE, attendees = listOf(ANNA)))
    signIn(ANNA)

    run()

    // Anna has never checked on this phone, so she starts from now
    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(mapOf(PATIENT to NOW - 10 * MINUTE, ANNA to NOW), store.times)
  }

  @Test
  fun aRunStoppedWhileShowingAReminderStopsAndStillSetsTheTimer() {
    store.last = NOW - 10 * MINUTE
    save(appointment("due", start = NOW + 55 * MINUTE))
    coEvery { poster.post(CIRCLE, any()) } throws CancellationException("stopped")

    assertThrows(CancellationException::class.java) { run() }

    // the time isn't stored, so the next run shows the reminder again
    assertEquals(NOW - 10 * MINUTE, store.last)
    assertEquals(NOW + 15 * MINUTE, timer.setAt)
  }

  @Test
  fun aRunStoppedWhileReadingTheAppointmentsStopsAndStillSetsTheTimer() {
    val stopped: AppointmentRepository = mockk {
      coEvery { getAppointments(CIRCLE, any(), any()) } throws CancellationException("stopped")
    }

    assertThrows(CancellationException::class.java) {
      runBlocking { checkOn(stopped, store).run() }
    }

    assertEquals(NOW + 15 * MINUTE, timer.setAt)
  }

  @Test
  fun itPlansFromTheOneTimeReadNotFromTheListenersFirstList() {
    store.last = NOW - 10 * MINUTE
    // the listener would give the phone's old cache first, without the appointment added meanwhile
    val added = appointment("added", start = NOW + 55 * MINUTE)
    val repository: AppointmentRepository = mockk {
      every { observeAppointments(CIRCLE, any(), any()) } returns flowOf(emptyList())
      coEvery { getAppointments(CIRCLE, any(), any()) } returns
          Result.success(AppointmentsRead(listOf(added), isFromCache = false))
    }

    runBlocking { checkOn(repository, store).run() }

    assertEquals(listOf("added"), posted.map { it.occurrence.id })
  }

  @Test
  fun aStoreThatCantBeReadIsTriedAgainInFifteenMinutes() {
    store.last = NOW - 10 * MINUTE
    save(appointment("due", start = NOW + 55 * MINUTE))
    val failing =
        object : ReminderCheckStore by store {
          override suspend fun lastCheckedAt(uid: String): Long? = throw IOException("disk error")
        }

    assertThrows(IOException::class.java) { runBlocking { checkOn(appointments, failing).run() } }

    // starting from now would skip the reminder, so nothing is shown and the time is kept
    assertEquals(emptyList<PlannedReminder>(), posted)
    assertEquals(NOW - 10 * MINUTE, store.last)
    assertEquals(NOW + 15 * MINUTE, timer.setAt)
  }

  @Test
  fun oneFailedPostDoesntStopTheOthers() {
    store.last = NOW - 10 * MINUTE
    save(
        appointment("first", start = NOW + 55 * MINUTE),
        appointment("second", start = NOW + 56 * MINUTE),
    )
    coEvery { poster.post(CIRCLE, match { it.occurrence.id == "first" }) } throws
        IllegalStateException("Boom")

    run()

    assertEquals(listOf("second"), posted.map { it.occurrence.id })
    assertEquals(NOW, store.last)
  }

  @Test
  fun twoChecksNeverRunAtOnce() = runBlocking {
    store.last = NOW - 10 * MINUTE
    save(appointment("due", start = NOW + 55 * MINUTE))
    val release = CompletableDeferred<Unit>()
    coEvery { poster.post(CIRCLE, any()) } coAnswers
        {
          release.await()
          true
        }

    val first = launch(Dispatchers.Unconfined) { check.run() }
    val second = launch(Dispatchers.Unconfined) { check.run() }
    yield()
    // the first one waits in post, so the second one hasn't read the time yet
    assertEquals(1, store.reads)

    release.complete(Unit)
    first.join()
    second.join()
    assertEquals(2, store.reads)
  }

  /** A [ReminderCheckStore] in memory, which counts its reads. */
  private class MemoryStore : ReminderCheckStore {
    val times = mutableMapOf<String, Long>()
    var reads = 0

    /** The user whose time [last] reads and writes: the one signed in. */
    var user = PATIENT

    var last: Long?
      get() = times[user]
      set(value) {
        if (value == null) times.remove(user) else times[user] = value
      }

    override suspend fun lastCheckedAt(uid: String): Long? = times[uid].also { reads++ }

    override suspend fun setLastCheckedAt(uid: String, millis: Long) {
      times[uid] = millis
    }

    override suspend fun clear() {
      times.clear()
    }
  }

  /** A [ReminderTimer] that keeps the time it is set for, null once cancelled. */
  private class RecordingTimer : ReminderTimer {
    var setAt: Long? = null

    override fun set(atMillis: Long) {
      setAt = atMillis
    }

    override fun cancel() {
      setAt = null
    }
  }

  private companion object {
    const val CIRCLE = "c1"
    const val PATIENT = "patient"
    const val ANNA = "anna"
    const val BEN = "ben"
    const val OTHER_CIRCLE = "c2"
    val CIRCLE_WITH_PATIENT =
        CareCircle(id = CIRCLE, patientId = PATIENT, memberIds = listOf(PATIENT, ANNA, BEN))
    val OTHER_CIRCLE_WITH_PATIENT =
        CareCircle(id = OTHER_CIRCLE, patientId = PATIENT, memberIds = listOf(PATIENT))

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    // Friday 15 January 2027, 12:00 UTC
    val NOW = LocalDateTime.of(2027, 1, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** An appointment of the patient, as saved, with a reminder 1 hour before by default. */
    fun appointment(
        id: String,
        start: Long,
        reminders: List<Int> = listOf(60),
        attendees: List<String> = emptyList(),
        saved: Long = NOW - 30 * DAY,
    ) =
        Appointment(
            id = id,
            title = id,
            startAt = start,
            durationMinutes = 30,
            patientId = PATIENT,
            attendeeIds = attendees,
            reminderMinutesBefore = reminders,
            updatedAt = saved,
        )
  }
}
