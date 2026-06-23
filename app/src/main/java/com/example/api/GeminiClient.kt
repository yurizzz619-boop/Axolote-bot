package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class MoshiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class MoshiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: MoshiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class MoshiContent(
    @Json(name = "role") val role: String? = null,
    @Json(name = "parts") val parts: List<MoshiPart>
)

@JsonClass(generateAdapter = true)
data class MoshiImageConfig(
    @Json(name = "aspectRatio") val aspectRatio: String,
    @Json(name = "imageSize") val imageSize: String
)

@JsonClass(generateAdapter = true)
data class MoshiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "imageConfig") val imageConfig: MoshiImageConfig? = null,
    @Json(name = "responseModalities") val responseModalities: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class MoshiGenerateContentRequest(
    @Json(name = "contents") val contents: List<MoshiContent>,
    @Json(name = "generationConfig") val generationConfig: MoshiGenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: MoshiContent? = null
)

@JsonClass(generateAdapter = true)
data class MoshiCandidate(
    @Json(name = "content") val content: MoshiContent
)

@JsonClass(generateAdapter = true)
data class MoshiGenerateContentResponse(
    @Json(name = "candidates") val candidates: List<MoshiCandidate>? = null
)

interface GeminiApiService {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: MoshiGenerateContentRequest
    ): MoshiGenerateContentResponse

    @POST
    suspend fun generateImage(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: MoshiGenerateContentRequest
    ): MoshiGenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}
