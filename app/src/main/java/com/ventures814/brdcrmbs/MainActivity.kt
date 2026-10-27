package com.ventures814.brdcrmbs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as BrdcrmbsApplication).reminders.refresh()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val feedback = intent.getStringExtra("dataResetFeedback")
        intent.removeExtra("dataResetFeedback")
        setContent { BrdcrmbsTheme { BrdcrmbsApp(resetApplicationUi = { message ->
            // A fresh Activity/task discards ALL retained/restorable UI state, not the app process.
            // Room/SharedPreferences have already committed; ordinary Clear uses live observers.
            startActivity(android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("dataResetFeedback", message))
        }, initialFeedback = feedback) } }
    }
}
