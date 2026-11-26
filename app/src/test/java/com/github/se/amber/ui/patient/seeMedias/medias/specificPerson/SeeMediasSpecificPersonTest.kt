// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.media.image.MediaPictureFetcher
import com.github.se.amber.data.media.image.MediaPictureKeyer
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MEDIA_CARD_LOAD_TIMEOUT_MS
import com.github.se.amber.ui.media.MediaCardTestTags
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.media.UnconfinedMainDispatcherRule
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.AudioPlaybackTestTags
import com.github.se.amber.ui.media.clickRelatedPerson
import com.github.se.amber.ui.navigation.BottomNavigationMenuTestTags
import com.github.se.amber.ui.navigation.PatientTab
import com.github.se.amber.ui.navigation.Tab
import com.github.se.amber.ui.navigation.TopNavigationMenuTestTags
import com.github.se.amber.ui.patient.PatientUtilsTestTags
import com.github.se.amber.ui.patient.seeMedias.medias.SeeGeneralMediasUIState
import com.github.se.amber.ui.theme.AmberAppTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeeMediasSpecificPersonScreenTest {
  @get:Rule val mainDispatcherRule = UnconfinedMainDispatcherRule()
  @get:Rule val composeTestRule = createComposeRule()

  private val profileRepository = mockk<ProfileRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val activeCircleRepository = mockk<ActiveCircleRepository>()
  // Pictures stay loading: no real repository, file or network is used
  private val imageRepository =
      mockk<MediaRepository> { coEvery { getFile(any(), any()) } coAnswers { awaitCancellation() } }

  private val profile = UserProfile(uid = "patient", role = Role.PATIENT, person = Person())
  private val circle = CareCircle(id = "circle1")
  private val circleWithPerson =
      CareCircle(id = "circle1", people = mapOf("p1" to Person(id = "p1", firstName = "Eva")))
  private val picture = MediaItem.Picture(id = "m1", shownIds = listOf("p1"))

  // MediaCard builds its own ViewModel: give it one made with mocks
  private val mediaCardOwner =
      object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory =
            object : ViewModelProvider.Factory {
              @Suppress("UNCHECKED_CAST")
              override fun <T : ViewModel> create(modelClass: Class<T>): T =
                  MediaCardViewModel(profileRepository, circleRepository, activeCircleRepository)
                      as T
            }
      }

  @Before
  fun setUp() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    coEvery { profileRepository.getProfile(any()) } returns Result.success(profile)
    every { activeCircleRepository.activeCircleId(any(), any()) } returns flowOf("circle1")
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circleWithPerson)

    val context = ApplicationProvider.getApplicationContext<Context>()
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(context)
            .components {
              add(MediaPictureKeyer())
              add(MediaPictureFetcher.Factory { imageRepository })
            }
            .build()
    )
  }

  @After
  fun tearDown() {
    // The loader is global: do not leak it into other test classes
    SingletonImageLoader.reset()
  }

  /** Text of [id] in the current test language, with [args] for its placeholders. */
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  /** Text of [id] in English, whatever the language of the current test. */
  private fun englishString(id: Int, vararg args: Any): String {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val configuration =
        Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    return context.createConfigurationContext(configuration).getString(id, *args)
  }

  /** Text currently displayed by the node with the given [tag]. */
  private fun displayedText(tag: String): String =
      composeTestRule
          .onNodeWithTag(tag)
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text]
          .joinToString("") { it.text }

  private fun contentOf(vararg items: MediaItem, circle: CareCircle = this.circle) =
      SeeGeneralMediasUIState.Content(profile, circle, items.toList())

  private fun setContent(
      state: SeeGeneralMediasUIState<MediaItem> = SeeGeneralMediasUIState.Empty(profile, circle),
      uiState: MutableStateFlow<SeeGeneralMediasUIState<MediaItem>> = MutableStateFlow(state),
      playbackStates: MutableStateFlow<Map<String, AudioPlaybackState>> =
          MutableStateFlow(emptyMap()),
      personName: MutableStateFlow<String?> = MutableStateFlow("Eva"),
      recomposeTrigger: MutableIntState = mutableIntStateOf(0),
      onBackClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
      onPersonClick: (String) -> Unit = {},
  ): SeeMediasSpecificPersonViewModel {
    val viewModel = mockk<SeeMediasSpecificPersonViewModel>(relaxed = true)
    every { viewModel.uiState } returns uiState
    every { viewModel.playbackStates } returns playbackStates
    every { viewModel.personName } returns personName
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides mediaCardOwner) {
        AmberAppTheme(dynamicColor = false) {
          // Read here: bumping the trigger recomposes this call with unchanged parameters,
          // so the screen itself is skipped
          recomposeTrigger.intValue
          SeeMediasSpecificPersonScreen(
              personId = "person1",
              viewModel = viewModel,
              onBackClick = onBackClick,
              onTabSelected = onTabSelected,
              onPersonClick = onPersonClick,
          )
        }
      }
    }
    return viewModel
  }

  // ---------------------------------------------------------------------------------------------
  // Static parts and title
  // ---------------------------------------------------------------------------------------------

  @Test
  fun title_and_navigation_menus_are_displayed() {
    setContent()

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.TOP_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.BOTTOM_NAVIGATION_MENU)
        .assertIsDisplayed()
  }

  @Test
  fun title_contains_the_name_of_the_person() {
    setContent(personName = MutableStateFlow("Eva"))

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonTitle, "Eva"))
  }

  @Test
  fun title_uses_the_unknown_name_until_the_name_is_known() {
    val personName = MutableStateFlow<String?>(null)
    setContent(personName = personName)

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE)
        .assertTextEquals(
            string(
                R.string.seeMediasSpecificPersonTitle,
                string(R.string.seeMediasSpecificPersonUnknownName),
            )
        )

    composeTestRule.runOnIdle { personName.value = "Eva" }

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonTitle, "Eva"))
  }

  @Test
  fun back_button_calls_onBackClick() {
    var clicked = false
    setContent(onBackClick = { clicked = true })

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()

    assertTrue(clicked)
  }

  @Test
  fun bottom_tab_calls_onTabSelected() {
    var selected: Tab? = null
    setContent(onTabSelected = { selected = it })

    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()

    assertEquals(PatientTab.Home, selected)
  }

  // ---------------------------------------------------------------------------------------------
  // Loading / Empty / Blocking error
  // ---------------------------------------------------------------------------------------------

  @Test
  fun loading_shows_the_loader_and_nothing_else() {
    setContent(state = SeeGeneralMediasUIState.Loading)

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertDoesNotExist()
  }

  @Test
  fun empty_shows_the_empty_message() {
    setContent()

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeMediasSpecificPersonEmpty, "Eva"))
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun preview_shows_the_empty_screen() {
    composeTestRule.setContent { SeeMediasSpecificPersonPreview() }

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonTitle, "Eva"))
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertIsDisplayed()
  }

  @Test
  fun blocking_error_shows_the_message_and_retry_calls_the_view_model() {
    val viewModel =
        setContent(
            state =
                SeeGeneralMediasUIState.BlockingError(
                    R.string.seeMediasSpecificPersonErrorLoadMedia
                )
        )

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonErrorLoadMedia))
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertDoesNotExist()

    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).performClick()

    verify(exactly = 1) { viewModel.retry() }
  }

  // ---------------------------------------------------------------------------------------------
  // Content
  // ---------------------------------------------------------------------------------------------

  @Test
  fun content_shows_pictures_and_audios_with_their_cards() {
    setContent(
        state =
            contentOf(
                MediaItem.Picture(id = "p1", width = 400, height = 300),
                MediaItem.Audio(id = "a1"),
            )
    )

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("p1")).assertExists()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.mediaCard("p1")).assertExists()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("a1")).assertExists()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.mediaCard("a1")).assertExists()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.LOADING).assertDoesNotExist()
  }

  @Test
  fun content_shows_a_card_for_each_media_and_hides_the_other_states() {
    setContent(
        state =
            SeeGeneralMediasUIState.Content(profile, circleWithPerson, listOf<MediaItem>(picture))
    )

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.mediaCard("m1"))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun a_text_media_is_not_displayed_yet_and_does_not_crash_the_screen() {
    setContent(state = contentOf(MediaItem.Text(id = "t1"), MediaItem.Audio(id = "a1")))

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("t1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("a1")).assertExists()
  }

  @Test
  fun content_follows_the_state() {
    val uiState =
        MutableStateFlow<SeeGeneralMediasUIState<MediaItem>>(
            contentOf(MediaItem.Audio(id = "a1"), MediaItem.Audio(id = "a2"))
        )
    setContent(uiState = uiState)
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("a1")).assertExists()

    composeTestRule.runOnIdle { uiState.value = contentOf(MediaItem.Audio(id = "a2")) }
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("a1")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("a2")).assertExists()

    composeTestRule.runOnIdle { uiState.value = SeeGeneralMediasUIState.Empty(profile, circle) }
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.media("a2")).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // Audio playback
  // ---------------------------------------------------------------------------------------------

  @Test
  fun play_and_restart_call_the_view_model_with_the_audio() {
    val audio = MediaItem.Audio(id = "a1")
    val viewModel = setContent(state = contentOf(audio))

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()

    verify(exactly = 1) { viewModel.onPlayPause(audio) }
    verify(exactly = 1) { viewModel.onRestart(audio) }
  }

  @Test
  fun playback_state_updates_the_card_and_retry_calls_the_view_model() {
    val audio = MediaItem.Audio(id = "a1")
    val playbackStates =
        MutableStateFlow<Map<String, AudioPlaybackState>>(
            mapOf(audio.id to AudioPlaybackState.Playing(2_000L, 5_000L))
        )
    val viewModel = setContent(state = contentOf(audio), playbackStates = playbackStates)

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:02 / 0:05")

    composeTestRule.runOnIdle {
      playbackStates.value = mapOf(audio.id to AudioPlaybackState.Error())
    }
    composeTestRule
        .onNodeWithTag(AudioPlaybackTestTags.ERROR)
        .assertTextEquals(string(R.string.audioPlaybackError))

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).performClick()
    verify(exactly = 1) { viewModel.onPlayPause(audio) }
  }

  @Test
  fun audio_without_playback_state_shows_its_own_duration() {
    setContent(state = contentOf(MediaItem.Audio(id = "a1", durationMs = 65_000L)))

    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.TIME).assertTextEquals("0:00 / 1:05")
  }

  @Test
  fun audio_callbacks_follow_recompositions_and_item_changes() {
    val first = MediaItem.Audio(id = "a1", durationMs = 1_000L)
    val updated = MediaItem.Audio(id = "a1", durationMs = 2_000L)
    val uiState = MutableStateFlow<SeeGeneralMediasUIState<MediaItem>>(contentOf(first))
    val playbackStates = MutableStateFlow<Map<String, AudioPlaybackState>>(emptyMap())
    val trigger = mutableIntStateOf(0)
    val viewModel =
        setContent(uiState = uiState, playbackStates = playbackStates, recomposeTrigger = trigger)

    // Recomposition with the same item: the remembered lambdas are reused
    composeTestRule.runOnIdle {
      playbackStates.value = mapOf("a1" to AudioPlaybackState.Playing(1_000L, 5_000L))
    }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    verify(exactly = 1) { viewModel.onPlayPause(first) }

    // The item changes (same id, other data): the lambdas must capture the new one
    composeTestRule.runOnIdle { uiState.value = contentOf(updated) }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.PLAY_PAUSE).performClick()
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RESTART).performClick()
    verify(exactly = 1) { viewModel.onPlayPause(updated) }
    verify(exactly = 1) { viewModel.onRestart(updated) }
    verify(exactly = 0) { viewModel.onRestart(first) }

    // Retry after an error also uses the new item
    composeTestRule.runOnIdle { playbackStates.value = mapOf("a1" to AudioPlaybackState.Error()) }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).performClick()
    verify(exactly = 2) { viewModel.onPlayPause(updated) }

    // Same parameters again: the screen is skipped and everything still works
    composeTestRule.runOnIdle { trigger.intValue++ }
    composeTestRule.onNodeWithTag(AudioPlaybackTestTags.RETRY).performClick()
    verify(exactly = 3) { viewModel.onPlayPause(updated) }
  }

  // ---------------------------------------------------------------------------------------------
  // Related person
  // ---------------------------------------------------------------------------------------------

  @Test
  fun clicking_a_related_person_on_an_audio_calls_onPersonClick_with_its_id() {
    val circleWithMarc =
        CareCircle(id = "circle1", people = mapOf("p2" to Person(id = "p2", firstName = "Marc")))
    val audio = MediaItem.Audio(id = "a1", shownIds = listOf("p2"))
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(circleWithMarc)
    var clickedId: String? = null
    setContent(
        state = contentOf(audio, circle = circleWithMarc),
        onPersonClick = { clickedId = it },
    )

    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("a1")).performClick()
    composeTestRule.waitUntil(timeoutMillis = MEDIA_CARD_LOAD_TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(MediaCardTestTags.relatedItem("a1", 0))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(MediaCardTestTags.relatedItem("a1", 0)).performClick()

    assertEquals("p2", clickedId)
  }

  // The related person is below the visible area of the test screen (the picture is tall):
  // trigger its click action directly, as a real tap would miss it.
  @Test
  fun clicking_a_related_person_calls_onPersonClick_with_its_id() {
    var clickedId: String? = null
    setContent(
        state =
            SeeGeneralMediasUIState.Content(profile, circleWithPerson, listOf<MediaItem>(picture)),
        onPersonClick = { clickedId = it },
    )

    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1")).performClick()
    composeTestRule.waitUntil(timeoutMillis = MEDIA_CARD_LOAD_TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(MediaCardTestTags.relatedItem("m1", 0))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(MediaCardTestTags.relatedItem("m1", 0))
        .performSemanticsAction(SemanticsActions.OnClick)

    assertEquals("p1", clickedId)
  }

  @Test
  fun person_click_ignores_the_shown_person_and_follows_the_callback() {
    val twoPeople =
        CareCircle(
            id = "circle1",
            people =
                mapOf(
                    "person1" to Person(id = "person1", firstName = "Eva"),
                    "p2" to Person(id = "p2", firstName = "Marc"),
                ),
        )
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(twoPeople)
    val media = MediaItem.Picture(id = "m1", shownIds = listOf("person1", "p2"))
    val playbackStates = MutableStateFlow<Map<String, AudioPlaybackState>>(emptyMap())
    val viewModel = mockk<SeeMediasSpecificPersonViewModel>(relaxed = true)
    every { viewModel.uiState } returns
        MutableStateFlow(
            SeeGeneralMediasUIState.Content(profile, twoPeople, listOf<MediaItem>(media))
        )
    every { viewModel.playbackStates } returns playbackStates
    every { viewModel.personName } returns MutableStateFlow<String?>("Eva")

    val first = mutableListOf<String>()
    val second = mutableListOf<String>()
    var callback by mutableStateOf<(String) -> Unit>({ first += it })

    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides mediaCardOwner) {
        AmberAppTheme(dynamicColor = false) {
          SeeMediasSpecificPersonScreen(
              personId = "person1",
              viewModel = viewModel,
              onPersonClick = callback,
          )
        }
      }
    }

    composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("m1")).performClick()
    composeTestRule.waitUntil(timeoutMillis = MEDIA_CARD_LOAD_TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(MediaCardTestTags.relatedItem("m1", 1))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    // Items are not visible on the test screen: trigger the click action directly
    fun click(index: Int) =
        composeTestRule
            .onNodeWithTag(MediaCardTestTags.relatedItem("m1", index))
            .performSemanticsAction(SemanticsActions.OnClick)

    click(0) // person1: the person already shown, ignored
    click(1) // p2
    // Recomposition with the same callback: remembered lambda reused
    composeTestRule.runOnIdle { playbackStates.value = mapOf("x" to AudioPlaybackState.Error()) }
    click(1)
    assertEquals(listOf("p2", "p2"), first)

    // New callback: the lambda is recreated
    composeTestRule.runOnIdle { callback = { second += it } }
    click(1)
    assertEquals(listOf("p2"), second)
    assertEquals(2, first.size)
  }

  // ---------------------------------------------------------------------------------------------
  // Non-blocking errors (Toast) and language
  // ---------------------------------------------------------------------------------------------

  @Test
  fun media_error_is_shown_in_a_toast_without_hiding_the_screen() {
    val viewModel =
        setContent(
            state =
                SeeGeneralMediasUIState.Empty(
                    profile,
                    circle,
                    errorMsgRes = R.string.seeMediasSpecificPersonErrorLoadMedia,
                )
        )

    composeTestRule.waitForIdle()

    assertEquals(
        string(R.string.seeMediasSpecificPersonErrorLoadMedia),
        ShadowToast.getTextOfLatestToast(),
    )
    verify(exactly = 1) { viewModel.clearErrorMsg() }
    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "fr")
  fun screen_is_shown_in_french() {
    setContent()

    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonTitle, "Eva"))
    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonEmpty, "Eva"))
    assertNotEquals(
        englishString(R.string.seeMediasSpecificPersonTitle, "Eva"),
        displayedText(SeeMediasSpecificPersonTestTags.TITLE),
    )
    assertNotEquals(
        englishString(R.string.seeMediasSpecificPersonEmpty, "Eva"),
        displayedText(SeeMediasSpecificPersonTestTags.NO_MEDIAS),
    )
  }

  // ---------------------------------------------------------------------------------------------
  // Default parameters
  // ---------------------------------------------------------------------------------------------

  @Test
  fun default_callbacks_do_nothing_and_keep_the_screen_displayed() {
    val viewModel = mockk<SeeMediasSpecificPersonViewModel>(relaxed = true)
    every { viewModel.uiState } returns
        MutableStateFlow(
            SeeGeneralMediasUIState.Content(profile, circleWithPerson, listOf<MediaItem>(picture))
        )
    every { viewModel.playbackStates } returns MutableStateFlow(emptyMap())
    every { viewModel.personName } returns MutableStateFlow<String?>("Eva")
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides mediaCardOwner) {
        AmberAppTheme(dynamicColor = false) { SeeMediasSpecificPersonScreen(viewModel = viewModel) }
      }
    }

    composeTestRule.clickRelatedPerson("m1")
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun default_view_model_is_the_one_stored_under_the_person_id() {
    val viewModel = mockk<SeeMediasSpecificPersonViewModel>(relaxed = true)
    every { viewModel.uiState } returns
        MutableStateFlow(SeeGeneralMediasUIState.Empty(profile, circle))
    every { viewModel.playbackStates } returns MutableStateFlow(emptyMap())
    every { viewModel.personName } returns MutableStateFlow<String?>("Eva")

    val owner =
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
    // Pre-fill the store under the key the screen uses ("person1")
    ViewModelProvider(
        owner.viewModelStore,
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
        },
    )["person1", SeeMediasSpecificPersonViewModel::class.java]

    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        AmberAppTheme(dynamicColor = false) { SeeMediasSpecificPersonScreen(personId = "person1") }
      }
    }

    composeTestRule.onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS).assertIsDisplayed()
  }
}
