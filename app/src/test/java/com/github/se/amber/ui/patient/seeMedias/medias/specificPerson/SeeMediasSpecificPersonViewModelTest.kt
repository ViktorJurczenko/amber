// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeeMediasSpecificPersonViewModelTest {

  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()
  private val testDispatcher = UnconfinedTestDispatcher()

  private val personId = "person1"

  private val profile =
      UserProfile(
          uid = "patient",
          role = Role.PATIENT,
          person = Person(),
          circleIds = listOf("circle1"),
      )

  // The person is a member without an account by default: it is in the circle
  private val circle =
      CareCircle(
          id = "circle1",
          people = mapOf(personId to Person(firstName = "Evangeline", nickname = "Eva")),
      )

  private val withPerson = MediaItem.Picture(id = "p1", shownIds = listOf(personId))

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(id: String = personId) =
      SeeMediasSpecificPersonViewModel(
          personId = id,
          mediaRepository = mediaRepository,
          circleRepository = circleRepository,
          profileRepository = profileRepository,
      )

  // ---------------------------------------------------------------------------------------------
  // Filtering
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `state is Empty when there is no media at all`() {
    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun `state is Loading until the first media list arrives`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Loading)
  }

  // ---------------------------------------------------------------------------------------------
  // Errors
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `blocking error when the profile fails to load`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))

    val viewModel = createViewModel()

    val state = viewModel.uiState.value as SeeGeneralMediasUIState.BlockingError
    assertEquals(R.string.seeMediasSpecificPersonErrorLoadProfile, state.errorMsgRes)
  }

  @Test
  fun `blocking error when no profile is found`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(R.string.seeMediasSpecificPersonErrorNoProfile, state.errorMsgRes)
  }

  @Test
  fun `blocking error when the patient is in no circle`() {
    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = emptyList()))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(R.string.seeMediasSpecificPersonErrorNoCircle, state.errorMsgRes)
  }

  @Test
  fun `blocking error when the circle fails to load`() {
    coEvery { circleRepository.getCircle("circle1") } returns Result.failure(Exception("boom"))

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(R.string.seeMediasSpecificPersonErrorLoadCircle, state.errorMsgRes)
  }

  @Test
  fun `blocking error when the media flow fails before anything is displayed`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw RuntimeException("offline") }

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError

    assertEquals(R.string.seeMediasSpecificPersonErrorLoadMedia, state.errorMsgRes)
  }

  @Test
  fun `retry reloads the medias after a blocking error`() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.BlockingError)

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(withPerson))
    viewModel.retry()

    assertEquals(
        listOf(withPerson),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
  }
}
