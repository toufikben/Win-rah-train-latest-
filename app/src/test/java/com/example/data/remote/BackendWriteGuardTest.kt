package com.example.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendWriteGuardTest {
    @Test
    fun blockedWriteProducesHttp403ResponseWithoutThrowing() {
        val request = Request.Builder()
            .url("https://train-api-uep7.onrender.com/monitor-sessions")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        val response = blockedWriteResponse(request, "production writes disabled")

        assertEquals(403, response.code)
        assertEquals(request, response.request)
        assertTrue(response.body?.string()?.contains("production writes disabled") == true)
    }

}
