// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import com.github.se.amber.R
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.media.audio.AudioPlayer
import com.github.se.amber.data.media.audio.AudioState
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
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
class SeeMediasSpecificPersonViewModelTest {

  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()
  private val playerState = MutableStateFlow(AudioState())
  private val audioPlayer =
      mockk<AudioPlayer>(relaxed = true) { every { state } returns playerState }
  private val testDispatcher = UnconfinedTestDispatcher()

  private val personId = "person1"
  private val otherId = "person2"

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
  private val withPerson2 = MediaItem.Picture(id = "p2", shownIds = listOf(otherId, personId))
  private val withoutPerson = MediaItem.Picture(id = "p3", shownIds = listOf(otherId))
  private val withNobody = MediaItem.Picture(id = "p4", shownIds = emptyList())
  private val audioWithPerson = MediaItem.Audio(id = "a1", shownIds = listOf(personId))
  private val audioWithoutPerson = MediaItem.Audio(id = "a2", shownIds = listOf(otherId))
  private val textWithPerson = MediaItem.Text(id = "t1", shownIds = listOf(personId))

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
          audioPlayer = audioPlayer,
      )

  // ---------------------------------------------------------------------------------------------
  // Filtering
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `only the medias crediting the person are kept, whatever their type`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(
            listOf(
                withPerson,
                withoutPerson,
                audioWithPerson,
                audioWithoutPerson,
                withPerson2,
                withNobody,
                textWithPerson,
            )
        )

    val state = createViewModel().uiState.value as SeeGeneralMediasUIState.Content

    assertEquals(listOf(withPerson, audioWithPerson, withPerson2, textWithPerson), state.items)
    assertEquals(profile, state.patientProfile)
    assertEquals(circle, state.careCircle)
    assertNull(state.errorMsgRes)
  }

  @Test
  fun `state is Empty when no media credits the person`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(withoutPerson, audioWithoutPerson, withNobody))

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun `state is Empty when there is no media at all`() {
    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun `another person id keeps another list of medias`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(withPerson, withoutPerson))

    val state = createViewModel(otherId).uiState.value as SeeGeneralMediasUIState.Content

    assertEquals(listOf(withoutPerson), state.items)
  }

  @Test
  fun `media updates are reflected live`() {
    val media = MutableStateFlow<List<MediaItem>>(emptyList())
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)

    media.value = listOf(withPerson, withoutPerson)
    assertEquals(
        listOf(withPerson),
        (viewModel.uiState.value as SeeGeneralMediasUIState.Content).items,
    )

    media.value = listOf(withoutPerson)
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Empty)
  }

  @Test
  fun `state is Loading until the first media list arrives`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    assertTrue(createViewModel().uiState.value is SeeGeneralMediasUIState.Loading)
  }

  // ---------------------------------------------------------------------------------------------
  // personName
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `personName is null while the screen is loading`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }

    assertNull(createViewModel().personName.value)
  }

  @Test
  fun `personName is the nickname of a person of the circle`() {
    assertEquals("Eva", createViewModel().personName.value)
    coVerify(exactly = 0) { profileRepository.getProfile(any()) }
  }

  @Test
  fun `personName falls back on the first name when the nickname is blank`() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(
            circle.copy(
                people = mapOf(personId to Person(firstName = "Evangeline", nickname = " "))
            )
        )

    assertEquals("Evangeline", createViewModel().personName.value)
  }

  @Test
  fun `personName falls back on the first name when there is no nickname`() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(
            circle.copy(
                people = mapOf(personId to Person(firstName = "Evangeline", nickname = null))
            )
        )

    assertEquals("Evangeline", createViewModel().personName.value)
  }

  @Test
  fun `personName comes from the profile when the person is a member of the circle`() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(circle.copy(people = emptyMap()))
    coEvery { profileRepository.getProfile(personId) } returns
        Result.success(
            UserProfile(
                uid = personId,
                role = Role.CAREGIVER,
                person = Person(firstName = "Marc", nickname = "Marco"),
            )
        )

    assertEquals("Marco", createViewModel().personName.value)
  }

  @Test
  fun `personName is null when the person cannot be found anywhere`() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(circle.copy(people = emptyMap()))
    coEvery { profileRepository.getProfile(personId) } returns Result.failure(Exception("boom"))

    assertNull(createViewModel().personName.value)
  }

  @Test
  fun `personName is null when the member profile does not exist`() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(circle.copy(people = emptyMap()))
    coEvery { profileRepository.getProfile(personId) } returns Result.success(null)

    assertNull(createViewModel().personName.value)
  }

  @Test
  fun `the name is looked up once even if the media list is updated`() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(circle.copy(people = emptyMap()))
    coEvery { profileRepository.getProfile(personId) } returns
        Result.success(
            UserProfile(uid = personId, role = Role.CAREGIVER, person = Person(firstName = "Marc"))
        )
    val media = MutableStateFlow<List<MediaItem>>(emptyList())
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()

    media.value = listOf(withPerson)
    media.value = listOf(withPerson, withPerson2)

    assertEquals("Marc", viewModel.personName.value)
    coVerify(exactly = 1) { profileRepository.getProfile(personId) }
  }

  // ---------------------------------------------------------------------------------------------
  // Playback
  // ---------------------------------------------------------------------------------------------

  private val audioFile = File("a1.m4a")

  /** The player reports [audioFile] as playing once it is asked to play it. */
  private fun playerStartsPlayingTheFile() {
    coEvery { audioPlayer.play(audioFile) } coAnswers
        {
          playerState.value =
              AudioState(file = audioFile, isPlaying = true, positionMs = 0L, durationMs = 5_000L)
        }
  }

  private fun viewModelWithAnAudio(): SeeMediasSpecificPersonViewModel {
    coEvery { mediaRepository.getFile(circle, audioWithPerson) } returns audioFile
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(audioWithPerson))
    return createViewModel()
  }

  @Test
  fun `play downloads the audio of the circle then plays it`() {
    playerStartsPlayingTheFile()
    val viewModel = viewModelWithAnAudio()

    viewModel.onPlayPause(audioWithPerson)

    coVerify(exactly = 1) { mediaRepository.getFile(circle, audioWithPerson) }
    coVerify(exactly = 1) { audioPlayer.play(audioFile) }
    assertEquals(
        AudioPlaybackState.Playing(0L, 5_000L),
        viewModel.playbackStates.value[audioWithPerson.id],
    )
  }

  @Test
  fun `a second press pauses the playing audio and a third resumes it`() {
    playerStartsPlayingTheFile()
    val viewModel = viewModelWithAnAudio()
    viewModel.onPlayPause(audioWithPerson)

    viewModel.onPlayPause(audioWithPerson)
    verify(exactly = 1) { audioPlayer.pause() }

    playerState.value = playerState.value.copy(isPlaying = false)
    viewModel.onPlayPause(audioWithPerson)
    verify(exactly = 1) { audioPlayer.resume() }
    // The file is downloaded only once
    coVerify(exactly = 1) { mediaRepository.getFile(any(), any()) }
  }

  @Test
  fun `a download failure shows an error on the audio card`() {
    coEvery { mediaRepository.getFile(circle, audioWithPerson) } throws IOException("offline")
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(audioWithPerson))
    val viewModel = createViewModel()

    viewModel.onPlayPause(audioWithPerson)

    assertEquals(
        AudioPlaybackState.Error(durationMs = audioWithPerson.durationMs),
        viewModel.playbackStates.value[audioWithPerson.id],
    )
    coVerify(exactly = 0) { audioPlayer.play(any()) }
  }

  @Test
  fun `play does nothing while the care circle is not loaded`() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    val viewModel = createViewModel()

    viewModel.onPlayPause(audioWithPerson)

    coVerify(exactly = 0) { mediaRepository.getFile(any(), any()) }
    assertTrue(viewModel.playbackStates.value.isEmpty())
  }

  @Test
  fun `restart seeks the loaded audio to the start`() {
    playerStartsPlayingTheFile()
    val viewModel = viewModelWithAnAudio()
    viewModel.onPlayPause(audioWithPerson)

    viewModel.onRestart(audioWithPerson)

    verify(exactly = 1) { audioPlayer.seekTo(0L) }
  }

  @Test
  fun `restart does nothing for an audio that was never played`() {
    val viewModel = viewModelWithAnAudio()

    viewModel.onRestart(audioWithPerson)

    verify(exactly = 0) { audioPlayer.seekTo(any()) }
  }

  @Test
  fun `the player is stopped when the playing audio leaves the list`() {
    playerStartsPlayingTheFile()
    val media = MutableStateFlow<List<MediaItem>>(listOf(audioWithPerson, withPerson))
    coEvery { mediaRepository.getFile(circle, audioWithPerson) } returns audioFile
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()
    viewModel.onPlayPause(audioWithPerson)

    // Only the picture is left: onItemsChanged receives no audio
    media.value = listOf(withPerson)

    verify(exactly = 1) { audioPlayer.stop(audioFile) }
  }

  @Test
  fun `the player keeps playing when only pictures are added to the list`() {
    playerStartsPlayingTheFile()
    val media = MutableStateFlow<List<MediaItem>>(listOf(audioWithPerson))
    coEvery { mediaRepository.getFile(circle, audioWithPerson) } returns audioFile
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns media
    val viewModel = createViewModel()
    viewModel.onPlayPause(audioWithPerson)

    media.value = listOf(audioWithPerson, withPerson)

    verify(exactly = 0) { audioPlayer.stop(any()) }
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
    assertNull(viewModel.personName.value)
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
    assertEquals("Eva", viewModel.personName.value)
  }
}
