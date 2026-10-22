package com.ventures814.brdcrmbs.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ventures814.brdcrmbs.R
import com.ventures814.brdcrmbs.ui.screens.ProgressScreen
import com.ventures814.brdcrmbs.ui.screens.SettingsScreen
import com.ventures814.brdcrmbs.ui.today.TodayRoute
import com.ventures814.brdcrmbs.ui.week.WeekRoute
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme

private enum class Destination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Today(R.string.nav_today, R.drawable.ic_today),
    Week(R.string.nav_week, R.drawable.ic_week),
    Progress(R.string.nav_progress, R.drawable.ic_progress),
    Settings(R.string.nav_settings, R.drawable.ic_settings)
}

@Composable
fun BrdcrmbsApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.Today) }
    var editorVisible by remember { mutableStateOf(false) }
    var resetWeekRequested by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!editorVisible) NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = {
                            if (destination == Destination.Week) resetWeekRequested = true
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
            Destination.Progress -> ProgressScreen(screenModifier)
            Destination.Settings -> SettingsScreen(screenModifier)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrdcrmbsAppPreview() {
    BrdcrmbsTheme { BrdcrmbsApp() }
}
