package com.saraltechies.saralsmssync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.saraltechies.saralsmssync.data.AppPrefs
import com.saraltechies.saralsmssync.ui.MainScreen
import com.saraltechies.saralsmssync.ui.SaralTheme
import com.saraltechies.saralsmssync.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaralTheme {
                var paired by remember { mutableStateOf(AppPrefs.get(this).groupKey != null) }
                if (paired) {
                    MainScreen(onLeftGroup = { paired = false })
                } else {
                    SetupScreen(onPaired = { paired = true })
                }
            }
        }
    }
}
