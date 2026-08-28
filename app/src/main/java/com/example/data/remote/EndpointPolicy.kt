package com.example.data.remote

/**
 * Runtime policy for selecting API environments and protecting write calls.
 *
 * Writes are disabled by default. A release may enable writes only through the
 * explicit build-time opt-in WINRAH_API_WRITES_ENABLED=true; this keeps accidental
 * writes blocked while allowing the real production app to submit monitor data
 * and user reports when the release configuration is intentional.
 */
data class EndpointPolicy(
    val baseUrl: String,
    val environment: String,
    val writesEnabled: Boolean,
) {
    val isProductionEndpoint: Boolean
        get() = baseUrl.trimEnd('/') == BackendService.PRODUCTION_BASE_URL.trimEnd('/')

    val canWrite: Boolean
        get() = writesEnabled && environment.lowercase() in setOf("local", "staging", "production")

    fun allows(method: String): Boolean {
        return method.uppercase() in READ_METHODS || canWrite
    }

    companion object {
        private val READ_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }
}
