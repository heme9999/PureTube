package com.example.puretube.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class TrendingResponse(
    val title: String = "",
    val url: String = "",
    val uploaderName: String = "",
    val thumbnail: String = ""
)

@Serializable
data class VideoStreamResponse(
    val hls: String? = null,
    val title: String = "",
    val videoStreams: List<VideoStream> = emptyList()
)

@Serializable
data class VideoStream(
    val url: String = "",
    val format: String = "",
    val quality: String = ""
)

interface PipedApiService {
    @GET("trending?region=US")
    suspend fun getTrending(): List<TrendingResponse>

    @GET("streams/{videoId}")
    suspend fun getVideoStream(@Path("videoId") videoId: String): VideoStreamResponse
}
