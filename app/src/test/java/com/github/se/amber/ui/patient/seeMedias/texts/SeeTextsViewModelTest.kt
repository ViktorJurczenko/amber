// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias.texts

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeeTextsViewModelTest {

  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()

  private val profile =
      UserProfile(
          uid = "patient",
          role = Role.PATIENT,
          person = Person(),
          circleIds = listOf("circle1"),
      )
  private val circle = CareCircle(id = "circle1")

  private val text1 = MediaItem.Text(id = "t1", title = "Market", text = "We went to the market.")
  private val text2 = MediaItem.Text(id = "t2", title = "Park", text = "A walk in the park.")
  private val picture = MediaItem.Picture(id = "p1")
  private val audio = MediaItem.Audio(id = "a1")

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel() =
      SeeTextsViewModel(mediaRepository, circleRepository, profileRepository)

  @Test
  fun keepsOnlyTextsInRepositoryOrder_andCoversLoadingEmptyContentStates() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Loading)

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
    val empty = createViewModel().uiState.value as SeeGeneralMediasUIState.Empty
    assertEquals(profile, empty.patientProfile)
    assertEquals(circle, empty.careCircle)
    assertNull(empty.errorMsgRes)

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture, text1, audio, text2))
    val content = createViewModel().uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(text1, text2), content.items)
    assertEquals(profile, content.patientProfile)
    assertEquals(circle, content.careCircle)

    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(picture, audio))
    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun mediaUpdatesAreReflectedLive_keepingOnlyTexts() {
    val media = MutableStateFlow<List<MediaItem>>(emptyList())
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)

    media.value = listOf(text1, picture)
    assertEquals(listOf(text1), (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items)

    media.value = listOf(text1, text2, audio)
    assertEquals(
        listOf(text1, text2),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )

    media.value = emptyList()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun blockingErrorsUseTextSpecificResources() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(null)
    assertEquals(
        R.string.seeTextsErrorNoProfile,
        (createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError).errorMsgRes,
    )

    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    assertEquals(
        R.string.seeTextsErrorLoadProfile,
        (createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError).errorMsgRes,
    )

    coEvery { profileRepository.getCurrentProfile() } returns
        Result.success(profile.copy(circleIds = emptyList()))
    assertEquals(
        R.string.seeTextsErrorNoCircle,
        (createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError).errorMsgRes,
    )

    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.failure(Exception("circle error"))
    assertEquals(
        R.string.seeTextsErrorLoadCircle,
        (createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError).errorMsgRes,
    )

    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circle)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow { throw RuntimeException("media error") }
    assertEquals(
        R.string.seeTextsErrorLoadMedia,
        (createViewModel().uiState.value as SeeGeneralMediasUIState.BlockingError).errorMsgRes,
    )
  }

  @Test
  fun nonBlockingMediaError_andClearErrorMsg_andRetry() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flow {
          emit(listOf(text1))
          throw RuntimeException("media error")
        }
    val viewModel = createViewModel()
    val withError = viewModel.uiState.value as SeeGeneralMediasUIState.Content
    assertEquals(listOf(text1), withError.items)
    assertEquals(R.string.seeTextsErrorLoadMedia, withError.errorMsgRes)

    viewModel.clearErrorMsg()
    assertNull((viewModel.uiState.value as SeeGeneralMediasUIState.Content).errorMsgRes)

    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    val failing = createViewModel()
    assertTrue(failing.uiState.value is SeeGeneralMediasUIState.BlockingError)
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(listOf(text2))
    failing.retry()
    assertEquals(
        listOf(text2),
        (failing.uiState.value as SeeGeneralMediasUIState.Content).items,
    )
  }
}
