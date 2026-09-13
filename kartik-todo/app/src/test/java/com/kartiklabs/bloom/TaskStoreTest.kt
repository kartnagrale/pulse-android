package com.kynurelabs.bloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskStoreTest {

    private fun task(
        recurrence: Recurrence = Recurrence.NONE,
        repeatEvery: Int = 1,
        repeatUnit: String = "days",
        weekdays: Set<Int> = emptySet(),
        repeatEnd: String = "",
        priority: Priority = Priority.MEDIUM,
        estimatedMinutes: Int = 30,
        due: String = "2026-09-13"
    ) = BloomTask(
        id = 1L,
        title = "Test task",
        category = "Work",
        due = due,
        recurrence = recurrence,
        repeatEvery = repeatEvery,
        repeatUnit = repeatUnit,
        weekdays = weekdays,
        repeatEnd = repeatEnd,
        priority = priority,
        estimatedMinutes = estimatedMinutes
    )

    @Test fun dailyRecurrenceMovesExactlyOneDay() {
        val start = LocalDate.of(2026, 9, 13)
        assertEquals(start.plusDays(1), nextOccurrence(start, task(recurrence = Recurrence.DAILY)))
    }

    @Test fun weeklyRecurrenceKeepsWeekCadence() {
        val start = LocalDate.of(2026, 9, 13)
        assertEquals(start.plusWeeks(1), nextOccurrence(start, task(recurrence = Recurrence.WEEKLY)))
    }

    @Test fun monthlyRecurrenceHandlesShortMonthsSafely() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), nextOccurrence(start, task(recurrence = Recurrence.MONTHLY)))
    }

    @Test fun customWeekdaysFindsNextSelectedDay() {
        val monday = LocalDate.of(2026, 9, 14)
        val recurring = task(recurrence = Recurrence.CUSTOM, weekdays = setOf(1, 3, 5))
        assertEquals(LocalDate.of(2026, 9, 16), nextOccurrence(monday, recurring))
    }

    @Test fun customIntervalUsesRequestedAmount() {
        val start = LocalDate.of(2026, 9, 13)
        val recurring = task(recurrence = Recurrence.CUSTOM, repeatEvery = 3, repeatUnit = "weeks")
        assertEquals(start.plusWeeks(3), nextOccurrence(start, recurring))
    }

    @Test fun recurrenceDoesNotPassConfiguredEndDate() {
        val start = LocalDate.of(2026, 9, 13)
        val recurring = task(recurrence = Recurrence.DAILY, repeatEnd = "2026-09-13")
        assertEquals(start, nextOccurrence(start, recurring))
    }

    @Test fun criticalPriorityOutranksLowPriorityWhenDatesMatch() {
        val today = LocalDate.of(2026, 9, 13)
        val critical = task(priority = Priority.CRITICAL, estimatedMinutes = 120)
        val low = task(priority = Priority.LOW, estimatedMinutes = 15)
        assertTrue(smartTaskScore(critical, today) > smartTaskScore(low, today))
    }

    @Test fun overdueTaskGetsUrgencyBoost() {
        val today = LocalDate.of(2026, 9, 13)
        val overdue = task(priority = Priority.MEDIUM, due = "2026-09-12")
        val future = task(priority = Priority.MEDIUM, due = "2026-09-20")
        assertTrue(smartTaskScore(overdue, today) > smartTaskScore(future, today))
    }

    @Test fun shorterTaskGetsSmallTieBreakerBonus() {
        val today = LocalDate.of(2026, 9, 13)
        val short = task(estimatedMinutes = 15)
        val long = task(estimatedMinutes = 120)
        assertTrue(smartTaskScore(short, today) > smartTaskScore(long, today))
    }
}
