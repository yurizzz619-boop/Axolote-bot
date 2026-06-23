package com.example

import com.example.api.GeminiApiService
import com.example.api.MoshiContent
import com.example.api.MoshiGenerateContentRequest
import com.example.api.MoshiGenerationConfig
import com.example.api.MoshiPart
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }
}

