package com.ventures814.brdcrmbs.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.ui.screens.WeekScreen
import com.ventures814.brdcrmbs.ui.fasting.FastingEditorScreen
import com.ventures814.brdcrmbs.ui.meals.MealEditorScreen
import com.ventures814.brdcrmbs.ui.activities.ActivityEditorScreen

@Composable
fun rememberWeekModel(): WeekViewModel {
    val application = LocalContext.current.applicationContext as BrdcrmbsApplication
    return viewModel(factory = viewModelFactory {
        initializer { WeekViewModel(application.planningRepository, createSavedStateHandle()) }
    })
}

@Composable
fun WeekRoute(
    modifier: Modifier = Modifier,
    model: WeekViewModel = rememberWeekModel(),
    onEditorVisible: (Boolean) -> Unit = {},
    resetToTodayRequested: Boolean = false,
    onResetHandled: () -> Unit = {}
) {
    LaunchedEffect(resetToTodayRequested) {
        if (resetToTodayRequested) {
            model.resetToToday()
            onResetHandled()
        }
    }
    DisposableEffect(model) { onDispose { model.clearCopyFeedback() } }
    val state by model.state.collectAsStateWithLifecycle()
    val shortcut by model.shortcut.collectAsStateWithLifecycle()
    val copyBusy by model.copyBusy.collectAsStateWithLifecycle()
    val copyError by model.copyError.collectAsStateWithLifecycle()
    val copyMessage by model.copyMessage.collectAsStateWithLifecycle()
    val draft by model.editor.collectAsStateWithLifecycle()
    val loading by model.editorLoading.collectAsStateWithLifecycle()
    val busy by model.editorBusy.collectAsStateWithLifecycle()
    val error by model.editorError.collectAsStateWithLifecycle()
    val meal by model.mealEditor.collectAsStateWithLifecycle()
    val mealLoading by model.mealLoading.collectAsStateWithLifecycle()
    val mealBusy by model.mealBusy.collectAsStateWithLifecycle()
    val mealError by model.mealError.collectAsStateWithLifecycle()
    val savedMeals by model.savedMeals.collectAsStateWithLifecycle()
    val mealEditing = meal != null || mealLoading || mealError != null
    val activity by model.activityEditor.collectAsStateWithLifecycle()
    val activityLoading by model.activityLoading.collectAsStateWithLifecycle()
    val activityBusy by model.activityBusy.collectAsStateWithLifecycle()
    val activityError by model.activityError.collectAsStateWithLifecycle()
    val savedActivities by model.savedActivities.collectAsStateWithLifecycle()
    val activityEditing = activity != null || activityLoading || activityError != null
    val editing = draft != null || loading || mealEditing || activityEditing
    LaunchedEffect(editing) { onEditorVisible(editing) }
    DisposableEffect(Unit) { onDispose { onEditorVisible(false) } }
    BackHandler(editing) {
        if (activityEditing) model.closeActivityEditor() else if (mealEditing) model.closeMealEditor() else model.closeEditor()
    }
    if (activityEditing) {
        if (activityLoading || activity == null) {
            Column(modifier) {
                TextButton(onClick = model::closeActivityEditor) { Text("Back to Week") }
                Text(activityError ?: "Loading activity…")
            }
        } else ActivityEditorScreen(activity!!, savedActivities, activityBusy, activityError, model::updateActivityDraft,
            model::saveActivity, model::deleteActivity, model::closeActivityEditor, modifier)
        return
    }
    if (mealEditing) {
        if (mealLoading || meal == null) {
            Column(modifier) {
                TextButton(onClick = model::closeMealEditor) { Text("Back to Week") }
                Text(mealError ?: "Loading meal…")
            }
        } else MealEditorScreen(meal!!, savedMeals, mealBusy, mealError, model::updateMealDraft,
            model::saveMeal, model::deleteMeal, model::closeMealEditor, modifier)
        return
    }
    if (draft != null) {
        FastingEditorScreen(draft!!, busy, error, model::updateDraft, model::saveFast, model::deleteFast, model::closeEditor, modifier)
        return
    }
    if (loading) {
        Column(modifier) {
            TextButton(onClick = model::closeEditor) { Text("Back to Week") }
            Text("Loading plan…")
        }
        return
    }
    if (error != null) {
        Column(modifier) {
            Text(error!!)
            TextButton(onClick = model::closeEditor) { Text("Back to Week") }
        }
        return
    }
    WeekScreen(
        state = state,
        onDaySelected = model::selectDay,
        modifier = modifier,
        onPreviousWeek = { model.moveWeek(-1) },
        onNextWeek = { model.moveWeek(1) },
        onAddFast = model::addFast,
        onEditFast = model::editFast,
        onAddMeal = model::addMeal,
        onEditMeal = model::editMeal,
        onAddActivity = model::addActivity,
        onEditActivity = model::editActivity,
        onPlanningAction = model::openShortcut,
        copyMessage = copyMessage
    )
    shortcut?.let { PlanningShortcutDialog(it, copyBusy, copyError, model::changeShortcut, model::copyPlans, model::closeShortcut) }
}
