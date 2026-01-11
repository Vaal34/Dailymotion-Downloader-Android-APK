package com.dailymotion.downloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dailymotion.downloader.ui.screen.MainScreen
import com.dailymotion.downloader.ui.theme.DailymotionDownloaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Récupérer l'URL partagée si l'activité est lancée via un intent de partage
        val sharedUrl = when {
            intent?.action == Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            intent?.action == Intent.ACTION_VIEW -> {
                intent.data?.toString()
            }
            else -> null
        }

        setContent {
            DailymotionDownloaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(sharedUrl = sharedUrl)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Gérer les nouveaux intents de partage
        val sharedUrl = when {
            intent.action == Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            intent.action == Intent.ACTION_VIEW -> {
                intent.data?.toString()
            }
            else -> null
        }

        if (!sharedUrl.isNullOrEmpty()) {
            setContent {
                DailymotionDownloaderTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(sharedUrl = sharedUrl)
                    }
                }
            }
        }
    }
}
