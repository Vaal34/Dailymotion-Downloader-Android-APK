package com.music.music

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var statusText: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var emptyText: TextView

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra("title") ?: "Vidéo"
            val success = intent?.getBooleanExtra("success", false) ?: false

            if (success) {
                showWin95Toast("Téléchargement terminé: $title", true)
            } else {
                val error = intent?.getStringExtra("error") ?: "Erreur inconnue"
                showWin95Toast("Échec: $error", false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        downloadButton = findViewById(R.id.downloadButton)
        statusText = findViewById(R.id.statusText)
        historyContainer = findViewById(R.id.historyContainer)
        emptyText = findViewById(R.id.emptyText)

        // Demander les permissions
        requestPermissions()

        // Gérer le partage depuis d'autres apps
        handleSharedIntent(intent)

        downloadButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                startDownload(url)
            } else {
                showWin95Toast("Veuillez entrer un lien Dailymotion", false)
            }
        }

        loadHistory()

        // Enregistrer le receiver pour les notifications de fin de téléchargement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                downloadCompleteReceiver,
                IntentFilter("com.music.music.DOWNLOAD_COMPLETE"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                downloadCompleteReceiver,
                IntentFilter("com.music.music.DOWNLOAD_COMPLETE")
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            // Ignorer si déjà unregister
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleSharedIntent(it) }
    }

    private fun handleSharedIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                urlInput.setText(sharedText)
                showWin95Toast("Lien collé depuis le partage", true)
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }
    }

    private fun startDownload(url: String) {
        val videoId = extractVideoId(url)
        if (videoId == null) {
            showWin95Toast("URL invalide!", false)
            showStatus("Utilisez un lien dailymotion.com/video/xxx", isError = true)
            return
        }

        downloadButton.isEnabled = false
        showStatus("Récupération des informations...")

        lifecycleScope.launch {
            try {
                val videoInfo = withContext(Dispatchers.IO) {
                    DailymotionHelper.getVideoInfo(videoId)
                }

                if (videoInfo == null) {
                    showStatus("Impossible de récupérer la vidéo", isError = true)
                    showWin95Toast("Vidéo non trouvée", false)
                    downloadButton.isEnabled = true
                    return@launch
                }

                showStatus("Téléchargement en cours...")
                showWin95Toast("Démarrage: ${videoInfo.title}", true)

                // Lancer le service de téléchargement
                val serviceIntent = Intent(this@MainActivity, DownloadService::class.java).apply {
                    putExtra("video_id", videoInfo.id)
                    putExtra("title", videoInfo.title)
                    putExtra("url", videoInfo.downloadUrl)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                urlInput.setText("")

                // Ajouter à l'historique
                addToHistory(videoInfo.title)

            } catch (e: Exception) {
                showStatus("Erreur: ${e.message}", isError = true)
                showWin95Toast("Erreur!", false)
            } finally {
                downloadButton.isEnabled = true
            }
        }
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""dailymotion\.com/video/([a-zA-Z0-9]+)"""),
            Regex("""dai\.ly/([a-zA-Z0-9]+)"""),
            Regex("""dailymotion\.com/embed/video/([a-zA-Z0-9]+)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun showStatus(message: String, isError: Boolean = false) {
        statusText.visibility = View.VISIBLE
        statusText.text = message
        statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) android.R.color.holo_red_dark else R.color.win95_black
            )
        )
    }

    private fun showWin95Toast(message: String, isSuccess: Boolean) {
        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.win95_toast, null)

        val textView = layout.findViewById<TextView>(R.id.toastText)
        val iconView = layout.findViewById<ImageView>(R.id.toastIcon)

        textView.text = message
        iconView.setImageResource(
            if (isSuccess) android.R.drawable.ic_dialog_info
            else android.R.drawable.ic_dialog_alert
        )

        val toast = Toast(applicationContext)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }

    private fun addToHistory(title: String) {
        emptyText.visibility = View.GONE

        val itemView = TextView(this).apply {
            text = "• $title"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.win95_black))
            setPadding(0, 8, 0, 8)
        }

        historyContainer.addView(itemView, 0)

        // Sauvegarder dans SharedPreferences
        val prefs = getSharedPreferences("history", MODE_PRIVATE)
        val history = prefs.getStringSet("items", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        history.add(title)
        prefs.edit().putStringSet("items", history).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("history", MODE_PRIVATE)
        val history = prefs.getStringSet("items", emptySet()) ?: emptySet()

        if (history.isNotEmpty()) {
            emptyText.visibility = View.GONE
            history.forEach { title ->
                val itemView = TextView(this).apply {
                    text = "• $title"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.win95_black))
                    setPadding(0, 8, 0, 8)
                }
                historyContainer.addView(itemView)
            }
        }
    }
}
