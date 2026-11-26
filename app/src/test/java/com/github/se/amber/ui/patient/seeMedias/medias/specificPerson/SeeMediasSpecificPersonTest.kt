// Written by camomille299, with assistance from Claude (Anthropic) via claude.ai.

package com.github.se.amber.ui.patient.seeMedias.medias.specificPerson

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.media.UnconfinedMainDispatcherRule
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
  @get:Rule val mainDispatcherRule = UnconfinedMainDispatcherRule()
  @get:Rule val composeTestRule = createComposeRule()

  private val profile = UserProfile(uid = "patient", role = Role.PATIENT, person = Person())
  private val circle = CareCircle(id = "circle1")

  private val profileRepository = mockk<ProfileRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val activeCircleRepository = mockk<ActiveCircleRepository>()

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

  private fun setContent(
      state: SeeGeneralMediasUIState<MediaItem> = SeeGeneralMediasUIState.Empty(profile, circle),
      onBackClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
      onPersonClick: (String) -> Unit = {},
  ): SeeMediasSpecificPersonViewModel {
    val viewModel = mockk<SeeMediasSpecificPersonViewModel>(relaxed = true)
    every { viewModel.uiState } returns MutableStateFlow(state)
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
        .assertTextEquals(string(R.string.seeMediasSpecificPersonEmpty))
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
        .assertTextEquals(string(R.string.seeMediasSpecificPersonTitle))
    composeTestRule
        .onNodeWithTag(SeeMediasSpecificPersonTestTags.NO_MEDIAS)
        .assertTextEquals(string(R.string.seeMediasSpecificPersonEmpty))
    // Would fail if the "fr" qualifier were ignored and the screen fell back to English
    assertNotEquals(
        englishString(R.string.seeMediasSpecificPersonTitle),
        displayedText(SeeMediasSpecificPersonTestTags.TITLE),
    )
    assertNotEquals(
        englishString(R.string.seeMediasSpecificPersonEmpty),
        displayedText(SeeMediasSpecificPersonTestTags.NO_MEDIAS),
    )
  }

  // ---------------------------------------------------------------------------------------------
  // Content
  // ---------------------------------------------------------------------------------------------

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
  fun clicking_a_related_person_calls_onPersonClick_with_its_id() {
    var clickedId: String? = null
    setContent(
        state =
            SeeGeneralMediasUIState.Content(profile, circleWithPerson, listOf<MediaItem>(picture)),
        onPersonClick = { clickedId = it },
    )

    composeTestRule.clickRelatedPerson("m1")

    assertEquals("p1", clickedId)
  }

  @Test
  fun clicking_the_person_already_shown_does_not_call_onPersonClick() {
    val shownPerson =
        CareCircle(
            id = "circle1",
            people = mapOf("person1" to Person(id = "person1", firstName = "Eva")),
        )
    coEvery { circleRepository.getCircle("circle1") } returns Result.success(shownPerson)
    val media = MediaItem.Picture(id = "m2", shownIds = listOf("person1"))
    var clickedId: String? = null
    setContent(
        state = SeeGeneralMediasUIState.Content(profile, shownPerson, listOf<MediaItem>(media)),
        onPersonClick = { clickedId = it },
    )

    composeTestRule.clickRelatedPerson("m2")

    assertNull(clickedId)
  }

  @Test
  fun default_callbacks_do_nothing_and_keep_the_screen_displayed() {
    val viewModel = mockk<SeeMediasSpecificPersonViewModel>(relaxed = true)
    every { viewModel.uiState } returns
        MutableStateFlow(
            SeeGeneralMediasUIState.Content(profile, circleWithPerson, listOf<MediaItem>(picture))
        )
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
