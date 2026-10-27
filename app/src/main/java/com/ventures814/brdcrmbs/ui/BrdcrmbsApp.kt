package com.ventures814.brdcrmbs.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.ui.progress.ProgressRoute
import com.ventures814.brdcrmbs.ui.screens.SettingsScreen
import com.ventures814.brdcrmbs.ui.today.TodayRoute
import com.ventures814.brdcrmbs.ui.week.WeekRoute
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.launch

private enum class Destination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Today(R.string.nav_today, R.drawable.ic_today),
    Week(R.string.nav_week, R.drawable.ic_week),
    Progress(R.string.nav_progress, R.drawable.ic_progress),
    Settings(R.string.nav_settings, R.drawable.ic_settings)
}

@Composable
fun BrdcrmbsApp(resetApplicationUi: ((String) -> Unit)? = null, initialFeedback: String? = null) {
    com.ventures814.brdcrmbs.ui.settings.TimePresentation { BrdcrmbsContent(resetApplicationUi, initialFeedback) }
}

@Composable
private fun BrdcrmbsContent(resetApplicationUi: ((String) -> Unit)?, initialFeedback: String?) {
    var selected by rememberSaveable { mutableStateOf(Destination.Today) }
    var editorVisible by remember { mutableStateOf(false) }
    var resetWeekRequested by remember { mutableStateOf(false) }
    var resetProgressRequested by remember { mutableStateOf(false) }
    val owner = LocalViewModelStoreOwner.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { initialFeedback?.let { snackbars.showSnackbar(it) } }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            if (!editorVisible) NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = {
                            if (destination == Destination.Week) resetWeekRequested = true
                            if (destination == Destination.Progress) resetProgressRequested = true
                            selected = destination
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (selected) {
            Destination.Today -> TodayRoute(screenModifier, onEditorVisible = { editorVisible = it })
            Destination.Week -> WeekRoute(screenModifier, onEditorVisible = { editorVisible = it },
                resetToTodayRequested = resetWeekRequested, onResetHandled = { resetWeekRequested = false })
            Destination.Progress -> ProgressRoute(screenModifier, resetToTodayRequested = resetProgressRequested,
                onResetHandled = { resetProgressRequested = false })
            Destination.Settings -> SettingsScreen(screenModifier, onEditorVisible = { editorVisible = it }, onResetComplete = { message ->
                if (resetApplicationUi != null) resetApplicationUi(message)
                else {
                    owner?.viewModelStore?.clear()
                    resetWeekRequested = false; resetProgressRequested = false
                    selected = Destination.Today; editorVisible = false
                    scope.launch { snackbars.showSnackbar(message) }
                }
            })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrdcrmbsAppPreview() {
    BrdcrmbsTheme { BrdcrmbsApp() }
}
