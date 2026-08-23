package com.example.data.remote

import dz.winrah.trainradar.BuildConfig
import com.example.data.remote.dto.MonitorSessionDto
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object BackendService {
    const val PRODUCTION_BASE_URL = "https://train-api-uep7.onrender.com/"
    val BASE_URL: String = BuildConfig.WINRAH_API_BASE_URL
    val endpointPolicy: EndpointPolicy = EndpointPolicy(
        baseUrl = BASE_URL,
        environment = BuildConfig.WINRAH_API_ENVIRONMENT,
        writesEnabled = BuildConfig.WINRAH_API_WRITES_ENABLED,
    )

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val writeGuardInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (!endpointPolicy.allows(request.method)) {
            throw IllegalStateException(
                "Write request blocked: environment=${endpointPolicy.environment}, " +
                    "baseUrl=${endpointPolicy.baseUrl}"
            )
        }
        chain.proceed(request)
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(writeGuardInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val api: TrainApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TrainApi::class.java)
}
