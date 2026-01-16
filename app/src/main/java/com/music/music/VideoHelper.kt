package com.music.music

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

enum class Platform {
    DAILYMOTION,
    TIKTOK,
    TWITTER,
    YOUTUBE,
    UNKNOWN
}

data class VideoInfo(
    val id: String,
    val title: String,
    val downloadUrl: String,
    val platform: Platform = Platform.UNKNOWN
)

object VideoHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Détecte la plateforme à partir de l'URL
     */
    fun detectPlatform(url: String): Platform {
        return when {
            url.contains("dailymotion.com") || url.contains("dai.ly") -> Platform.DAILYMOTION
            url.contains("tiktok.com") || url.contains("vm.tiktok.com") -> Platform.TIKTOK
            url.contains("twitter.com") || url.contains("x.com") -> Platform.TWITTER
            url.contains("youtube.com") || url.contains("youtu.be") -> Platform.YOUTUBE
            else -> Platform.UNKNOWN
        }
    }

    /**
     * Extrait l'ID de la vidéo selon la plateforme
     */
    fun extractVideoId(url: String, platform: Platform): String? {
        return when (platform) {
            Platform.DAILYMOTION -> extractDailymotionId(url)
            Platform.TIKTOK -> extractTikTokId(url)
            Platform.TWITTER -> extractTwitterId(url)
            Platform.YOUTUBE -> extractYouTubeId(url)
            Platform.UNKNOWN -> null
        }
    }

    private fun extractDailymotionId(url: String): String? {
        val patterns = listOf(
            Regex("""dailymotion\.com/video/([a-zA-Z0-9]+)"""),
            Regex("""dai\.ly/([a-zA-Z0-9]+)"""),
            Regex("""dailymotion\.com/embed/video/([a-zA-Z0-9]+)""")
        )
        for (pattern in patterns) {
            pattern.find(url)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun extractTikTokId(url: String): String? {
        val patterns = listOf(
            Regex("""tiktok\.com/@[^/]+/video/(\d+)"""),
            Regex("""tiktok\.com/[^/]+/video/(\d+)"""),
            Regex("""vm\.tiktok\.com/([a-zA-Z0-9]+)"""),
            Regex("""tiktok\.com/t/([a-zA-Z0-9]+)""")
        )
        for (pattern in patterns) {
            pattern.find(url)?.let { return it.groupValues[1] }
        }
        // Si c'est un lien court, retourner l'URL complète pour résolution
        if (url.contains("vm.tiktok.com") || url.contains("tiktok.com/t/")) {
            return url
        }
        return null
    }

    private fun extractTwitterId(url: String): String? {
        val patterns = listOf(
            Regex("""(?:twitter\.com|x\.com)/[^/]+/status/(\d+)"""),
            Regex("""(?:twitter\.com|x\.com)/i/status/(\d+)""")
        )
        for (pattern in patterns) {
            pattern.find(url)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun extractYouTubeId(url: String): String? {
        val patterns = listOf(
            Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/v/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""youtube\.com/shorts/([a-zA-Z0-9_-]{11})""")
        )
        for (pattern in patterns) {
            pattern.find(url)?.let { return it.groupValues[1] }
        }
        return null
    }

    /**
     * Récupère les informations de la vidéo selon la plateforme
     */
    fun getVideoInfo(url: String): VideoInfo? {
        val platform = detectPlatform(url)
        return when (platform) {
            Platform.DAILYMOTION -> getDailymotionInfo(url)
            Platform.TIKTOK -> getTikTokInfo(url)
            Platform.TWITTER -> getTwitterInfo(url)
            Platform.YOUTUBE -> getYouTubeInfo(url)
            Platform.UNKNOWN -> null
        }
    }

    // ==================== DAILYMOTION ====================

    private fun getDailymotionInfo(url: String): VideoInfo? {
        return try {
            val videoId = extractDailymotionId(url) ?: return null
            val playerUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val request = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.has("error")) return null

            val title = json.optString("title", "Video_$videoId")
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

            val downloadUrl = getBestQualityFromM3u8(m3u8Url) ?: m3u8Url
            VideoInfo(videoId, title, downloadUrl, Platform.DAILYMOTION)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getBestQualityFromM3u8(m3u8Url: String): String? {
        return try {
            val request = Request.Builder()
                .url(m3u8Url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: return null

            val lines = content.lines()
            var bestUrl: String? = null
            var bestBandwidth = 0L

            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                    val bandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0

                    if (i + 1 < lines.size) {
                        val urlLine = lines[i + 1].trim()
                        if (urlLine.isNotEmpty() && !urlLine.startsWith("#")) {
                            if (bandwidth > bestBandwidth) {
                                bestBandwidth = bandwidth
                                bestUrl = if (urlLine.startsWith("http")) {
                                    urlLine
                                } else {
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

    // ==================== TIKTOK ====================

    private fun getTikTokInfo(url: String): VideoInfo? {
        return try {
            // Résoudre les liens courts
            val resolvedUrl = resolveShortUrl(url)

            // Extraire l'ID de la vidéo
            val videoId = Regex("""video/(\d+)""").find(resolvedUrl)?.groupValues?.get(1)
                ?: return null

            // Utiliser l'API oEmbed de TikTok pour le titre
            val oembedUrl = "https://www.tiktok.com/oembed?url=$resolvedUrl"
            val oembedRequest = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val oembedResponse = client.newCall(oembedRequest).execute()
            val oembedBody = oembedResponse.body?.string()
            val title = if (oembedBody != null) {
                try {
                    val json = JSONObject(oembedBody)
                    json.optString("title", "TikTok_$videoId").take(100)
                } catch (e: Exception) {
                    "TikTok_$videoId"
                }
            } else {
                "TikTok_$videoId"
            }

            // Utiliser un service tiers pour obtenir l'URL de téléchargement
            val downloadUrl = getTikTokDownloadUrl(resolvedUrl, videoId)
                ?: return null

            VideoInfo(videoId, title, downloadUrl, Platform.TIKTOK)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getTikTokDownloadUrl(url: String, videoId: String): String? {
        return try {
            // Méthode 1: Utiliser tikwm.com API
            val apiUrl = "https://www.tikwm.com/api/"
            val formBody = FormBody.Builder()
                .add("url", url)
                .add("hd", "1")
                .build()

            val request = Request.Builder()
                .url(apiUrl)
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optInt("code") == 0) {
                val data = json.optJSONObject("data")
                // Préférer HD, sinon version normale
                data?.optString("hdplay")?.takeIf { it.isNotEmpty() }
                    ?: data?.optString("play")?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== TWITTER / X ====================

    private fun getTwitterInfo(url: String): VideoInfo? {
        return try {
            val tweetId = extractTwitterId(url) ?: return null

            // Utiliser un service tiers pour Twitter
            val downloadUrl = getTwitterDownloadUrl(url, tweetId)
                ?: return null

            val title = "Twitter_$tweetId"
            VideoInfo(tweetId, title, downloadUrl, Platform.TWITTER)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getTwitterDownloadUrl(url: String, tweetId: String): String? {
        return try {
            // Utiliser twitsave.com API
            val apiUrl = "https://twitsave.com/info?url=$url"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            // Parser le HTML pour trouver l'URL de la vidéo
            val videoUrlPattern = Regex("""<a[^>]*href="(https://[^"]*twimg\.com/[^"]*\.mp4[^"]*)"[^>]*>""")
            val match = videoUrlPattern.find(html)

            if (match != null) {
                match.groupValues[1]
            } else {
                // Essayer avec une autre méthode
                val altPattern = Regex("""(https://video\.twimg\.com/[^"'\s]+\.mp4[^"'\s]*)""")
                altPattern.find(html)?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== YOUTUBE ====================

    private fun getYouTubeInfo(url: String): VideoInfo? {
        return try {
            val videoId = extractYouTubeId(url) ?: return null

            // Récupérer les informations via la page embed
            val infoUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val infoRequest = Request.Builder()
                .url(infoUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val infoResponse = client.newCall(infoRequest).execute()
            val infoBody = infoResponse.body?.string()

            val title = if (infoBody != null) {
                try {
                    val json = JSONObject(infoBody)
                    json.optString("title", "YouTube_$videoId")
                } catch (e: Exception) {
                    "YouTube_$videoId"
                }
            } else {
                "YouTube_$videoId"
            }

            // Utiliser un service tiers pour obtenir l'URL de téléchargement
            val downloadUrl = getYouTubeDownloadUrl(videoId)
                ?: return null

            VideoInfo(videoId, title, downloadUrl, Platform.YOUTUBE)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getYouTubeDownloadUrl(videoId: String): String? {
        return try {
            // Méthode: Utiliser l'API de y2mate ou similaire
            // Note: Ces APIs peuvent changer, il faudra les mettre à jour régulièrement

            val apiUrl = "https://api.vevioz.com/api/button/videos/$videoId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            // Parser pour trouver le lien de téléchargement MP4
            val patterns = listOf(
                Regex("""href="(https://[^"]+)"[^>]*>720p"""),
                Regex("""href="(https://[^"]+)"[^>]*>480p"""),
                Regex("""href="(https://[^"]+)"[^>]*>360p"""),
                Regex("""(https://[^"'\s]+\.googlevideo\.com/[^"'\s]+)""")
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    return match.groupValues[1]
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== UTILITAIRES ====================

    private fun resolveShortUrl(url: String): String {
        return try {
            if (!url.contains("vm.tiktok.com") && !url.contains("tiktok.com/t/")) {
                return url
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            response.request.url.toString()
        } catch (e: Exception) {
            url
        }
    }
}
