package com.lorongtea.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LorongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF315548),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBDA),
            onPrimaryContainer = Color(0xFF17382D),
            secondary = Color(0xFF74502C),
            background = Color(0xFFF9F7F0),
            surface = Color(0xFFF9F7F0),
            surfaceVariant = Color(0xFFE9E8DC),
            onSurface = Color(0xFF202822),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String,
    back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { if (back != null) TextButton(onClick = back) { Text("Back") } },
                actions = actions,
            )
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
fun LoadNotice(loading: Boolean, error: String?) {
    Column {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) {
            Text(error, Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.error)
        }
    }
}
