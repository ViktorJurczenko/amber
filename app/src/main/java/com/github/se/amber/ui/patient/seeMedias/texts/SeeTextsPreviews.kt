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
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.location.GeoLocation
import com.github.se.amber.model.media.CircleMedia
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.MemoryDate
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import com.github.se.amber.ui.media.MediaCardViewModel
import com.github.se.amber.ui.theme.AmberAppTheme
import java.io.File
import java.time.Month
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal enum class PreviewTextState {
  Loading,
  Empty,
  Error,
  Content,
}

/** Only the approved-media observation is used by this preview. */
internal class PreviewMediaRepository(private val media: Flow<List<MediaItem>>) : MediaRepository {
  override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> = media

  override fun newMediaId(circleId: String): String {
    error("Not used by SeeTexts previews")
  }

  override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> {
    error("Not used by SeeTexts previews")
  }

  override fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>> {
    error("Not used by SeeTexts previews")
  }

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? {
    error("Not used by SeeTexts previews")
  }

  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?) {
    error("Not used by SeeTexts previews")
  }

  override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> {
    error("Not used by SeeTexts previews")
  }

  override suspend fun retrySend(circleId: String, mediaId: String) {
    error("Not used by SeeTexts previews")
  }

  override suspend fun discardUnsent(circleId: String, mediaId: String) {
    error("Not used by SeeTexts previews")
  }

  override suspend fun updateMedia(circleId: String, item: MediaItem) {
    error("Not used by SeeTexts previews")
  }

  override suspend fun deleteMedia(circle: CareCircle, item: MediaItem) {
    error("Not used by SeeTexts previews")
  }

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
    error("Not used by SeeTexts previews")
  }

  override suspend fun clearLocalMedia(circleId: String) {
    error("Not used by SeeTexts previews")
  }

  override suspend fun clearLocalMedia() {
    error("Not used by SeeTexts previews")
  }
}

internal class PreviewProfiles(
    private val profile: UserProfile,
    private val showError: Boolean,
) : ProfileRepository {
  override suspend fun createProfile(person: Person, role: Role): Result<Unit> =
      error("Preview does not create profiles")

  override suspend fun updateOnboardingProfile(person: Person, role: Role): Result<Unit> =
      error("Preview does not update profiles")

  override suspend fun getCurrentProfile(): Result<UserProfile?> =
      if (showError) Result.failure(Exception("preview")) else Result.success(profile)

  override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(profile)
}

internal class PreviewCircles(private val circle: CareCircle) : CareCircleRepository {
  override fun newId(): String = circle.id

  override suspend fun getCircle(circleId: String): Result<CareCircle> = Result.success(circle)

  override suspend fun addPerson(circleId: String, person: Person): Result<Person> =
      error("Preview does not add people")
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

  private val profiles = PreviewProfiles(profile, state == PreviewTextState.Error)
  private val circles = PreviewCircles(circle)
  internal val activeCircles =
      object : ActiveCircleRepository {
        override fun activeCircleId(uid: String, circleIds: List<String>): Flow<String?> =
            flowOf(circleIds.firstOrNull { it.isNotBlank() })

        override suspend fun setActiveCircle(uid: String, id: String) = Unit

        override suspend fun clear(uid: String) = Unit
      }

  private val longBody =
      List(8) { "This story is long enough for Show more to appear in the preview." }
          .joinToString("\n")

  private val media =
      PreviewMediaRepository(
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
