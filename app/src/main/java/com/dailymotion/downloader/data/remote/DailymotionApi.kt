package com.dailymotion.downloader.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DailymotionApi {
    @GET("video/{videoId}")
    suspend fun getVideoInfo(
        @Path("videoId") videoId: String,
        @Query("fields") fields: String = "id,title,thumbnail_url,qualities"
    ): VideoInfoResponse
}

data class VideoInfoResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String?,
    @SerializedName("qualities")
    val qualities: Map<String, List<QualityInfo>>?
)

data class QualityInfo(
    @SerializedName("type")
    val type: String,
    @SerializedName("url")
    val url: String
)
