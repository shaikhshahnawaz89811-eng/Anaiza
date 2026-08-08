package com.aidesktop.os.di

import com.aidesktop.os.BuildConfig
import com.aidesktop.os.data.remote.GitHubApiService
import com.aidesktop.os.data.remote.GroqApiService
import com.aidesktop.os.data.remote.PistonApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Body-level logging only in debug builds — never log the Authorization header
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGroqApiService(client: OkHttpClient): GroqApiService =
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)

    @Provides
    @Singleton
    fun providePistonApiService(client: OkHttpClient): PistonApiService =
        Retrofit.Builder()
            .baseUrl("https://emkc.org/api/v2/piston/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PistonApiService::class.java)

    @Provides
    @Singleton
    fun provideGitHubApiService(client: OkHttpClient): GitHubApiService =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
}
