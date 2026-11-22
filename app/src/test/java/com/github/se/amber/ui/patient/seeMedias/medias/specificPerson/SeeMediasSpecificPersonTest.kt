// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.github.se.amber.R
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardTestTags
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.media.audio.AudioPlaybackState
import com.github.se.amber.ui.media.audio.AudioPlaybackTestTags
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeeMediasSpecificPersonScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val profileRepository = mockk<ProfileRepository>()
    private val circleRepository = mockk<CareCircleRepository>()
    private val activeCircleRepository =
        mockk<ActiveCircleRepository> {
            every { activeCircleId(any(), any()) } returns flowOf("circle1")
        }

    private val profile = UserProfile(uid = "patient", role = Role.PATIENT, person = Person())
    private val circle = CareCircle(id = "circle1")

    /** Text of [id] in the current test language, with [args] for its placeholders. */
    private fun string(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    // MediaCard's ViewModel is provided without its Firebase defaults
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

    private fun contentOf(vararg items: MediaItem, circle: CareCircle = this.circle) =
        SeeGeneralMediasUIState.Content(profile, circle, items.toList())

    private fun setContent(
        state: SeeGeneralMediasUIState<MediaItem> = SeeGeneralMediasUIState.Empty(profile, circle),
        uiState: MutableStateFlow<SeeGeneralMediasUIState<MediaItem>> = MutableStateFlow(state),
        playbackStates: MutableStateFlow<Map<String, AudioPlaybackState>> =
            MutableStateFlow(emptyMap()),
        personName: MutableStateFlow<String?> = MutableStateFlow("Eva"),
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

    // ---------------------------------------------------------------------------------------------
    // Related person
    // ---------------------------------------------------------------------------------------------

    @Test
    fun clicking_a_related_person_calls_onPersonClick_with_its_id() {
        val circleWithPerson =
            CareCircle(id = "circle1", people = mapOf("p2" to Person(id = "p2", firstName = "Marc")))
        val audio = MediaItem.Audio(id = "a1", shownIds = listOf("p2"))
        coEvery { profileRepository.getProfile(any()) } returns Result.success(profile)
        coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
        coEvery { circleRepository.getCircle("circle1") } returns Result.success(circleWithPerson)
        var clickedId: String? = null
        setContent(
            state = contentOf(audio, circle = circleWithPerson),
            onPersonClick = { clickedId = it },
        )

        composeTestRule.onNodeWithTag(MediaCardTestTags.toggleButtonTextShow("a1")).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag(MediaCardTestTags.relatedItem("a1", 0))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(MediaCardTestTags.relatedItem("a1", 0)).performClick()

        assertEquals("p2", clickedId)
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
            .assertTextEquals("Il n'y a pas encore de médias avec Eva.")
    }
}