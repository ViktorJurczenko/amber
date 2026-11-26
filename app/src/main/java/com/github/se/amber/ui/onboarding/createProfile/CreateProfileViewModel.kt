// Written by Alaabrine with the aid of an AI coding agent
// Modified by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Neha Chakraborty, with assistance from Claude (Anthropic) via Claude Code.
// Modified by Yuhan YANG, with assistance from Cursor.
// Modified by Alaabrine, with assistance from Cursor.

package com.github.se.amber.ui.onboarding.createProfile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.amber.R
import com.github.se.amber.data.profile.ProfileCreationError
import com.github.se.amber.data.profile.ProfileReadError
import com.github.se.amber.data.profile.ProfileRepository
import com.github.se.amber.data.profile.ProfileRepositoryProvider
import com.github.se.amber.data.profile.ProfileUpdateError
import com.github.se.amber.model.user.Person
import com.github.se.amber.model.user.Role
import com.github.se.amber.model.validation.InputValidation
import com.github.se.amber.ui.utils.UiText
import com.github.se.amber.ui.utils.errorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the create-profile step.
 *
 * Field errors are derived from the value: a name with characters a name can't have shows its error
 * at once, while a blank required one only does once validation has been attempted, matching the
 * bootcamp B2 pattern. [nickname] is optional. [errorMsgRes] carries mapped repository failures.
 *
 * @param name first name entered by the user.
 * @param surname last name entered by the user.
 * @param nickname optional nickname entered by the user.
 * @param validationAttempted true after the user has pressed Patient or Caregiver at least once.
 * @param isSaving true while a create-profile request is in flight.
 * @param errorMsgRes actionable repository failure message, or null when there is none.
 */
data class CreateProfileUIState(
    val name: String = "",
    val surname: String = "",
    val nickname: String = "",
    val validationAttempted: Boolean = false,
    val isSaving: Boolean = false,
    @param:StringRes val errorMsgRes: Int? = null,
) {
  /** Why [name] is refused, or null; a blank or unfinished one only once validation ran. */
  val nameError: UiText?
    get() = requiredNameError(name)

  /** Why [surname] is refused, or null; a blank or unfinished one only once validation ran. */
  val surnameError: UiText?
    get() = requiredNameError(surname)

  /** Why [nickname] is refused, or null; blank is allowed, format errors show at once. */
  val nicknameError: UiText?
    get() =
        InputValidation.OPTIONAL_NAME.errorText(
            nickname,
            submitted = validationAttempted,
            formatRes = R.string.inputErrorNameFormat,
        )

  /** Whether the role buttons can be pressed: no save in progress and no error shown. */
  val canSubmit: Boolean
    get() = !isSaving && nameError == null && surnameError == null && nicknameError == null

  private fun requiredNameError(value: String): UiText? =
      InputValidation.NAME.errorText(
          value,
          submitted = validationAttempted,
          blankRes = R.string.createProfileFillOut,
          formatRes = R.string.inputErrorNameFormat,
      )
}

/**
 * ViewModel for [CreateProfileScreen]: validates name/surname/nickname and persists the profile on
 * role choice.
 *
 * On start it reads the saved profile and, when the user has not typed yet, fills the nickname. A
 * relaunch otherwise sends a blank nickname and clears the stored one.
 *
 * @param profileRepository source of profile create/read operations.
 */
class CreateProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.default.repository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(CreateProfileUIState())
  val uiState: StateFlow<CreateProfileUIState> = _uiState.asStateFlow()

  private var savedProfile: SavedOnboardingProfile? = null

  /** True after a field edit, so a saved nickname does not replace text the user cleared. */
  private var typed = false

  init {
    viewModelScope.launch { loadSavedProfile() }
  }

  /**
   * Fills [CreateProfileUIState.nickname] from the saved profile unless the user already typed. A
   * missing profile or a failed read leaves the field blank.
   */
  private suspend fun loadSavedProfile() {
    val nickname =
        profileRepository
            .getCurrentProfile()
            .getOrNull()
            ?.person
            ?.nickname
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    if (!typed) _uiState.update { it.copy(nickname = nickname) }
  }

  /**
   * Updates [CreateProfileUIState.name], cut at [InputValidation.NAME_MAX_LENGTH]; field error
   * clears automatically once the name is valid.
   */
  fun onNameChange(value: String) {
    updateNameField(name = InputValidation.NAME.clean(value))
  }

  /**
   * Updates [CreateProfileUIState.surname], cut at [InputValidation.NAME_MAX_LENGTH]; field error
   * clears automatically once the surname is valid.
   */
  fun onSurnameChange(value: String) {
    updateNameField(surname = InputValidation.NAME.clean(value))
  }

  /**
   * Updates [CreateProfileUIState.nickname], cut at [InputValidation.NAME_MAX_LENGTH]; field error
   * clears automatically once the nickname is valid or blank.
   */
  fun onNicknameChange(value: String) {
    updateNameField(nickname = InputValidation.NAME.clean(value))
  }

  /**
   * Validates name, surname and optional nickname, then saves the profile with [role].
   *
   * The first successful choice creates the profile. Pressing the same role again only navigates.
   * Pressing the other role updates the stored onboarding profile, so a wrong choice can be
   * corrected after Cancel. If this screen is recreated and the profile already exists with no care
   * circle, the selected role is written over that profile instead of failing with "already
   * exists".
   *
   * [onSuccess] runs after the profile matches [role], so the navigation owner can move forward
   * without observing UI state. Does nothing while a save is already in progress. Does not call the
   * repository when a required field is blank after trim or isn't a valid name.
   */
  fun onRoleSelected(role: Role, onSuccess: (Role) -> Unit = {}) {
    if (_uiState.value.isSaving) return

    val trimmedName = _uiState.value.name.trim()
    val trimmedSurname = _uiState.value.surname.trim()
    val trimmedNickname = _uiState.value.nickname.trim()

    _uiState.update {
      it.copy(
          name = trimmedName,
          surname = trimmedSurname,
          nickname = trimmedNickname,
          validationAttempted = true,
          errorMsgRes = null,
      )
    }

    if (
        !InputValidation.NAME.isValid(trimmedName) ||
            !InputValidation.NAME.isValid(trimmedSurname) ||
            !InputValidation.OPTIONAL_NAME.isValid(trimmedNickname)
    ) {
      return
    }

    val nicknameOrNull = trimmedNickname.takeIf { it.isNotEmpty() }
    val saved = savedProfile
    if (
        saved != null &&
            saved.name == trimmedName &&
            saved.surname == trimmedSurname &&
            saved.nickname == nicknameOrNull &&
            saved.role == role
    ) {
      onSuccess(role)
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMsgRes = null) }
      val person =
          Person(firstName = trimmedName, lastName = trimmedSurname, nickname = nicknameOrNull)
      val result =
          if (saved == null) {
            profileRepository.createProfile(person = person, role = role)
          } else {
            profileRepository.updateOnboardingProfile(person = person, role = role)
          }
      result.fold(
          onSuccess = {
            rememberAndContinue(trimmedName, trimmedSurname, nicknameOrNull, role, onSuccess)
          },
          onFailure = { error ->
            if (saved == null && error is ProfileCreationError.ProfileAlreadyExists) {
              correctExistingProfile(person, role, onSuccess)
            } else {
              showFailure(error)
            }
          },
      )
    }
  }

  private suspend fun correctExistingProfile(
      person: Person,
      role: Role,
      onSuccess: (Role) -> Unit,
  ) {
    val current =
        profileRepository.getCurrentProfile().getOrElse { error ->
          showFailure(error)
          return
        }
    val alreadyJoined = current?.circleIds?.any { it.isNotBlank() } == true
    if (current == null || alreadyJoined) {
      showFailure(ProfileCreationError.ProfileAlreadyExists)
      return
    }
    if (
        current.role == role &&
            current.person.firstName == person.firstName &&
            current.person.lastName == person.lastName &&
            current.person.nickname == person.nickname
    ) {
      rememberAndContinue(
          person.firstName,
          person.lastName,
          person.nickname,
          role,
          onSuccess,
      )
      return
    }
    profileRepository
        .updateOnboardingProfile(person = person, role = role)
        .fold(
            onSuccess = {
              rememberAndContinue(
                  person.firstName,
                  person.lastName,
                  person.nickname,
                  role,
                  onSuccess,
              )
            },
            onFailure = { showFailure(it) },
        )
  }

  private fun rememberAndContinue(
      name: String,
      surname: String,
      nickname: String?,
      role: Role,
      onSuccess: (Role) -> Unit,
  ) {
    savedProfile =
        SavedOnboardingProfile(name = name, surname = surname, nickname = nickname, role = role)
    _uiState.update { it.copy(isSaving = false, errorMsgRes = null) }
    onSuccess(role)
  }

  private fun showFailure(error: Throwable) {
    _uiState.update { it.copy(isSaving = false, errorMsgRes = error.errorMessage()) }
  }

  private fun updateNameField(
      name: String? = null,
      surname: String? = null,
      nickname: String? = null,
  ) {
    typed = true
    _uiState.update {
      it.copy(
          name = name ?: it.name,
          surname = surname ?: it.surname,
          nickname = nickname ?: it.nickname,
          errorMsgRes = null,
      )
    }
  }
}

private data class SavedOnboardingProfile(
    val name: String,
    val surname: String,
    val nickname: String?,
    val role: Role,
)

/** Maps a repository failure to the string resource of the message shown to the user. */
@StringRes
private fun Throwable.errorMessage(): Int =
    when (this) {
      is ProfileCreationError.InvalidPersonDetails,
      is ProfileUpdateError.InvalidPersonDetails -> R.string.createProfileErrorInvalidName
      is ProfileCreationError.NotAuthenticated,
      is ProfileUpdateError.NotAuthenticated,
      is ProfileReadError.NotAuthenticated -> R.string.createProfileErrorSessionExpired
      is ProfileCreationError.ProfileAlreadyExists -> R.string.createProfileErrorExists
      is ProfileUpdateError.AlreadyInCareCircle -> R.string.createProfileErrorAlreadyInCircle
      is ProfileCreationError.PermissionDenied,
      is ProfileUpdateError.PermissionDenied,
      is ProfileReadError.PermissionDenied -> R.string.createProfileErrorPermissionDenied
      is ProfileCreationError.Network,
      is ProfileUpdateError.Network,
      is ProfileReadError.Network -> R.string.createProfileErrorOffline
      else -> R.string.createProfileErrorUnknown
    }
