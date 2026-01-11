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
            // Étape 1: Récupérer les métadonnées depuis l'API player
            val playerUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val request = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)

            // Vérifier s'il y a une erreur
            if (json.has("error")) {
                return null
            }

            // Récupérer le titre
            val title = json.optString("title", "Video_$videoId")

            // Récupérer l'URL du manifest HLS
            val qualities = json.optJSONObject("qualities") ?: return null
            val autoArray = qualities.optJSONArray("auto") ?: return null

            var m3u8Url: String? = null
            for (i in 0 until autoArray.length()) {
                val item = autoArray.getJSONObject(i)
                val type = item.optString("type", "")
                if (type.contains("mpegURL") || type.contains("m3u8")) {
                    m3u8Url = item.optString("url", null)
                    break
                }
            }

            if (m3u8Url == null) return null

            // Étape 2: Parser le manifest HLS pour obtenir la meilleure qualité
            val downloadUrl = getBestQualityFromM3u8(m3u8Url) ?: m3u8Url

            VideoInfo(videoId, title, downloadUrl)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getBestQualityFromM3u8(m3u8Url: String): String? {
        return try {
            val request = Request.Builder()
                .url(m3u8Url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: return null

            // Parser le manifest HLS pour trouver les différentes qualités
            val lines = content.lines()
            var bestUrl: String? = null
            var bestBandwidth = 0L

            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    // Extraire la bande passante
                    val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    val bandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0

                    // La ligne suivante contient l'URL
                    if (i + 1 < lines.size) {
                        val urlLine = lines[i + 1].trim()
                        if (urlLine.isNotEmpty() && !urlLine.startsWith("#")) {
                            if (bandwidth > bestBandwidth) {
                                bestBandwidth = bandwidth
                                bestUrl = if (urlLine.startsWith("http")) {
                                    urlLine
                                } else {
                                    // URL relative - construire l'URL complète
                                    val baseUrl = m3u8Url.substringBeforeLast("/")
                                    "$baseUrl/$urlLine"
                                }
                            }
                        }
                    }
                }
            }

            bestUrl
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
