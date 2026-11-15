package com.daniviaworld.momentos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    // Surface fija el color de textos e iconos (claro sobre fondo oscuro) en toda la app
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (val state = vm.appState) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is AppState.Error -> ErrorScreen(state.message) { vm.start() }
            AppState.Onboarding -> if (vm.isTv) TvOnboardingScreen(vm) else PhoneOnboardingScreen(vm)
            is AppState.Ready -> ReadyContent(vm)
        }

        vm.availableUpdate?.let { release ->
            val progress = vm.updateProgress
            AlertDialog(
                onDismissRequest = { if (progress == null) vm.dismissUpdate() },
                title = { Text("¡Nueva versión ${release.versionName}!") },
                text = {
                    Column {
                        Text("Hay una actualización de DANIVIA Momentos. Se descarga e instala sin perder nada.")
                        if (progress != null) {
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text("Descargando… ${(progress * 100).toInt()} %")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { vm.startUpdate() }, enabled = progress == null) { Text("Actualizar") }
                },
                dismissButton = {
                    if (progress == null) TextButton(onClick = { vm.dismissUpdate() }) { Text("Más tarde") }
                },
            )
        }

        vm.message?.let { msg ->
            AlertDialog(
                onDismissRequest = { vm.message = null },
                confirmButton = { TextButton(onClick = { vm.message = null }) { Text("Vale") } },
                text = { Text(msg) },
            )
        }
    }
    }
}

@Composable
private fun ReadyContent(vm: MainViewModel) {
    val photos by vm.photos.collectAsState()
    when (val s = vm.screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.Upload -> {
            BackHandler { vm.screen = Screen.Home }
            UploadScreen(vm)
        }
        Screen.FamilyInfo -> {
            BackHandler { vm.screen = Screen.Home }
            FamilyScreen(vm)
        }
        Screen.Albums -> {
            BackHandler { vm.screen = Screen.Home }
            AlbumsScreen(vm)
        }
        is Screen.Detail -> {
            BackHandler { vm.screen = Screen.Home }
            if (photos.isEmpty()) {
                LaunchedEffect(Unit) { vm.screen = Screen.Home }
            } else {
                val start = remember(s.photoId) { photos.indexOfFirst { it.id == s.photoId }.coerceAtLeast(0) }
                PhotoDetailScreen(vm, photos, start)
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ups…", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}
