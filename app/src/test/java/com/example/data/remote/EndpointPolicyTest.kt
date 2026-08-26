package com.example.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {
    @Test
    fun production_requires_explicit_write_opt_in() {
        val policy = EndpointPolicy(
            baseUrl = BackendService.PRODUCTION_BASE_URL,
            environment = "production",
            writesEnabled = true,
        )

        assertTrue(policy.allows("GET"))
        assertTrue(policy.canWrite)
        assertTrue(policy.allows("POST"))
        assertTrue(policy.allows("PUT"))
        assertTrue(policy.allows("DELETE"))
    }

    @Test
    fun staging_requires_explicit_write_opt_in() {
        val readOnly = EndpointPolicy(
            baseUrl = "https://staging.example.invalid/",
            environment = "staging",
            writesEnabled = false,
        )
        val writeEnabled = readOnly.copy(writesEnabled = true)

        assertTrue(readOnly.allows("GET"))
        assertFalse(readOnly.canWrite)
        assertFalse(readOnly.allows("POST"))
        assertTrue(writeEnabled.canWrite)
        assertTrue(writeEnabled.allows("POST"))
    }

    @Test
    fun local_can_write_only_when_explicitly_enabled() {
        val policy = EndpointPolicy(
            baseUrl = "http://127.0.0.1:18080/",
            environment = "local",
            writesEnabled = true,
        )

        assertTrue(policy.isProductionEndpoint.not())
        assertTrue(policy.canWrite)
        assertTrue(policy.allows("POST"))
    }

    @Test
    fun production_is_read_only_without_explicit_opt_in() {
        val policy = EndpointPolicy(
            baseUrl = BackendService.PRODUCTION_BASE_URL,
            environment = "production",
            writesEnabled = false,
        )

        assertFalse(policy.canWrite)
        assertFalse(policy.allows("POST"))
    }

    @Test
    fun unsupported_environment_cannot_write_even_when_enabled() {
        val policy = EndpointPolicy(
            baseUrl = BackendService.PRODUCTION_BASE_URL,
            environment = "sandbox",
            writesEnabled = true,
        )

        assertFalse(policy.canWrite)
        assertFalse(policy.allows("POST"))
    }
}
