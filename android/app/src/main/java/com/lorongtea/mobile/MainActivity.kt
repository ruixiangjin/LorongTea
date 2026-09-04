package com.lorongtea.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lorongtea.mobile.ui.navigation.AppNavigation
import com.lorongtea.mobile.ui.components.LorongTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LorongTeaApplication
        setContent {
            LorongTheme { AppNavigation(app) }
        }
    }
}
