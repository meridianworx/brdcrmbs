package com.ventures814.brdcrmbs

import com.ventures814.brdcrmbs.backup.BackupSnapshot
import com.ventures814.brdcrmbs.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class BackupSnapshotTest {
    private val day = LocalDate.of(2026, 11, 1)
    private fun snapshot(vararg activities: ActivityPlan) = BackupSnapshot(Instant.EPOCH, PlanningData(activities = activities.toList()))
    private fun rejects(action: () -> Unit) { try { action(); fail("Invalid backup accepted") } catch (_: IllegalArgumentException) { } }
    @Test fun emptySnapshotIsValid() { assertEquals(0, snapshot().validate().count) }
    @Test fun historicalOverlapsAndNoFastArePreservedRatherThanRepaired() {
        val fast = FastingEvent("one", Instant.EPOCH, Instant.EPOCH.plusSeconds(172800), ZoneId.of("UTC"), outcome = FastingOutcome.DONE)
        val data = PlanningData(fasting = listOf(fast, fast.copy(id = "two", outcome = FastingOutcome.MISSED)), noFast = listOf(NoFastPlan("none", LocalDate.ofEpochDay(0), fast.zone)))
        val backup = BackupSnapshot(Instant.EPOCH, data)
        assertEquals(backup, backup.validate()); assertEquals(3, backup.count)
    }
    @Test fun duplicateIdsAreRejectedWithinEachRecordKind() {
        val activity = ActivityPlan("same", day, name = "Walk")
        rejects { snapshot(activity, activity.copy(name = "Other")).validate() }
        val meal = MealPlan("same", day, text = "Meal")
        rejects { BackupSnapshot(Instant.EPOCH, PlanningData(meals = listOf(meal, meal))).validate() }
        val saved = SavedMeal("saved", "Meal")
        rejects { BackupSnapshot(Instant.EPOCH, savedMeals = listOf(saved, saved)).validate() }
    }
    @Test fun sameIdsAcrossIndependentCategoriesRemainValid() {
        val backup = snapshot(ActivityPlan("same", day, name = "Walk")).copy(plans = PlanningData(
            meals = listOf(MealPlan("same", day, text = "Meal")), activities = listOf(ActivityPlan("same", day, name = "Walk"))))
        assertEquals(backup, backup.validate())
    }
    @Test fun missingHistoricalActivityParentIsRejected() {
        rejects { snapshot(ActivityPlan("child", day, name = "Walk", rescheduledFromId = "missing")).validate() }
    }
    @Test fun cyclicHistoricalActivityLinksAreRejected() {
        rejects { snapshot(ActivityPlan("a", day, name = "A", rescheduledFromId = "b"), ActivityPlan("b", day, name = "B", rescheduledFromId = "a")).validate() }
    }
    @Test fun parentsAreInsertedBeforeChildrenRegardlessOfIdsAndInputOrder() {
        val original = ActivityPlan("z-parent", day, name = "Original", outcome = ActivityOutcome.MISSED)
        val child = ActivityPlan("a-child", day.plusDays(1), name = "Later", rescheduledFromId = original.id)
        assertEquals(listOf(original, child), snapshot(child, original).activitiesInInsertionOrder())
    }
}
