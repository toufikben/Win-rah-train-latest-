package com.example.data.remote

import dz.winrah.trainradar.BuildConfig
import com.example.data.remote.dto.MonitorSessionDto
import com.example.data.remote.dto.ObservationRequest
import com.example.data.remote.dto.ReportRequest
import com.example.data.local.PersistentAppLogger
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

internal fun blockedWriteResponse(request: okhttp3.Request, message: String): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(403)
        .message("Write blocked by client policy")
        .body("{\"error\":\"$message\"}".toResponseBody("application/json".toMediaType()))
        .build()

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
            val message = "Write request blocked: environment=${endpointPolicy.environment}, " +
                "baseUrl=${endpointPolicy.baseUrl}"
            // Never throw from an OkHttp worker thread: Retrofit can convert this
            // response into a normal HttpException that the caller can handle.
            PersistentAppLogger.write("WRITE_BLOCKED method=${request.method}")
            return@Interceptor blockedWriteResponse(request, message)
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
