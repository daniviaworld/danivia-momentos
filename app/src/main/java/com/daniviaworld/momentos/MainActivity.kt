package com.daniviaworld.momentos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.daniviaworld.momentos.ui.AppRoot
import com.daniviaworld.momentos.ui.MomentosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MomentosTheme {
                AppRoot()
            }
        }
    }
}
