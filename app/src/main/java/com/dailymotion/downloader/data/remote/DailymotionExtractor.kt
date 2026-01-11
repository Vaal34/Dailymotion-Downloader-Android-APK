package com.dailymotion.downloader.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

data class VideoInfo(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val downloadUrl: String,
    val quality: String
)

class DailymotionExtractor {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    suspend fun extractVideoInfo(videoId: String): VideoInfo = withContext(Dispatchers.IO) {
        // Méthode 1: Récupérer les métadonnées via l'embed
        val embedUrl = "https://www.dailymotion.com/embed/video/$videoId"

        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: throw Exception("Impossible de récupérer la page")

        // Chercher le JSON de configuration dans la page
        val configPattern = Pattern.compile("var\\s+config\\s*=\\s*(\\{.+?\\});", Pattern.DOTALL)
        val configMatcher = configPattern.matcher(html)

        var title = "Video Dailymotion"
        var thumbnailUrl: String? = null
        var downloadUrl: String? = null
        var quality = "auto"

        // Essayer de trouver les métadonnées dans le HTML
        val metadataPattern = Pattern.compile("\"metadata\":\\s*(\\{[^}]+\\})")
        val metadataMatcher = metadataPattern.matcher(html)

        if (metadataMatcher.find()) {
            try {
                val metadataJson = JSONObject(metadataMatcher.group(1))
                title = metadataJson.optString("title", title)
                thumbnailUrl = metadataJson.optString("poster_url", null)
            } catch (e: Exception) {
                // Ignorer les erreurs de parsing
            }
        }

        // Chercher les qualités disponibles
        val qualitiesPattern = Pattern.compile("\"qualities\":\\s*(\\{[^}]+\\})")
        val qualitiesMatcher = qualitiesPattern.matcher(html)

        if (qualitiesMatcher.find()) {
            try {
                val qualitiesJson = JSONObject(qualitiesMatcher.group(1))
                // Chercher la meilleure qualité disponible
                val preferredQualities = listOf("1080", "720", "480", "380", "240", "auto")
                for (q in preferredQualities) {
                    if (qualitiesJson.has(q)) {
                        val qualityArray = qualitiesJson.getJSONArray(q)
                        for (i in 0 until qualityArray.length()) {
                            val item = qualityArray.getJSONObject(i)
                            val type = item.optString("type", "")
                            if (type.contains("mp4") || type.contains("video")) {
                                downloadUrl = item.optString("url", null)
                                quality = q
                                break
                            }
                        }
                        if (downloadUrl != null) break
                    }
                }
            } catch (e: Exception) {
                // Ignorer les erreurs de parsing
            }
        }

        // Méthode alternative: utiliser l'API player
        if (downloadUrl == null) {
            downloadUrl = getVideoUrlFromPlayerApi(videoId)
        }

        // Méthode de secours: HLS stream
        if (downloadUrl == null) {
            downloadUrl = "https://www.dailymotion.com/cdn/H264-848x480/video/$videoId.mp4"
            quality = "480"
        }

        // Récupérer le titre depuis l'API publique si pas trouvé
        if (title == "Video Dailymotion") {
            try {
                val apiRequest = Request.Builder()
                    .url("https://api.dailymotion.com/video/$videoId?fields=title,thumbnail_url")
                    .build()
                val apiResponse = client.newCall(apiRequest).execute()
                val apiJson = JSONObject(apiResponse.body?.string() ?: "{}")
                title = apiJson.optString("title", title)
                thumbnailUrl = apiJson.optString("thumbnail_url", thumbnailUrl)
            } catch (e: Exception) {
                // Ignorer
            }
        }

        VideoInfo(
            id = videoId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            downloadUrl = downloadUrl,
            quality = quality
        )
    }

    private fun getVideoUrlFromPlayerApi(videoId: String): String? {
        return try {
            val playerUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val request = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")

            val qualities = json.optJSONObject("qualities")
            if (qualities != null) {
                val preferredQualities = listOf("1080", "720", "480", "380", "240", "auto")
                for (q in preferredQualities) {
                    val qualityArray = qualities.optJSONArray(q)
                    if (qualityArray != null && qualityArray.length() > 0) {
                        for (i in 0 until qualityArray.length()) {
                            val item = qualityArray.getJSONObject(i)
                            val type = item.optString("type", "")
                            if (type.contains("mp4") || type.contains("video")) {
                                return item.optString("url", null)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""dailymotion\.com/video/([a-zA-Z0-9]+)"""),
            Regex("""dai\.ly/([a-zA-Z0-9]+)"""),
            Regex("""dailymotion\.com/embed/video/([a-zA-Z0-9]+)"""),
            Regex("""dailymotion\.com/[^/]+/video/([a-zA-Z0-9]+)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // Si l'URL est juste l'ID
        if (url.matches(Regex("^[a-zA-Z0-9]+$"))) {
            return url
        }

        return null
    }
}
