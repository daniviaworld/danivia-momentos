package com.daniviaworld.momentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class HelpItem(val icon: String, val title: String, val text: String, val adminOnly: Boolean = false)
private class HelpSection(val title: String, val items: List<HelpItem>)

private val helpPhone = listOf(
    HelpSection(
        "📲 Subir y ver fotos",
        listOf(
            HelpItem("➕", "Subir fotos y vídeos", "Pulsa ＋ y elige de la galería (hasta 50 a la vez). La app mejora la luz y el color sola; si no lo quieres, apaga «Mejora automática» al subir o en Familia. Vídeos de hasta 5 minutos."),
            HelpItem("🖼️", "25 marcos", "Al subir puedes elegir marco: Polaroid, Neón, Cumpleaños, Navidad, Halloween, Postal, Cole… Verás tu propia foto con cada uno."),
            HelpItem("📁", "Álbumes", "Guarda las fotos en un álbum o crea uno nuevo con «➕ Nuevo álbum» al subir. Todos los álbumes están en 🗂️."),
            HelpItem("👀", "8 formas de verlas", "Cuadrícula, Carrusel, Estilo cine, Cubo 3D, Diapositivas con música, Mosaico, Línea del tiempo y Mesa de fotos (polaroids que caen solas). Se cambia arriba."),
            HelpItem("🔍", "Ver en grande", "Toca una foto. Desliza para pasar a la siguiente. Pellizca con dos dedos o haz doble toque para hacer zoom."),
            HelpItem("☑️", "Elegir varias", "Mantén pulsada una foto en la cuadrícula para elegir varias y moverlas de álbum.", adminOnly = true),
        ),
    ),
    HelpSection(
        "💕 En familia",
        listOf(
            HelpItem("❤️", "Me gusta y reacciones", "Dale al corazón, o toca 😊 para reaccionar con 😂 😍 😮 👏 🥰."),
            HelpItem("💬", "Comentarios", "Escribe algo debajo de cada foto. Quien la subió recibe un aviso."),
            HelpItem("🎙️", "Nota de voz", "Quien sube la foto puede grabarle un audio contando el momento. Todos lo pueden escuchar."),
            HelpItem("📤", "Compartir", "Botón de compartir arriba: envía la foto o el vídeo por WhatsApp, Telegram, o guárdalo en tu móvil."),
            HelpItem("🔔", "Avisos y recuerdos", "Te avisa cuando hay fotos nuevas y de «Tal día como hoy»: las fotos de hace un año."),
            HelpItem("📱", "Widget «Foto del día»", "Mantén pulsada la pantalla de inicio del móvil → Widgets → DANIVIA Momentos."),
            HelpItem("👤", "Tu nombre", "En Familia → Tu nombre puedes cambiar cómo te ve la familia."),
        ),
    ),
    HelpSection(
        "🎬 Creaciones",
        listOf(
            HelpItem("🍿", "Recuerdos automáticos", "Como Google Fotos: la app busca sola momentos especiales (Tal día como hoy, lo mejor del mes, un gran día, las favoritas) y te prepara un vídeo con música. Están en 🎬 Recuerdos; desde ahí puedes compartirlos o subirlos a la familia."),
            HelpItem("🎬", "Vídeo, collage y libro", "En 🗂️ Álbumes, toca ⋯ en un álbum: vídeo con música, collage (eliges fotos, forma, fondo y marco) o libro PDF para imprimir con el diseño de página que quieras."),
            HelpItem("✏️", "Editar una foto ya subida", "Abre la foto y toca ✏️: pegatinas (arrástralas con el dedo), texto y marco. Siempre se puede volver a la original.", adminOnly = true),
            HelpItem("🎵", "Cambiar la música de un vídeo", "Abre el vídeo y toca 🎵. Elige la música nueva; deja apagado «Mantener el sonido original» para quitar la vieja.", adminOnly = true),
            HelpItem("🎶", "Música", "8 canciones incluidas y las que añada el admin en Familia. Cada álbum puede tener la suya."),
        ),
    ),
    HelpSection(
        "📺 En la tele",
        listOf(
            HelpItem("📺", "Las mismas fotos", "Instala la app en la Android TV (Downloader, código 2101417) y vincúlala desde el móvil: Familia → Vincular una TV."),
            HelpItem("🎮", "Con el mando", "Flechas para moverte, OK para abrir, Atrás para volver a los modos de vista."),
            HelpItem("🌙", "Salvapantallas", "En la tele: Familia → Salvapantallas. Elige cubo 3D girando, diapositivas o carrusel, qué fotos salen y si lleva música."),
        ),
    ),
    HelpSection(
        "⚙️ Otros",
        listOf(
            HelpItem("🔄", "Se actualiza sola", "Cuando hay versión nueva la app te lo dice. Pulsa «Actualizar» y listo."),
            HelpItem("🔒", "Privada", "Sólo los miembros de la familia (con el código familiar) ven vuestras fotos."),
        ),
    ),
)

/** «¿Cómo funciona?»: todas las funciones explicadas, en el móvil y en la tele. */
@Composable
fun HelpDialog(isTv: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = 900.dp)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = if (isTv) 48.dp else 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("¿Cómo funciona?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Todo lo que puedes hacer con DANIVIA Momentos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!isTv) {
                                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
                            }
                        }
                    }
                    helpPhone.forEach { section ->
                        item {
                            Box(
                                Modifier
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF8A5CFF), Color(0xFFFF5FA2), Color(0xFFFFA94D))))
                                    .padding(horizontal = 18.dp, vertical = 6.dp),
                            ) {
                                Text(section.title, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        section.items.forEach { h ->
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                        Box(
                                            Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(h.icon, fontSize = 24.sp) }
                                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(h.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                if (h.adminOnly) {
                                                    Text(
                                                        "admin",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2A1A00),
                                                        modifier = Modifier
                                                            .padding(start = 8.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color(0xFFFFB347))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                h.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        ) { Text("¡Entendido!") }
                    }
                }
            }
        }
    }
}
