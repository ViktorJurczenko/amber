// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.amber.R
import com.github.se.amber.ui.theme.AmberAppTheme
import com.github.se.amber.ui.theme.BORDER_THICKNESS
import com.github.se.amber.ui.theme.CALENDAR_CELL_ASPECT_RATIO
import com.github.se.amber.ui.theme.CALENDAR_CIRCLE_THICKNESS
import com.github.se.amber.ui.theme.CALENDAR_WIDTH_FRACTION
import com.github.se.amber.ui.theme.CAREGIVER_PADDING_SMALL
import com.github.se.amber.ui.theme.PADDING_MINI
import com.github.se.amber.ui.theme.ROUND_CORNER
import com.github.se.amber.ui.theme.WEIGHT_ONE
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

internal object CalendarTestTags {
  const val CALENDAR = "calendar"
  const val MONTH_TITLE = "calendar_month_title"
  const val PREVIOUS_MONTH_BUTTON = "calendar_previous_month_button"
  const val NEXT_MONTH_BUTTON = "calendar_next_month_button"
  const val PREVIOUS_MONTH_ICON = "calendar_previous_month_icon"
  const val NEXT_MONTH_ICON = "calendar_next_month_icon"

  fun weekday(index: Int) = "calendar_weekday_$index"

  fun day(day: Int) = "calendar_day_$day"

  fun appointmentCircle(day: Int) = "calendar_appointment_circle_$day"
}

/** Pattern of the title: the full month name, then the year ("November 2026" / "novembre 2026"). */
private const val MONTH_TITLE_PATTERN = "LLLL yyyy"

/**
 * Displays a calendar in a box: the month and the arrows to change it at the top, the days of the
 * week under it, then every day of the month, week after week.
 *
 * The month names, the weekday names and the first day of the week follow the app language. A day
 * with an appointment is circled with the tertiary color. A click on a day calls [onDayClick].
 *
 * @param modifier modifier applied to the calendar box.
 * @param onDayClick called with the date of the day the user clicks on.
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient. A patient gets
 *   larger texts (month, year, weekdays, day numbers) and larger icons.
 * @param viewModel the ViewModel owning the displayed month and the days with an appointment. It
 *   starts on the current month.
 */
@Composable
fun Calendar(
    modifier: Modifier = Modifier,
    onDayClick: (LocalDate) -> Unit = {},
    isPatient: Boolean = true,
    viewModel: CalendarViewModel = viewModel(),
) {
  // State: the displayed month, owned by the ViewModel.
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  // The app language decides the month and weekday names and the first day of the week.
  val locale = LocalConfiguration.current.locales[0]
  val context = LocalContext.current
  val resources = LocalResources.current

  // Loaded each time the calendar is shown, for example when coming back from another screen.
  LaunchedEffect(viewModel, isPatient) { viewModel.loadAppointments(isPatient) }

  val errorMsgRes = uiState.errorMsgRes
  LaunchedEffect(errorMsgRes) {
    if (errorMsgRes != null) {
      Toast.makeText(context, resources.getString(errorMsgRes), Toast.LENGTH_SHORT).show()
      viewModel.clearErrorMsg()
    }
  }

  Column(
      modifier =
          modifier
              .fillMaxWidth(CALENDAR_WIDTH_FRACTION)
              .testTag(CalendarTestTags.CALENDAR)
              .border(
                  BORDER_THICKNESS,
                  MaterialTheme.colorScheme.outline,
                  RoundedCornerShape(ROUND_CORNER),
              )
              .padding(CAREGIVER_PADDING_SMALL)
  ) {
    // Top: month and year, and the arrows (they call the ViewModel).
    CalendarHeader(
        month = uiState.month,
        locale = locale,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        isPatient = isPatient,
    )
    // Under the header: the names of the days of the week.
    CalendarWeekdays(locale = locale, isPatient = isPatient)
    // Then the days of the month; a click is reported to the caller through [onDayClick].
    CalendarDays(
        month = uiState.month,
        locale = locale,
        onDayClick = onDayClick,
        isPatient = isPatient,
        appointmentDays = uiState.appointmentDays,
    )
  }
}

/**
 * Month and year at the top left, the previous and next month arrows at the top right.
 *
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient.
 */
