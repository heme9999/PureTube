package com.example.puretube.updater

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val assets: List<Asset>
) {
    @Serializable
    data class Asset(
        val browser_download_url: String,
        val name: String
    )
}

interface GitHubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

object GitHubUpdater {
    private val json = Json { ignoreUnknownKeys = true }
    
    val api = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApiService::class.java)
        
    suspend fun checkForUpdates(owner: String, repo: String, currentVersion: String): String? {
        return try {
            val release = api.getLatestRelease(owner, repo)
            if (release.tag_name != currentVersion) {
                release.assets.firstOrNull { it.name.endsWith(".apk") }?.browser_download_url
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
