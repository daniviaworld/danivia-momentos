package com.daniviaworld.momentos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Brand(big: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "DANIVIA",
            color = Orange,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 30.sp else 20.sp,
            letterSpacing = 4.sp,
        )
        Text(
            "Momentos",
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 56.sp else 38.sp,
        )
    }
}

@Composable
fun PhoneOnboardingScreen(vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf(vm.prefs.myName) }
    var familyName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(0) } // 0 = elegir, 1 = crear, 2 = unirse

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Brand(big = false)
        Spacer(Modifier.height(8.dp))
        Text(
            "Las fotos de la familia, en el móvil y en la tele",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            label = { Text("¿Cómo te llamas?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        when (mode) {
            0 -> {
                Button(
                    onClick = { mode = 2 },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("Unirme a mi familia (tengo un código)") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { mode = 1 },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("Crear una familia nueva") }
            }
            1 -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Nueva familia", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Serás el administrador. Después podrás dar el código a los demás.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = familyName,
                        onValueChange = { familyName = it.take(40) },
                        label = { Text("Nombre de la familia") },
                        placeholder = { Text("Familia Álvarez") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { mode = 0 }) { Text("Atrás") }
                        Button(
                            onClick = { vm.createFamily(name, familyName) },
                            enabled = !vm.busy && name.isNotBlank() && familyName.isNotBlank(),
                        ) { Text("Crear") }
                    }
                }
            }
            2 -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Unirme a mi familia", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pide el código de 6 letras a quien creó la familia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                        label = { Text("Código familiar") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { mode = 0 }) { Text("Atrás") }
                        Button(
                            onClick = { vm.joinFamily(name, code) },
                            enabled = !vm.busy && name.isNotBlank() && code.length == 6,
                        ) { Text("Entrar") }
                    }
                }
            }
        }

        if (vm.busy) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
fun TvOnboardingScreen(vm: MainViewModel) {
    var code by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Brand(big = true)
            Spacer(Modifier.height(24.dp))
            Text(
                "Vincula esta tele con tu familia",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(48.dp))
        Card(Modifier.weight(1f)) {
            Column(
                Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("En el móvil abre DANIVIA Momentos →", style = MaterialTheme.typography.titleMedium)
                Text("Familia → Vincular una TV, y escribe:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                val pairing = vm.tvPairingCode
                if (pairing == null) {
                    CircularProgressIndicator(Modifier.size(48.dp))
                } else {
                    Text(
                        pairing.chunked(3).joinToString("  "),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = Orange,
                        letterSpacing = 6.sp,
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "¿Prefieres escribir el código familiar?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                        singleLine = true,
                        placeholder = { Text("ABC123") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { vm.joinFamily("", code) },
                        enabled = !vm.busy && code.length == 6,
                    ) { Text("Entrar") }
                }
            }
        }
    }
}