@Composable
@NonRestartableComposable
internal fun CalendarHeader(
    month: YearMonth = YearMonth.now(),
    locale: Locale = Locale.getDefault(),
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    isPatient: Boolean = true,
) {
  // The formatter gives the month name as the language writes it inside a sentence.
  // e.g.: lowercase in French ("novembre 2026"), which looks wrong as a title. titlecase puts the
  // first letter in capital ("Novembre 2026"), following the rules of the locale.
  // A language that has no capitals, or that already capitalizes the month, is left as it is.
  val title =
      DateTimeFormatter.ofPattern(MONTH_TITLE_PATTERN, locale).format(month).replaceFirstChar {
        it.titlecase(locale)
      }

  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = title,
        style = calendarTitleTextStyle(isPatient),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(WEIGHT_ONE).testTag(CalendarTestTags.MONTH_TITLE),
    )
    IconButton(
        onClick = onPreviousMonth,
        modifier = Modifier.testTag(CalendarTestTags.PREVIOUS_MONTH_BUTTON),
    ) {
      Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          contentDescription = stringResource(R.string.calendarPreviousMonth),
          tint = MaterialTheme.colorScheme.secondary,
          modifier =
              Modifier.size(calendarIconSize(isPatient))
                  .testTag(CalendarTestTags.PREVIOUS_MONTH_ICON),
      )
    }
    IconButton(
        onClick = onNextMonth,
        modifier = Modifier.testTag(CalendarTestTags.NEXT_MONTH_BUTTON),
    ) {
      Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = stringResource(R.string.calendarNextMonth),
          tint = MaterialTheme.colorScheme.secondary,
          modifier =
              Modifier.size(calendarIconSize(isPatient)).testTag(CalendarTestTags.NEXT_MONTH_ICON),
      )
    }
  }
}

/**
 * The names of the days of the week, starting with the first day of the week of [locale].
 *
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient.
 */
@Composable
@NonRestartableComposable
internal fun CalendarWeekdays(
    locale: Locale = Locale.getDefault(),
    isPatient: Boolean = true,
) {
  val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek

  Row(modifier = Modifier.fillMaxWidth()) {
    repeat(DayOfWeek.entries.size) { index ->
      Text(
          text = firstDayOfWeek.plus(index.toLong()).getDisplayName(TextStyle.SHORT, locale),
          style = calendarBodyTextStyle(isPatient),
          color = MaterialTheme.colorScheme.secondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(WEIGHT_ONE).testTag(CalendarTestTags.weekday(index)),
      )
    }
  }
}

/**
 * Every day of [month], one row per week. A day is clickable: [onDayClick] gets its date.
 *
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient.
 * @param appointmentDays the days of [month] that have an appointment. They are circled.
 */
@Composable
@NonRestartableComposable
internal fun CalendarDays(
    month: YearMonth = YearMonth.now(),
    locale: Locale = Locale.getDefault(),
    onDayClick: (LocalDate) -> Unit = {},
    isPatient: Boolean = true,
    appointmentDays: Set<Int> = emptySet(),
) {
  val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
  val weeks = calendarCells(month, firstDayOfWeek).chunked(DayOfWeek.entries.size)

  Column {
    weeks.forEach { week ->
      Row(modifier = Modifier.fillMaxWidth()) {
        week.forEach { day ->
          Box(
              modifier = Modifier.weight(WEIGHT_ONE).aspectRatio(CALENDAR_CELL_ASPECT_RATIO),
              contentAlignment = Alignment.Center,
          ) {
            // A null cell is an empty cell of the grid. It shows nothing and is not clickable,
            // but it still takes its place so that the other days stay in the right column.
            // This covers two situations:
            // 1. before the first day of the month, to fill the empty day before
            // 2. after the last one, to fill the empty day after
            if (day != null) {
              CalendarDay(
                  day = day,
                  onClick = { onDayClick(month.atDay(day)) },
                  isPatient = isPatient,
                  hasAppointment = day in appointmentDays,
              )
            }
          }
        }
      }
    }
  }
}

/**
 * A clickable day number. The click flash is round.
 *
 * @param isPatient Boolean indicating if the user viewing the calendar is a Patient.
 * @param hasAppointment whether the day has an appointment, and so is circled.
 */
@Composable
internal fun CalendarDay(
    day: Int = 1,
    onClick: () -> Unit = {},
    isPatient: Boolean = true,
    hasAppointment: Boolean = false,
) {
  Box(
      modifier =
          Modifier.fillMaxSize()
              .padding(PADDING_MINI)
              // Clipping before clickable makes the click flash round.
              .clip(CircleShape)
              .clickable(onClick = onClick)
              .testTag(CalendarTestTags.day(day)),
      contentAlignment = Alignment.Center,
  ) {
    // Its own node, so that tests can find the circle with its test tag.
    if (hasAppointment) {
      Box(
          modifier =
              Modifier.matchParentSize()
                  .border(
                      CALENDAR_CIRCLE_THICKNESS,
                      MaterialTheme.colorScheme.tertiary,
                      CircleShape,
                  )
                  .testTag(CalendarTestTags.appointmentCircle(day))
      )
    }
    Text(
        text = day.toString(),
        style = calendarBodyTextStyle(isPatient),
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** Preview of the calendar on the current month, with the sizes of the caregiver. */
@Preview(showBackground = true)
@Composable
internal fun CalendarPreview() {
  AmberAppTheme { Calendar(isPatient = false) }
}
