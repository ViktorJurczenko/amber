// Written by LaMaier, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.ui.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.github.se.amber.ui.theme.CAREGIVER_ICON_SIZE
import com.github.se.amber.ui.theme.PATIENT_ICON_SIZE
import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tests [calendarCells]: the layout of a month in a grid; and [calendarIconSize]*/
class CalendarUtilsTest {
  // ---------- calendarCells ----------

  // November 2026 has 30 days and starts on a Sunday.
  private val november = YearMonth.of(2026, 11)

  @Test
  fun sundayFirstMonthStartingOnSundayHasNoLeadingBlank() {
    assertEquals(1, calendarCells(november, DayOfWeek.SUNDAY).first())
  }

  @Test
  fun mondayFirstMonthStartingOnSundayHasSixLeadingBlanks() {
    assertEquals(6, calendarCells(november, DayOfWeek.MONDAY).takeWhile { it == null }.size)
  }

  @Test
  fun sizeIsAMultipleOfAWeek() {
    assertEquals(0, calendarCells(november, DayOfWeek.MONDAY).size % DayOfWeek.entries.size)
  }

  @Test
  fun sundayFirstNovemberFillsFiveWeeks() {
    assertEquals(35, calendarCells(november, DayOfWeek.SUNDAY).size)
  }

  @Test
  fun mondayFirstNovemberFillsSixWeeks() {
    assertEquals(42, calendarCells(november, DayOfWeek.MONDAY).size)
  }

  @Test
  fun sundayFirstMonthStartingOnMondayHasOneLeadingBlank() {
    // February 2027 starts on a Monday: the difference with Sunday is negative (1 - 7), which is
    // the case that needs the + daysInWeek before the modulo.
    assertEquals(
        listOf(null, 1),
        calendarCells(YearMonth.of(2027, 2), DayOfWeek.SUNDAY).take(2),
    )
  }

  @Test
  fun sundayFirstMonthStartingOnMondayFillsFiveWeeks() {
    assertEquals(35, calendarCells(YearMonth.of(2027, 2), DayOfWeek.SUNDAY).size)
  }

  @Test
  fun everyDayOfTheMonthAppearsOnce() {
    assertEquals((1..30).toList(), calendarCells(november, DayOfWeek.MONDAY).filterNotNull())
  }

  @Test
  fun leapFebruaryHasTwentyNineDays() {
    assertEquals(29, calendarCells(YearMonth.of(2028, 2), DayOfWeek.MONDAY).filterNotNull().size)
  }

  @Test
  fun nonLeapFebruaryStartingOnFirstColumnFillsFourWeeks() {
    // February 2027 has 28 days and starts on a Monday.
    assertEquals(28, calendarCells(YearMonth.of(2027, 2), DayOfWeek.MONDAY).size)
  }

  // ---------- default parameters ----------

  @Test
  fun defaultMonthIsTheCurrentMonth() {
    assertEquals(
        calendarCells(YearMonth.now(), DayOfWeek.SUNDAY),
        calendarCells(firstDayOfWeek = DayOfWeek.SUNDAY),
    )
  }

  @Test
  fun defaultFirstDayOfWeekIsMonday() {
    assertEquals(calendarCells(november, DayOfWeek.MONDAY), calendarCells(november))
  }

  @Test
  fun withoutParametersUsesBothDefaults() {
    assertEquals(calendarCells(YearMonth.now(), DayOfWeek.MONDAY), calendarCells())
  }

  // ---------- calendarIconSize ----------

  @Test
  fun iconSizeIsThePatientOneByDefault() {
    assertEquals(PATIENT_ICON_SIZE, calendarIconSize())
  }

  @Test
  fun iconSizeOfAPatientIsThePatientIconSize() {
    assertEquals(PATIENT_ICON_SIZE, calendarIconSize(isPatient = true))
  }

  @Test
  fun iconSizeOfACaregiverIsTheCaregiverIconSize() {
    assertEquals(CAREGIVER_ICON_SIZE, calendarIconSize(isPatient = false))
  }


  /** Tests [calendarTitleTextStyle] and [calendarBodyTextStyle].
  *
  * The theme used here gives a different size to every style. A helper that returns the wrong style
  * therefore always fails, which would not be the case with the default sizes if two of them were
  * equal. The tests compare the whole style, not only its size.
  */
  @RunWith(RobolectricTestRunner::class)
  class CalendarTextStyleTest {

    @get:Rule val composeTestRule = createComposeRule()

    // Every style the helpers can return has its own size.
    private val testTypography =
      androidx.compose.material3.Typography(
        titleLarge = TextStyle(fontSize = 21.sp),
        headlineMedium = TextStyle(fontSize = 22.sp),
        bodyMedium = TextStyle(fontSize = 23.sp),
        bodyLarge = TextStyle(fontSize = 24.sp),
      )

    /**
     * Asserts that [style], read inside [testTypography], is the style [expected] picks in that same
     * typography.
     */
    private fun assertStyle(
      expected: Typography.() -> TextStyle,
      style: @Composable () -> TextStyle,
    ) {
      var actual: TextStyle? = null
      var wanted: TextStyle? = null
      composeTestRule.setContent {
        MaterialTheme(typography = testTypography) {
          actual = style()
          wanted = MaterialTheme.typography.expected()
        }
      }
      composeTestRule.waitForIdle()
      assertEquals(wanted, actual)
    }

    // ---------- calendarTitleTextStyle ----------

    @Test
    fun titleStyleOfAPatientIsHeadlineMedium() {
      assertStyle({ headlineMedium }) { calendarTitleTextStyle(isPatient = true) }
    }

    @Test
    fun titleStyleOfACaregiverIsTitleLarge() {
      assertStyle({ titleLarge }) { calendarTitleTextStyle(isPatient = false) }
    }

    @Test
    fun titleStyleIsThePatientOneByDefault() {
      assertStyle({ headlineMedium }) { calendarTitleTextStyle() }
    }

    // ---------- calendarBodyTextStyle ----------

    @Test
    fun bodyStyleOfAPatientIsBodyLarge() {
      assertStyle({ bodyLarge }) { calendarBodyTextStyle(isPatient = true) }
    }

    @Test
    fun bodyStyleOfACaregiverIsBodyMedium() {
      assertStyle({ bodyMedium }) { calendarBodyTextStyle(isPatient = false) }
    }

    @Test
    fun bodyStyleIsThePatientOneByDefault() {
      assertStyle({ bodyLarge }) { calendarBodyTextStyle() }
    }
  }

}
