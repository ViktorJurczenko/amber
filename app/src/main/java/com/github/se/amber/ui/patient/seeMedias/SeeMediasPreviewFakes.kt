// Written by Yuhan YANG, with assistance from Cursor.

package com.github.se.amber.ui.patient.seeMedias

import com.github.se.amber.data.circle.ActiveCircleRepository
import com.github.se.amber.data.circle.CareCircleRepository
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.model.circle.CareCircle
import com.github.se.amber.model.media.CircleMedia
import com.github.se.amber.model.media.MediaItem
import com.github.se.amber.model.media.MediaRepository
import com.github.se.amber.model.media.OutboxEntry
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.user.UserProfile
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared preview/test fakes for patient See Medias screens. Only
 * [MediaRepository.observeApprovedMedia] is meant to return real data; other repository APIs fail
 * fast so Studio previews never touch them by accident.
 */
internal class SeeMediasPreviewMediaRepository(private val media: Flow<List<MediaItem>>) :
    MediaRepository {
  override fun observeApprovedMedia(circleId: String): Flow<List<MediaItem>> = media

  override fun newMediaId(circleId: String): String {
    error("Not used by See Medias previews")
  }

  override fun observeMediaShowing(circleId: String, shownId: String): Flow<List<MediaItem>> {
    error("Not used by See Medias previews")
  }

  override fun observeCircleMedia(circleId: String): Flow<List<CircleMedia>> {
    error("Not used by See Medias previews")
  }

  override suspend fun getMedia(circleId: String, mediaId: String): MediaItem? {
    error("Not used by See Medias previews")
  }

  override suspend fun addMedia(circle: CareCircle, item: MediaItem, file: File?) {
    error("Not used by See Medias previews")
  }

  override fun observeOutbox(circleId: String): Flow<List<OutboxEntry>> {
    error("Not used by See Medias previews")
  }

  override suspend fun retrySend(circleId: String, mediaId: String) {
    error("Not used by See Medias previews")
  }

  override suspend fun discardUnsent(circleId: String, mediaId: String) {
    error("Not used by See Medias previews")
  }

  override suspend fun updateMedia(circleId: String, item: MediaItem) {
    error("Not used by See Medias previews")
  }

  override suspend fun deleteMedia(circle: CareCircle, item: MediaItem) {
    error("Not used by See Medias previews")
  }

  override suspend fun getFile(circle: CareCircle, item: MediaItem): File {
    error("Not used by See Medias previews")
  }

  override suspend fun clearLocalMedia(circleId: String) {
    error("Not used by See Medias previews")
  }

  override suspend fun clearLocalMedia() {
    error("Not used by See Medias previews")
  }
}

internal class SeeMediasPreviewProfiles(
    private val profile: UserProfile,
    private val showError: Boolean,
) : ProfileRepository {
  override suspend fun createProfile(person: Person, role: Role): Result<Unit> {
    error("Preview does not create profiles")
  }

  override suspend fun updateOnboardingProfile(person: Person, role: Role): Result<Unit> {
    error("Preview does not update profiles")
  }

  override suspend fun getCurrentProfile(): Result<UserProfile?> =
      if (showError) Result.failure(Exception("preview")) else Result.success(profile)

  override suspend fun getProfile(uid: String): Result<UserProfile?> = Result.success(profile)
}

internal class SeeMediasPreviewCircles(private val circle: CareCircle) : CareCircleRepository {
  override fun newId(): String = circle.id

  override suspend fun getCircle(circleId: String): Result<CareCircle> = Result.success(circle)

  override suspend fun addPerson(circleId: String, person: Person): Result<Person> {
    error("Preview does not add people")
  }
}

/** Active-circle fake that always picks the first non-blank id from the given list. */
internal fun seeMediasPreviewActiveCircles(): ActiveCircleRepository =
    object : ActiveCircleRepository {
      override fun activeCircleId(uid: String, circleIds: List<String>): Flow<String?> =
          flowOf(circleIds.firstOrNull { it.isNotBlank() })

      override suspend fun setActiveCircle(uid: String, id: String) = Unit

      override suspend fun clear(uid: String) = Unit
    }
