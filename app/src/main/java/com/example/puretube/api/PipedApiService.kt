package com.example.puretube.api

import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
    val title: String = ""
)

interface PipedApiService {
    @GET("trending?region=US")
    suspend fun getTrending(): List<TrendingResponse>

    @GET("streams/{videoId}")
    suspend fun getVideoStream(@Path("videoId") videoId: String): VideoStreamResponse
}

object PipedApiClient {
    private const val BASE_URL = "https://pipedapi.kavin.rocks/"

    private val json = Json { ignoreUnknownKeys = true }

    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api = retrofit.create(PipedApiService::class.java)
}
