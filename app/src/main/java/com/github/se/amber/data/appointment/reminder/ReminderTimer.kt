// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.data.appointment.reminder

/**
 * The one timer of this phone. When it goes off, the reminder check runs and sets it again: for the
 * next reminder, or for the next check 6 hours later. Nobody sees the timer itself: the user only
 * sees the notification.
 */
interface ReminderTimer {

  /** Sets the timer for [atMillis], in epoch milliseconds. It replaces the previous one. */
  fun set(atMillis: Long)

  /** Cancels the timer, if one is set. */
  fun cancel()
}
