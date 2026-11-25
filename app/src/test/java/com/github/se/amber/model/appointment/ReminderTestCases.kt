// Written by Viktor Jurczenko, with assistance from Claude (Anthropic) via Claude Code.

package com.github.se.amber.model.appointment

/*
 * Reminder lists shared by the app's validation test and both Firestore rules tests. They all
 * check the same inputs, so the app and the rules can't drift apart.
 *
 * The rules can't loop over a list, so they check each place of it on its own. That is why the
 * invalid lists put a bad value at every place.
 */

/** Reminder lists the app and the rules both accept. */
val VALID_REMINDER_LISTS: List<List<Int>> =
    listOf(
        emptyList(),
        DEFAULT_REMINDER_MINUTES,
        listOf(0, MAX_REMINDER_MINUTES),
        List(MAX_REMINDERS) { it },
    )

/**
 * Reminder lists the app and the rules both refuse: one too many, then a bad value at each place.
 */
val INVALID_REMINDER_LISTS: List<List<Int>> =
    listOf(List(MAX_REMINDERS + 1) { it }) +
        (0 until MAX_REMINDERS).flatMap { place ->
          listOf(-1, MAX_REMINDER_MINUTES + 1).map { bad ->
            List(MAX_REMINDERS) { if (it == place) bad else it }
          }
        }
