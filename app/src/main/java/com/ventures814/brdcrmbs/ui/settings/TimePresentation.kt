package com.ventures814.brdcrmbs.ui.settings

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventures814.brdcrmbs.BrdcrmbsApplication
import com.ventures814.brdcrmbs.preferences.ClockFormat

val LocalClockFormat = staticCompositionLocalOf { ClockFormat() }

/** Observe only while this app surface exists; refresh device choice on resume as well. */
@Composable
fun TimePresentation(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BrdcrmbsApplication
    val preferences by app.appPreferences.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    var system24 by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    DisposableEffect(context, owner) {
        fun refresh() { system24 = DateFormat.is24HourFormat(context) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val lifecycle = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        owner.lifecycle.addObserver(lifecycle)
        onDispose { context.contentResolver.unregisterContentObserver(observer); owner.lifecycle.removeObserver(lifecycle) }
    }
    CompositionLocalProvider(LocalClockFormat provides ClockFormat(preferences.timeFormat.is24Hour(system24)), content = content)
}
