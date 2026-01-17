package com.music.music

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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
    private lateinit var progressContainer: FrameLayout
    private lateinit var progressFill: View
    private lateinit var progressText: TextView
    private lateinit var progressLabel: TextView

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra("title") ?: "Vidéo"
            val success = intent?.getBooleanExtra("success", false) ?: false
            val platform = intent?.getStringExtra("platform") ?: "Videos"

            hideProgress()

            if (success) {
                showWin95Toast("Téléchargement terminé!\n$title", true)
                showStatus("Sauvegardé dans Téléchargements/$platform", isError = false)
            } else {
                val error = intent?.getStringExtra("error") ?: "Erreur inconnue"
                showWin95Toast("Échec: $error", false)
            }
        }
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("progress", 0) ?: 0
            updateProgress(progress)
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
        progressContainer = findViewById(R.id.progressContainer)
        progressFill = findViewById(R.id.progressFill)
        progressText = findViewById(R.id.progressText)
        progressLabel = findViewById(R.id.progressLabel)

        requestPermissions()
        handleSharedIntent(intent)

        // Initialiser yt-dlp pour YouTube et Twitter
        VideoHelper.initYtDlp(applicationContext)

        // Mettre à jour yt-dlp en arrière-plan pour avoir la dernière version
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                VideoHelper.updateYtDlp(applicationContext)
            }
        }

        downloadButton.setOnClickListener {
            // Cacher le clavier
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(urlInput.windowToken, 0)
            urlInput.clearFocus()

            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                startDownload(url)
            } else {
                showWin95Toast("Veuillez entrer un lien vidéo", false)
            }
        }

        loadHistory()

        // Enregistrer les receivers
        val filter1 = IntentFilter("com.music.music.DOWNLOAD_COMPLETE")
        val filter2 = IntentFilter("com.music.music.DOWNLOAD_PROGRESS")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter1, RECEIVER_NOT_EXPORTED)
            registerReceiver(progressReceiver, filter2, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, filter1)
            registerReceiver(progressReceiver, filter2)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadCompleteReceiver)
            unregisterReceiver(progressReceiver)
        } catch (e: Exception) {
            // Ignorer
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
        val platform = VideoHelper.detectPlatform(url)
        if (platform == Platform.UNKNOWN) {
            showWin95Toast("URL invalide!", false)
            showStatus("Plateformes supportées: Dailymotion, TikTok, Twitter/X, YouTube", isError = true)
            return
        }

        downloadButton.isEnabled = false
        showStatus("Récupération des informations (${getPlatformName(platform)})...")
        showProgress()
        updateProgress(0)

        lifecycleScope.launch {
            try {
                val videoInfo = withContext(Dispatchers.IO) {
                    VideoHelper.getVideoInfo(url)
                }

                if (videoInfo == null) {
                    showStatus("Impossible de récupérer la vidéo", isError = true)
                    showWin95Toast("Vidéo non trouvée ou inaccessible", false)
                    hideProgress()
                    downloadButton.isEnabled = true
                    return@launch
                }

                showStatus("Téléchargement: ${videoInfo.title}")
                showWin95Toast("Démarrage du téléchargement...", true)

                val serviceIntent = Intent(this@MainActivity, DownloadService::class.java).apply {
                    putExtra("video_id", videoInfo.id)
                    putExtra("title", videoInfo.title)
                    putExtra("url", videoInfo.downloadUrl)
                    putExtra("platform", videoInfo.platform.name)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                urlInput.setText("")
                addToHistory("${getPlatformEmoji(videoInfo.platform)} ${videoInfo.title}")

            } catch (e: Exception) {
                showStatus("Erreur: ${e.message}", isError = true)
                showWin95Toast("Erreur!", false)
                hideProgress()
            } finally {
                downloadButton.isEnabled = true
            }
        }
    }

    private fun getPlatformName(platform: Platform): String {
        return when (platform) {
            Platform.DAILYMOTION -> "Dailymotion"
            Platform.TIKTOK -> "TikTok"
            Platform.TWITTER -> "Twitter/X"
            Platform.YOUTUBE -> "YouTube"
            Platform.UNKNOWN -> "Inconnu"
        }
    }

    private fun getPlatformEmoji(platform: Platform): String {
        return when (platform) {
            Platform.DAILYMOTION -> "[DM]"
            Platform.TIKTOK -> "[TT]"
            Platform.TWITTER -> "[X]"
            Platform.YOUTUBE -> "[YT]"
            Platform.UNKNOWN -> "[?]"
        }
    }

    private fun showStatus(message: String, isError: Boolean = false) {
        statusText.visibility = View.VISIBLE
        statusText.text = message
        statusText.setTextColor(
            if (isError) Color.parseColor("#FF0000") else Color.parseColor("#000000")
        )
    }

    private fun showProgress() {
        progressLabel.visibility = View.VISIBLE
        progressContainer.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        progressLabel.visibility = View.GONE
        progressContainer.visibility = View.GONE
    }

    private fun updateProgress(progress: Int) {
        progressText.text = "$progress%"

        // Calculer la largeur de la barre
        progressContainer.post {
            val totalWidth = progressContainer.width - 4 // -4 pour les marges
            val fillWidth = (totalWidth * progress) / 100

            val params = progressFill.layoutParams
            params.width = fillWidth
            progressFill.layoutParams = params
        }
    }

    private fun showWin95Toast(message: String, isSuccess: Boolean) {
        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.win95_toast, null)

        val textView = layout.findViewById<TextView>(R.id.toastText)
        val iconView = layout.findViewById<ImageView>(R.id.toastIcon)
        val colorBar = layout.findViewById<View>(R.id.toastColorBar)

        textView.text = message

        if (isSuccess) {
            iconView.setImageResource(android.R.drawable.ic_dialog_info)
            colorBar.setBackgroundColor(Color.parseColor("#008000")) // Vert
        } else {
            iconView.setImageResource(android.R.drawable.ic_dialog_alert)
            colorBar.setBackgroundColor(Color.parseColor("#FF0000")) // Rouge
        }

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
            typeface = resources.getFont(R.font.w95fa_font)
        }

        historyContainer.addView(itemView, 0)

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
                    typeface = resources.getFont(R.font.w95fa_font)
                }
                historyContainer.addView(itemView)
            }
        }
    }
}
