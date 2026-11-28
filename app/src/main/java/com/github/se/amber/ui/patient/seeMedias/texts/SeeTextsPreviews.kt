// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias.texts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.patient.seeMedias.SeeMediasPreviewCircles
import com.github.se.amber.ui.patient.seeMedias.SeeMediasPreviewMediaRepository
import com.github.se.amber.ui.patient.seeMedias.SeeMediasPreviewProfiles
import com.github.se.amber.ui.patient.seeMedias.seeMediasPreviewActiveCircles
import com.github.se.amber.ui.theme.AmberAppTheme
import java.time.Month
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal enum class PreviewTextState {
  Loading,
  Empty,
  Error,
  Content,
}

internal class SeeTextsPreviewOwner(state: PreviewTextState) :
    ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
  override val viewModelStore = ViewModelStore()
  private val profile =
      UserProfile(
          uid = "preview",
          role = Role.PATIENT,
          person = Person(firstName = "Circee"),
          circleIds = listOf("preview"),
      )
  private val circle =
      CareCircle(id = "preview", people = mapOf("relative" to Person(firstName = "Evangeline")))

  private val profiles = SeeMediasPreviewProfiles(profile, state == PreviewTextState.Error)
  private val circles = SeeMediasPreviewCircles(circle)
  internal val activeCircles: ActiveCircleRepository = seeMediasPreviewActiveCircles()

  private val longBody =
      List(8) { "This story is long enough for Show more to appear in the preview." }
          .joinToString("\n")

  private val media =
      SeeMediasPreviewMediaRepository(
          when (state) {
            PreviewTextState.Loading -> flow { awaitCancellation() }
            PreviewTextState.Content ->
                flowOf(
                    listOf(
                        MediaItem.Text(
                            id = "sample1",
                            authorId = profile.uid,
                            title = "Market day",
                            text = "We bought fruit and flowers together.",
                            description = "A sunny morning at the market.",
                            date = MemoryDate(year = 2020, month = Month.JUNE, day = 12),
                            location = GeoLocation(placeName = "Lausanne"),
                            shownIds = listOf("relative"),
                        ),
                        MediaItem.Text(
                            id = "sample2",
                            authorId = profile.uid,
                            title = "A long story",
                            text = longBody,
                            description = "Notes from that afternoon.",
                            date = MemoryDate(year = 2018),
                            shownIds = listOf("relative"),
                        ),
                    )
                )
            PreviewTextState.Empty,
            PreviewTextState.Error -> flowOf(emptyList())
          }
      )

  private val textsViewModel = SeeTextsViewModel(media, circles, profiles)

  override val defaultViewModelProviderFactory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            when (modelClass) {
              SeeTextsViewModel::class.java -> textsViewModel as T
              MediaCardViewModel::class.java ->
                  MediaCardViewModel(profiles, circles, activeCircles) as T
              else -> error("Unexpected ViewModel: $modelClass")
            }
      }
}

@Composable
private fun PreviewSeeTexts(state: PreviewTextState = PreviewTextState.Empty) {
  val owner = remember(state) { SeeTextsPreviewOwner(state) }
  CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
    AmberAppTheme { SeeTextsScreen() }
  }
}

/** Preview of [SeeTextsScreen] with no text stories. */
@Preview(showBackground = true, name = "Empty texts")
@Composable
fun SeeTextsScreenPreview() {
  PreviewSeeTexts(PreviewTextState.Empty)
}

/** Preview of [SeeTextsScreen] while approved texts are loading. */
@Preview(showBackground = true, name = "Loading texts")
@Composable
fun SeeTextsLoadingPreview() {
  PreviewSeeTexts(PreviewTextState.Loading)
}

/** Preview of [SeeTextsScreen] when loading the profile fails. */
@Preview(showBackground = true, name = "Text error")
@Composable
fun SeeTextsErrorPreview() {
  PreviewSeeTexts(PreviewTextState.Error)
}

/** Preview of [SeeTextsScreen] with short and long sample stories. */
@Preview(showBackground = true, name = "Texts")
@Composable
fun SeeTextsContentPreview() {
  PreviewSeeTexts(PreviewTextState.Content)
}
