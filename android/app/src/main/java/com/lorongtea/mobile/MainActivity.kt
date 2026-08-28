package com.lorongtea.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lorongtea.mobile.ui.ListViewModel
import com.lorongtea.mobile.ui.LorongTheme
import com.lorongtea.mobile.ui.StoresScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LorongTeaApplication
        setContent {
            LorongTheme {
                val stores = viewModel { ListViewModel(app.backend::stores) }
                StoresScreen(stores.state.collectAsStateWithLifecycle().value, stores::refresh)
            }
        }
    }
}
