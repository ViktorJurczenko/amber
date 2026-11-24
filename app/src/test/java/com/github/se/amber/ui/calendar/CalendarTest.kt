// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.se.amber.R
import com.github.se.amber.ui.calendar.CalendarTestTags.CALENDAR
import com.github.se.amber.ui.calendar.CalendarTestTags.MONTH_TITLE
import com.github.se.amber.ui.calendar.CalendarTestTags.NEXT_MONTH_BUTTON
import com.github.se.amber.ui.calendar.CalendarTestTags.PREVIOUS_MONTH_BUTTON
import com.github.se.amber.ui.theme.AmberAppTheme
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests [Calendar]: the box, the month navigation, the weekdays and the days. */
@RunWith(RobolectricTestRunner::class)
class CalendarTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  // November has 30 days, October and December have 31.
  private val november = YearMonth.of(2026, 11)

  private fun launch(onDayClick: (LocalDate) -> Unit = {}) {
    // Created here, not in setContent: a ViewModel must not be constructed in a composable.
    val viewModel = CalendarViewModel(november)
    composeTestRule.setContent {
      AmberAppTheme { Calendar(onDayClick = onDayClick, viewModel = viewModel) }
    }
  }

  /** The text of the month title, to see that it changes without depending on its exact value. */
  private fun monthTitle(): String =
      composeTestRule
          .onNodeWithTag(MONTH_TITLE)
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text]
          .joinToString { it.text }

  // The display checks of the calendar are grouped in one test, to launch the screen only once.
  @Test
  fun displaysTheCalendar() {
    launch()
    composeTestRule.onNodeWithTag(CALENDAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MONTH_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PREVIOUS_MONTH_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).assertIsDisplayed()
    for (index in 0 until 7) {
      composeTestRule.onNodeWithTag(CalendarTestTags.weekday(index)).assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag(CalendarTestTags.day(30)).assertIsDisplayed()
  }

  @Test
  fun nextArrowShowsTheNextMonth() {
    launch()
    // The 31st only exists in December and not in november.
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertIsDisplayed()
  }

  @Test
  fun previousArrowShowsThePreviousMonth() {
    launch()
    // The 31st only exists in October and not in november.
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PREVIOUS_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.day(31)).assertIsDisplayed()
  }

  @Test
  fun clickingADayCallsOnDayClickWithItsDate() {
    var clicked: LocalDate? = null
    launch(onDayClick = { clicked = it })
    composeTestRule.onNodeWithTag(CalendarTestTags.day(12)).performClick()
    assertEquals(LocalDate.of(2026, 11, 12), clicked)
  }

  @Test
  fun clickingAfterChangingMonthGivesTheDateOfThatMonth() {
    var clicked: LocalDate? = null
    launch(onDayClick = { clicked = it })
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.day(5)).performClick()
    assertEquals(LocalDate.of(2026, 12, 5), clicked)
  }

  // The title and the arrows of the header are grouped in one test, to launch the header only once.
  @Test
  fun headerShowsTheTitleOfTheGivenLocaleAndTranslatedButtonDescriptions() {
    composeTestRule.setContent {
      AmberAppTheme { CalendarHeader(month = november, locale = Locale.US) }
    }
    composeTestRule.onNodeWithTag(MONTH_TITLE).assertTextEquals("November 2026")
    composeTestRule
        .onNodeWithTag(PREVIOUS_MONTH_BUTTON)
        .assertContentDescriptionEquals(
            composeTestRule.activity.getString(R.string.calendarPreviousMonth)
        )
    composeTestRule
        .onNodeWithTag(NEXT_MONTH_BUTTON)
        .assertContentDescriptionEquals(
            composeTestRule.activity.getString(R.string.calendarNextMonth)
        )
  }

  // The language comes from the app configuration, not from a Locale given to the header.
  @Test
  @Config(qualifiers = "fr")
  fun calendarFollowsTheAppLanguage() {
    launch()
    composeTestRule.onNodeWithTag(MONTH_TITLE).assertTextEquals("Novembre 2026")
  }

  @Test
  fun frenchHeaderTitleStartsWithACapitalizedMonthName() {
    composeTestRule.setContent {
      AmberAppTheme { CalendarHeader(month = november, locale = Locale.FRANCE) }
    }
    composeTestRule.onNodeWithTag(MONTH_TITLE).assertTextEquals("Novembre 2026")
  }

  @Test
  fun weekdaysStartOnSundayForUsLocale() {
    composeTestRule.setContent { AmberAppTheme { CalendarWeekdays(locale = Locale.US) } }
    composeTestRule.onNodeWithTag(CalendarTestTags.weekday(0)).assertTextEquals("Sun")
  }

  @Test
  fun headerButtonsRunTheirCallbacks() {
    var previousClicks = 0
    var nextClicks = 0
    composeTestRule.setContent {
      AmberAppTheme {
        CalendarHeader(
            month = november,
            locale = Locale.US,
            onPreviousMonth = { previousClicks++ },
            onNextMonth = { nextClicks++ },
        )
      }
    }
    composeTestRule.onNodeWithTag(PREVIOUS_MONTH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).performClick()
    assertEquals(1, previousClicks)
    assertEquals(1, nextClicks)
  }

  @Test
  fun leapFebruaryShowsDayTwentyNineAndCallsOnDayClick() {
    var clicked: LocalDate? = null
    composeTestRule.setContent {
      AmberAppTheme {
        CalendarDays(
            month = YearMonth.of(2028, 2),
            locale = Locale.US,
            onDayClick = { clicked = it },
        )
      }
    }
    composeTestRule.onNodeWithTag(CalendarTestTags.day(29)).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.day(30)).assertDoesNotExist()
    assertEquals(LocalDate.of(2028, 2, 29), clicked)
  }

  @Test
  fun calendarDayRunsTheGivenOnClick() {
    var clicked = false
    composeTestRule.setContent {
      AmberAppTheme { CalendarDay(day = 17, onClick = { clicked = true }) }
    }
    composeTestRule.onNodeWithTag(CalendarTestTags.day(17)).performClick()
    assertTrue(clicked)
  }

  // ---------- default parameters ----------

  // Note: currently, using the "real" ViewModel is safe and can't hit the network.
  // If CalendarViewModel start using an AppointmentRepository: viewModel() would then use
  // the default Firebase repository.
  // The following tests should then no longer call Calendar() with the defaults,
  // and should pass it a fake repository.

  @Test
  fun displaysTheCalendarWithTheDefaultParametersAndChangesItsMonth() {
    // Case for no viewModel and no onDayClick:
    // the calendar uses its own ViewModel and ignores the clicks.
    composeTestRule.setContent { AmberAppTheme { Calendar() } }
    composeTestRule.onNodeWithTag(CALENDAR).assertIsDisplayed()
    val before = monthTitle()
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).performClick()
    assertNotEquals(before, monthTitle())
  }

  // ---------- the parts of the calendar, with their default parameters ----------

  @Test
  fun displaysThePartsWithTheDefaultParameters() {
    composeTestRule.setContent {
      AmberAppTheme {
        Column {
          CalendarHeader()
          CalendarWeekdays()
          CalendarDays()
        }
      }
    }
    composeTestRule.onNodeWithTag(MONTH_TITLE).assertIsDisplayed()
    for (index in 0 until 7) {
      composeTestRule.onNodeWithTag(CalendarTestTags.weekday(index)).assertIsDisplayed()
    }
    // Every month has a 1st.
    composeTestRule.onNodeWithTag(CalendarTestTags.day(1)).assertIsDisplayed()
  }

  @Test
  fun clickingTheArrowsOfTheDefaultHeaderDoesNotChangeTheMonth() {
    composeTestRule.setContent { AmberAppTheme { CalendarHeader() } }
    val before = monthTitle()
    // The default callbacks do nothing: the month stays the same after each click.
    composeTestRule.onNodeWithTag(PREVIOUS_MONTH_BUTTON).performClick()
    assertEquals(before, monthTitle())
    composeTestRule.onNodeWithTag(NEXT_MONTH_BUTTON).performClick()
    assertEquals(before, monthTitle())
  }

  @Test
  fun previewDisplaysTheCalendar() {
    composeTestRule.setContent { CalendarPreview() }
    composeTestRule.onNodeWithTag(CALENDAR).assertIsDisplayed()
  }

  // ---------- modifier ----------

  @Test
  fun appliesTheModifierGivenToTheCalendar() {
    val viewModel = CalendarViewModel(november)
    composeTestRule.setContent {
      AmberAppTheme {
        Calendar(
            modifier = Modifier.testTag(MODIFIER),
            viewModel = viewModel,
        )
      }
    }
    composeTestRule.onNodeWithTag(MODIFIER).assertIsDisplayed()
  }

  private companion object {
    const val MODIFIER = "calendar_test_modifier"
  }
}
