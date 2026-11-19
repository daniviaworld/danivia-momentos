package com.daniviaworld.momentos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.daniviaworld.momentos.ui.AppRoot
import com.daniviaworld.momentos.ui.MomentosTheme
import com.daniviaworld.momentos.ui.OpenRequests

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            MomentosTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** La notificación de un recuerdo nuevo abre directamente la pantalla de Recuerdos. */
    private fun handle(intent: Intent?) {
        if (intent?.getStringExtra("abrir") == "recuerdos") OpenRequests.memories = true
    }
}
