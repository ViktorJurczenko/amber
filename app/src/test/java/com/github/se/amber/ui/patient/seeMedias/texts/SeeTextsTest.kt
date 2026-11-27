// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias.texts

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardTestTags
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
import java.time.Month
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeeTextsScreenTest {
  private fun string(id: Int, vararg args: Any): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

  @get:Rule val mainDispatcherRule = UnconfinedMainDispatcherRule()
  @get:Rule val composeTestRule = createComposeRule()

  private val mediaRepository = mockk<MediaRepository>()
  private val circleRepository = mockk<CareCircleRepository>()
  private val activeCircleRepository = mockk<ActiveCircleRepository>()
  private val profileRepository = mockk<ProfileRepository>()

  private val profile =
      UserProfile(
          uid = "patient",
          role = Role.PATIENT,
          person = Person(),
          circleIds = listOf("circle1"),
      )

  private val longBody =
      List(12) { "This is a long line of the story so that Show more appears on screen." }
          .joinToString("\n")

  private val mediaCardOwner =
      object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory =
            object : ViewModelProvider.Factory {
              @Suppress("UNCHECKED_CAST")
              override fun <T : ViewModel> create(modelClass: Class<T>): T =
                  MediaCardViewModel(
                      profileRepository,
                      circleRepository,
                      activeCircleRepository,
                  )
                      as T
            }
      }

  /** Owner that can build both [SeeTextsViewModel] and [MediaCardViewModel] for default args. */
  private val defaultParamsOwner =
      object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory =
            object : ViewModelProvider.Factory {
              @Suppress("UNCHECKED_CAST")
              override fun <T : ViewModel> create(modelClass: Class<T>): T =
                  when {
                    modelClass.isAssignableFrom(SeeTextsViewModel::class.java) ->
                        SeeTextsViewModel(
                            mediaRepository,
                            circleRepository,
                            profileRepository,
                        )
                            as T
                    modelClass.isAssignableFrom(MediaCardViewModel::class.java) ->
                        MediaCardViewModel(
                            profileRepository,
                            circleRepository,
                            activeCircleRepository,
                        )
                            as T
                    else -> error("Unexpected ViewModel: ${modelClass.name}")
                  }
            }
      }

  @Before
  fun setUp() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.success(profile)
    every { activeCircleRepository.activeCircleId(any(), any()) } returns flowOf("circle1")
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(CareCircle(id = "circle1"))
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flowOf(emptyList())
    coEvery { profileRepository.getProfile(any()) } returns Result.success(profile)
  }

  private fun setContent(
      onBackClick: () -> Unit = {},
      onTabSelected: (Tab) -> Unit = {},
      onPersonClick: (String) -> Unit = {},
  ): SeeTextsViewModel {
    val viewModel = SeeTextsViewModel(mediaRepository, circleRepository, profileRepository)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides mediaCardOwner) {
        AmberAppTheme(dynamicColor = false) {
          SeeTextsScreen(
              textViewModel = viewModel,
              onBackClick = onBackClick,
              onTabSelected = onTabSelected,
              onPersonClick = onPersonClick,
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    return viewModel
  }

  @Test
  fun loadingState_hidesEmptyAndError() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns flow { awaitCancellation() }
    val viewModel = setContent()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Loading)
    composeTestRule.onNodeWithTag(SeeTextsTestTags.ERROR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.NO_TEXTS).assertDoesNotExist()
  }

  @Test
  fun emptyState_showsThereAreNoStoriesYet() {
    setContent()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.NO_TEXTS).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SeeTextsTestTags.NO_TEXTS)
        .assertTextEquals(string(R.string.seeTextsEmpty))
  }

  @Test
  fun blockingError_showsRetry() {
    coEvery { profileRepository.getCurrentProfile() } returns Result.failure(Exception("boom"))
    setContent()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PatientUtilsTestTags.BUTTON).assertIsDisplayed()
  }

  @Test
  fun contentState_showsTitleBodyDatePlaceAndCard() {
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(
            listOf(
                MediaItem.Text(
                    id = "1",
                    title = "Market day",
                    text = "We bought fruit.",
                    date = MemoryDate(year = 2020, month = Month.FEBRUARY, day = 1),
                    location = GeoLocation(lat = 46.5, lng = 6.6, placeName = "Lausanne"),
                    shownIds = listOf("p1"),
                ),
            ),
        )
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(
            CareCircle(
                id = "circle1",
                people = mapOf("p1" to Person(id = "p1", firstName = "Eva")),
            ),
        )
    val viewModel = setContent()
    assertTrue(viewModel.uiState.value is SeeGeneralMediasUIState.Content)
    composeTestRule.onNodeWithTag(SeeTextsTestTags.storyTitle("1")).assertTextEquals("Market day")
    composeTestRule
        .onNodeWithTag(SeeTextsTestTags.storyBody("1"))
        .assertTextEquals("We bought fruit.")
    composeTestRule.onNodeWithTag(SeeTextsTestTags.textCard("1")).assertExists()
    composeTestRule.onNodeWithTag(MediaCardTestTags.date("1")).assertExists()
    composeTestRule.onNodeWithTag(MediaCardTestTags.location("1")).assertExists()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.NO_TEXTS).assertDoesNotExist()
  }

  @Test
  fun showMoreAndShowLess_toggleTheLongStoryBody() {
    composeTestRule.setContent {
      AmberAppTheme(dynamicColor = false) {
        Box(Modifier.width(280.dp)) {
          DisplayTextStory(
              text = MediaItem.Text(id = "long", title = "Long story", text = longBody),
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(SeeTextsTestTags.storyTitle("long")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.showMore("long")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.showLess("long")).assertDoesNotExist()

    composeTestRule.onNodeWithTag(SeeTextsTestTags.showMore("long")).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.showLess("long")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.showMore("long")).assertDoesNotExist()

    composeTestRule.onNodeWithTag(SeeTextsTestTags.showLess("long")).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.showMore("long")).assertIsDisplayed()
  }

  @Test
  fun navigationCallbacks_andRelatedPerson_work() {
    coEvery { circleRepository.getCircle("circle1") } returns
        Result.success(
            CareCircle(
                id = "circle1",
                people = mapOf("p1" to Person(id = "p1", firstName = "Eva")),
            ),
        )
    coEvery { mediaRepository.observeApprovedMedia("circle1") } returns
        flowOf(listOf(MediaItem.Text(id = "1", text = "Hi", shownIds = listOf("p1"))))

    var back = false
    var selected: Tab? = null
    var personId: String? = null
    setContent(
        onBackClick = { back = true },
        onTabSelected = { selected = it },
        onPersonClick = { personId = it },
    )

    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    assertTrue(back)
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()
    assertEquals(PatientTab.Home, selected)
    composeTestRule.clickRelatedPerson("1")
    assertEquals("p1", personId)
  }

  @Test
  @Config(qualifiers = "fr")
  fun screenIsShownInFrench() {
    setContent()
    composeTestRule
        .onNodeWithTag(SeeTextsTestTags.TITLE)
        .assertTextEquals(string(R.string.seeTextsTitle))
    composeTestRule
        .onNodeWithTag(SeeTextsTestTags.NO_TEXTS)
        .assertTextEquals(string(R.string.seeTextsEmpty))
  }

  @Test
  fun screen_withDefaultParameters_showsEmptyState() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalViewModelStoreOwner provides defaultParamsOwner) {
        AmberAppTheme(dynamicColor = false) { SeeTextsScreen() }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.NO_TEXTS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TopNavigationMenuTestTags.BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(BottomNavigationMenuTestTags.tabTag(PatientTab.Home))
        .performClick()
  }

  @Test
  fun preview_displays_the_empty_message() {
    composeTestRule.setContent { SeeTextsScreenPreview() }
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(SeeTextsTestTags.NO_TEXTS)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.seeTextsEmpty))
  }

  @Test
  fun loading_error_and_content_previews_show_their_states() {
    var preview by mutableIntStateOf(0)
    composeTestRule.setContent {
      when (preview) {
        0 -> SeeTextsLoadingPreview()
        1 -> SeeTextsErrorPreview()
        else -> SeeTextsContentPreview()
      }
    }

    composeTestRule.onNodeWithTag(SeeTextsTestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.textCard("sample1")).assertDoesNotExist()

    composeTestRule.runOnIdle { preview = 1 }
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(SeeTextsTestTags.ERROR)
        .assertTextEquals(string(R.string.seeTextsErrorLoadProfile))
    composeTestRule.onNodeWithTag(SeeTextsTestTags.LOADING).assertDoesNotExist()

    composeTestRule.runOnIdle { preview = 2 }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.storyTitle("sample1")).assertExists()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.storyTitle("sample2")).assertExists()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.textCard("sample1")).assertExists()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.textCard("sample2")).assertExists()
    composeTestRule.onNodeWithTag(SeeTextsTestTags.ERROR).assertDoesNotExist()
  }
}
