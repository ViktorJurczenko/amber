// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias.texts

import androidx.lifecycle.ViewModel
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Covers preview fakes that Studio never calls, so Jacoco stays above the project threshold. */
@OptIn(ExperimentalCoroutinesApi::class)
class SeeTextsPreviewsTest {

  private val profile =
      UserProfile(
          uid = "preview",
          role = Role.PATIENT,
          person = Person(firstName = "Circee"),
          circleIds = listOf("preview"),
      )
  private val circle = CareCircle(id = "preview")

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private suspend fun assertFails(block: suspend () -> Unit) {
    try {
      block()
      fail("Expected IllegalStateException")
    } catch (_: IllegalStateException) {
      // expected
    }
  }

  @Test
  fun previewMediaRepository_observeApprovedMedia_andUnusedApis() = runTest {
    val media = listOf(MediaItem.Text(id = "t1", text = "hello"))
    val repo = PreviewMediaRepository(flowOf(media))
    assertEquals(media, repo.observeApprovedMedia("preview").first())

    assertThrows(IllegalStateException::class.java) { repo.newMediaId("preview") }
    assertThrows(IllegalStateException::class.java) { repo.observeMediaShowing("preview", "p1") }
    assertThrows(IllegalStateException::class.java) { repo.observeCircleMedia("preview") }
    assertThrows(IllegalStateException::class.java) { repo.observeOutbox("preview") }

    assertFails { repo.getMedia("preview", "t1") }
    assertFails { repo.addMedia(circle, MediaItem.Text(id = "t2"), file = null) }
    assertFails { repo.retrySend("preview", "t1") }
    assertFails { repo.discardUnsent("preview", "t1") }
    assertFails { repo.updateMedia("preview", MediaItem.Text(id = "t1")) }
    assertFails { repo.deleteMedia(circle, MediaItem.Text(id = "t1")) }
    assertFails { repo.getFile(circle, MediaItem.Text(id = "t1")) }
    assertFails { repo.clearLocalMedia("preview") }
    assertFails { repo.clearLocalMedia() }
  }

  @Test
  fun previewProfilesAndCircles_coverSuccessErrorAndUnusedApis() = runTest {
    val ok = PreviewProfiles(profile, showError = false)
    assertEquals(profile, ok.getCurrentProfile().getOrNull())
    assertEquals(profile, ok.getProfile("preview").getOrNull())

    val failing = PreviewProfiles(profile, showError = true)
    assertTrue(failing.getCurrentProfile().isFailure)

    assertFails { ok.createProfile(Person(), Role.PATIENT) }
    assertFails { ok.updateOnboardingProfile(Person(), Role.PATIENT) }

    val circles = PreviewCircles(circle)
    assertEquals("preview", circles.newId())
    assertEquals(circle, circles.getCircle("preview").getOrNull())
    assertFails { circles.addPerson("preview", Person()) }
  }

  @Test
  fun previewOwner_factoryAndActiveCircleHelpers() = runTest {
    val owner = SeeTextsPreviewOwner(PreviewTextState.Content)
    val factory = owner.defaultViewModelProviderFactory

    assertTrue(factory.create(SeeTextsViewModel::class.java) is SeeTextsViewModel)
    assertTrue(factory.create(MediaCardViewModel::class.java) is MediaCardViewModel)
    assertThrows(IllegalStateException::class.java) { factory.create(DummyViewModel::class.java) }

    assertEquals("preview", owner.activeCircles.activeCircleId("u", listOf("preview")).first())
    assertNull(owner.activeCircles.activeCircleId("u", listOf(" ", "")).first())
    owner.activeCircles.setActiveCircle("u", "preview")
    owner.activeCircles.clear("u")
  }

  private class DummyViewModel : ViewModel()
}
