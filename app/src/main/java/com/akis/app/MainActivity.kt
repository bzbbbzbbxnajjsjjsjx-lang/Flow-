package com.akis.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.akis.app.domain.ReaderRules
import com.akis.app.ui.AkisApp

class MainActivity : ComponentActivity() {
    /** An address shared into Akış from another app, waiting to open the "add source" sheet. */
    private var shared by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // A recreated activity (rotation) must not offer the same shared address again.
        if (savedInstanceState == null) shared = sharedAddress(intent)
        setContent { AkisApp(sharedUrl = shared, onUseSharedUrl = { shared = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shared = sharedAddress(intent)
    }

    private fun sharedAddress(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let(ReaderRules::sharedUrl) else null
}
