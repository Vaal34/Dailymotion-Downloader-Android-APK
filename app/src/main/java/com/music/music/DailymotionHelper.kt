package com.music.music

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class VideoInfo(
    val id: String,
    val title: String,
    val downloadUrl: String
)

object DailymotionHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getVideoInfo(videoId: String): VideoInfo? {
        return try {
            // Étape 1: Récupérer le titre depuis l'API publique
            val title = getTitleFromApi(videoId) ?: "Video_$videoId"

            // Étape 2: Récupérer l'URL de téléchargement depuis la page player
            val downloadUrl = getDownloadUrl(videoId)

            if (downloadUrl != null) {
                VideoInfo(videoId, title, downloadUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getTitleFromApi(videoId: String): String? {
        return try {
            val request = Request.Builder()
                .url("https://api.dailymotion.com/video/$videoId?fields=title")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            json.optString("title", null)
        } catch (e: Exception) {
            null
        }
    }

    private fun getDownloadUrl(videoId: String): String? {
        return try {
            // Essayer l'API player metadata
            val playerUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val request = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)

            // Chercher dans les qualités
            val qualities = json.optJSONObject("qualities")
            if (qualities != null) {
                val preferredQualities = listOf("720", "480", "380", "240", "auto")
                for (q in preferredQualities) {
                    val qualityArray = qualities.optJSONArray(q)
                    if (qualityArray != null && qualityArray.length() > 0) {
                        for (i in 0 until qualityArray.length()) {
                            val item = qualityArray.getJSONObject(i)
                            val type = item.optString("type", "")
                            val url = item.optString("url", "")
                            if (url.isNotEmpty() && (type.contains("video") || type.contains("mp4"))) {
                                return url
                            }
                        }
                    }
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
