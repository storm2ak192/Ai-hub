package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AIHubApp
import com.example.ui.theme.AIHubTheme
import com.example.ui.viewmodel.HubViewModel
import com.example.util.UrlSecurityValidator

class MainActivity : ComponentActivity() {

    private val viewModel: HubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            processIncomingIntent(intent)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()

            AIHubTheme(
                themeMode = themeMode,
                language = language
            ) {
                AIHubApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingIntent(intent)
    }

    private fun processIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        when (incoming.action) {
            com.example.browser.PinnedSiteShortcutManager.ACTION_OPEN_PINNED_SITE -> {
                val siteId = incoming.getStringExtra(com.example.browser.PinnedSiteShortcutManager.EXTRA_SITE_ID)
                if (!siteId.isNullOrBlank()) viewModel.openPinnedSite(siteId)
            }
            Intent.ACTION_VIEW -> {
                val url = incoming.dataString
                if (!url.isNullOrBlank()) {
                    viewModel.handleExternalUrl(url)
                }
            }
            Intent.ACTION_SEND -> {
                if (incoming.type == "text/plain") {
                    val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        val extractedUrl = UrlSecurityValidator.extractFirstUrlFromSharedText(sharedText)
                        if (extractedUrl != null) {
                            viewModel.handleExternalUrl(extractedUrl)
                        }
                    }
                }
            }
        }
    }
}
