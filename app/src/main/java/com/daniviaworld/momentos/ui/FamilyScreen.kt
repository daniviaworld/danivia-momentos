package com.daniviaworld.momentos.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniviaworld.momentos.data.Member
import com.daniviaworld.momentos.update.Updater

@Composable
fun FamilyScreen(vm: MainViewModel) {
    val family by vm.family.collectAsState()
    val members by vm.members.collectAsState()
    val context = LocalContext.current
    val me = vm.myUid
    val iAmAdmin = vm.isAdmin()
    var tvCode by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Member?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (vm.isTv) 48.dp else 16.dp,
            vertical = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.screen = Screen.Home }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    family?.name ?: "Mi familia",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Código familiar
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Código de la familia", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Quien lo tenga puede entrar a ver y subir fotos. Dáselo sólo a la familia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            family?.code ?: "······",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Orange,
                            letterSpacing = 8.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (!vm.isTv && family != null) {
                            IconButton(onClick = {
                                val text = "Únete a «${family?.name}» en DANIVIA Momentos con el código: ${family?.code}"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, "Compartir código"))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Compartir código")
                            }
                        }
                    }
                }
            }
        }

        // Vincular TV (sólo desde el móvil)
        if (!vm.isTv) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("📺 Vincular una TV", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Instala DANIVIA Momentos en la Android TV, ábrela y escribe aquí el número que aparece.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = tvCode,
                                onValueChange = { tvCode = it.filter { c -> c.isDigit() }.take(6) },
                                singleLine = true,
                                placeholder = { Text("123456") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { vm.linkTv(tvCode) { tvCode = "" } },
                                enabled = tvCode.length == 6 && !vm.busy,
                            ) { Text("Vincular") }
                        }
                    }
                }
            }
        }

        // Salvapantallas (sobre todo para la tele)
        item {
            var help by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("🖼️ Salvapantallas con vuestras fotos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (vm.isTv) "Cuando la tele esté encendida sin usarse, se verán las fotos de la familia."
                        else "Mientras el móvil se carga, puede mostrar las fotos de la familia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        val opened = try {
                            context.startActivity(
                                Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            true
                        } catch (_: Exception) {
                            false
                        }
                        help = true
                        if (!opened) vm.message = "No se pudo abrir el ajuste; mira las instrucciones de abajo."
                    }) { Text("Activar salvapantallas") }
                    if (help) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "En el ajuste que se abre elige «DANIVIA Momentos» como salvapantallas. " +
                                "Si no aparece (algunas Google TV lo ocultan): Ajustes → Sistema → Ahorro de energía / Salvapantallas. " +
                                "Si tampoco está, se puede activar una vez desde un ordenador con el comando:\n" +
                                "adb shell settings put secure screensaver_components com.daniviaworld.momentos/.dream.MomentosDream",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Música de fondo para diapositivas y cubo (con escucha previa)
        item {
            var preview by remember { mutableStateOf("") }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("🎵 Música de fondo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Suena en Diapositivas y en el Cubo 3D. Cada álbum puede tener la suya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    MusicChips(selected = vm.backgroundMusic, onSelect = {
                        vm.setBackgroundMusicKey(it)
                        preview = it
                    })
                    if (preview.isNotBlank()) {
                        BackgroundMusic(preview)
                        TextButton(onClick = { preview = "" }) { Text("⏹ Parar la escucha") }
                    }
                }
            }
        }

        item {
            Text(
                "Miembros (${members.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(members, key = { it.uid }) { m ->
            val isAdmin = family?.admins?.contains(m.uid) == true
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (m.device == "tv") "📺" else "📱", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.name + if (m.uid == me) " (tú)" else "",
                            fontWeight = FontWeight.Bold,
                        )
                        if (isAdmin) Text("Administrador", color = Orange, style = MaterialTheme.typography.bodySmall)
                    }
                    if (iAmAdmin && m.uid != me) {
                        if (!isAdmin && m.device != "tv") {
                            TextButton(onClick = { vm.makeAdmin(m.uid) }) { Text("Hacer admin") }
                        }
                        TextButton(onClick = { confirmRemove = m }) {
                            Text("Quitar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            if (iAmAdmin && (family?.admins?.size ?: 0) < 2 && !vm.isTv) {
                Text(
                    "Consejo: haz administrador a otro adulto de la familia. Si cambias de móvil o reinstalas la app, " +
                        "él podrá seguir gestionando el grupo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Versión ${Updater.currentVersionName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.checkForUpdates(manual = true) }) { Text("Buscar actualizaciones") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmLeave = true }) {
                Text(if (vm.isTv) "Desvincular esta TV" else "Salir de la familia")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (vm.isTv) "¿Desvincular esta TV?" else "¿Salir de la familia?") },
            text = { Text("Dejarás de ver las fotos. Podrás volver a entrar con el código familiar.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; vm.leaveFamily() }) { Text("Salir") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancelar") } },
        )
    }
    confirmRemove?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("¿Quitar a ${m.name}?") },
            text = { Text("Ya no podrá ver ni subir fotos. Sus fotos subidas se quedan en el álbum.") },
            confirmButton = {
                TextButton(onClick = { vm.removeMember(m.uid); confirmRemove = null }) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") } },
        )
    }
}
