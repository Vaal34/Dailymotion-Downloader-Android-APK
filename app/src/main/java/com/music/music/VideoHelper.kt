package com.music.music

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONObject
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

    private var isYtDlpInitialized = false

    /**
     * Initialise yt-dlp (doit être appelé une fois au démarrage)
     */
    fun initYtDlp(context: Context) {
        if (!isYtDlpInitialized) {
            try {
                YoutubeDL.getInstance().init(context)
                isYtDlpInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Met à jour yt-dlp vers la dernière version
     */
    suspend fun updateYtDlp(context: Context) {
        try {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        return url.hashCode().toString()
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

        // Pour YouTube et Twitter, utiliser yt-dlp
        if (platform == Platform.YOUTUBE || platform == Platform.TWITTER) {
            return getVideoInfoWithYtDlp(url, platform)
        }

        // Pour les autres plateformes, utiliser les méthodes existantes
        return when (platform) {
            Platform.DAILYMOTION -> getDailymotionInfo(url)
            Platform.TIKTOK -> getTikTokInfo(url)
            else -> null
        }
    }

    /**
     * Utilise yt-dlp pour récupérer les infos de la vidéo
     */
    private fun getVideoInfoWithYtDlp(url: String, platform: Platform): VideoInfo? {
        // D'abord essayer les méthodes de fallback qui sont plus rapides
        val fallbackResult = when (platform) {
            Platform.YOUTUBE -> getYouTubeInfoFallback(url)
            Platform.TWITTER -> getTwitterInfoFallback(url)
            else -> null
        }

        if (fallbackResult != null) {
            return fallbackResult
        }

        // Si les fallbacks échouent, essayer yt-dlp
        return try {
            if (!isYtDlpInitialized) return null

            val request = YoutubeDLRequest(url)
            request.addOption("-f", "best[ext=mp4]/best")

            val streamInfo = YoutubeDL.getInstance().getInfo(request)

            val videoId = streamInfo.id ?: url.hashCode().toString()
            val title = streamInfo.title ?: "${platform.name}_$videoId"
            val downloadUrl = streamInfo.url ?: return null

            VideoInfo(videoId, title, downloadUrl, platform)
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
                ?: resolvedUrl.hashCode().toString()

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

            // Utiliser tikwm.com API
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

    // ==================== YOUTUBE FALLBACK ====================

    private fun getYouTubeInfoFallback(url: String): VideoInfo? {
        return try {
            val videoId = extractYouTubeId(url) ?: return null

            // Récupérer le titre via oEmbed
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

            // Essayer avec des services tiers
            val downloadUrl = getYouTubeDownloadUrlFallback(videoId) ?: return null

            VideoInfo(videoId, title, downloadUrl, Platform.YOUTUBE)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getYouTubeDownloadUrlFallback(videoId: String): String? {
        // Méthode 1: ssyoutube.com (savefrom)
        try {
            val request = Request.Builder()
                .url("https://ssyoutube.com/watch?v=$videoId")
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // Chercher les liens de téléchargement
            val patterns = listOf(
                Regex("""href="(https://[^"]+)"[^>]*class="[^"]*download[^"]*"[^>]*>""", RegexOption.IGNORE_CASE),
                Regex(""""url"\s*:\s*"(https://[^"]+googlevideo[^"]+)""""),
                Regex("""(https://r[0-9]+---[a-z0-9-]+\.googlevideo\.com/videoplayback[^"'\s]+)""")
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val url = match.groupValues[1].replace("\\u0026", "&")
                    if (url.contains("googlevideo.com")) {
                        return url
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Méthode 2: y2mate.com
        try {
            val analyzeBody = FormBody.Builder()
                .add("url", "https://www.youtube.com/watch?v=$videoId")
                .add("q_auto", "0")
                .add("ajax", "1")
                .build()

            val analyzeRequest = Request.Builder()
                .url("https://www.y2mate.com/mates/analyzeV2/ajax")
                .post(analyzeBody)
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val analyzeResponse = client.newCall(analyzeRequest).execute()
            val analyzeJson = analyzeResponse.body?.string() ?: ""

            val json = JSONObject(analyzeJson)
            if (json.optString("status") == "ok") {
                val links = json.optJSONObject("links")
                val mp4 = links?.optJSONObject("mp4")
                if (mp4 != null) {
                    // Chercher la meilleure qualité disponible
                    val qualities = listOf("720", "480", "360", "240")
                    for (quality in qualities) {
                        val item = mp4.optJSONObject(quality)
                        if (item != null) {
                            val k = item.optString("k", null)
                            if (k != null) {
                                val convertUrl = getY2mateConvertUrl(videoId, k)
                                if (convertUrl != null) return convertUrl
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Méthode 3: yt1s.com
        try {
            val formBody = FormBody.Builder()
                .add("url", "https://www.youtube.com/watch?v=$videoId")
                .build()

            val request = Request.Builder()
                .url("https://www.yt1s.com/api/ajaxSearch/index")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            if (json.optString("status") == "ok") {
                val links = json.optJSONObject("links")
                val mp4 = links?.optJSONObject("mp4")
                if (mp4 != null) {
                    val keys = mp4.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val item = mp4.optJSONObject(key)
                        val quality = item?.optString("q", "")
                        if (quality == "720p" || quality == "480p" || quality == "360p") {
                            val k = item?.optString("k", null)
                            if (k != null) {
                                val convertUrl = getYt1sConvertUrl(videoId, k)
                                if (convertUrl != null) return convertUrl
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun getY2mateConvertUrl(videoId: String, k: String): String? {
        return try {
            val formBody = FormBody.Builder()
                .add("vid", videoId)
                .add("k", k)
                .build()

            val request = Request.Builder()
                .url("https://www.y2mate.com/mates/convertV2/index")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optString("status") == "ok") {
                json.optString("dlink", null)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getYt1sConvertUrl(videoId: String, k: String): String? {
        return try {
            val formBody = FormBody.Builder()
                .add("vid", videoId)
                .add("k", k)
                .build()

            val request = Request.Builder()
                .url("https://www.yt1s.com/api/ajaxConvert/convert")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optString("status") == "ok") {
                json.optString("dlink", null)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== TWITTER FALLBACK ====================

    private fun getTwitterInfoFallback(url: String): VideoInfo? {
        return try {
            val tweetId = extractTwitterId(url) ?: return null

            val downloadUrl = getTwitterDownloadUrlFallback(url) ?: return null

            val title = "Twitter_$tweetId"
            VideoInfo(tweetId, title, downloadUrl, Platform.TWITTER)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getTwitterDownloadUrlFallback(url: String): String? {
        // Méthode 1: twdown.net
        try {
            val formBody = FormBody.Builder()
                .add("URL", url)
                .build()

            val request = Request.Builder()
                .url("https://twdown.net/download.php")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://twdown.net")
                .header("Referer", "https://twdown.net/")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val patterns = listOf(
                Regex("""href="(https://[^"]+\.mp4[^"]*)"[^>]*>.*?Download""", RegexOption.IGNORE_CASE),
                Regex("""(https://video\.twimg\.com/[^"'\s]+\.mp4[^"'\s]*)"""),
                Regex("""(https://[^"'\s]+twimg[^"'\s]+\.mp4[^"'\s]*)""")
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Méthode 2: ssstwitter.com
        try {
            val formBody = FormBody.Builder()
                .add("id", url)
                .add("locale", "en")
                .build()

            val request = Request.Builder()
                .url("https://ssstwitter.com/")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://ssstwitter.com")
                .header("Referer", "https://ssstwitter.com/")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val patterns = listOf(
                Regex("""href="(https://[^"]+\.mp4[^"]*)"[^>]*>.*?HD""", RegexOption.IGNORE_CASE),
                Regex("""href="(https://[^"]+\.mp4[^"]*)"""),
                Regex("""(https://video\.twimg\.com/[^"'\s]+\.mp4[^"'\s]*)""")
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Méthode 3: twitsave.com
        try {
            val request = Request.Builder()
                .url("https://twitsave.com/info?url=$url")
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val patterns = listOf(
                Regex("""href="(https://[^"]*twimg\.com/[^"]*\.mp4[^"]*)"[^>]*>"""),
                Regex("""(https://video\.twimg\.com/[^"'\s]+\.mp4[^"'\s]*)""")
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Méthode 4: twittervideodownloader.com
        try {
            val formBody = FormBody.Builder()
                .add("tweet", url)
                .build()

            val request = Request.Builder()
                .url("https://twittervideodownloader.com/download")
                .post(formBody)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val pattern = Regex("""(https://video\.twimg\.com/[^"'\s]+\.mp4[^"'\s]*)""")
            val match = pattern.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
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
