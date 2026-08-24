package com.example.data.remote

/**
 * Runtime policy for selecting API environments and protecting write calls.
 *
 * Production is always read-only from the client. Writes require both an
 * explicit non-production environment and an explicit build-time opt-in.
 */
data class EndpointPolicy(
    val baseUrl: String,
    val environment: String,
    val writesEnabled: Boolean,
) {
    val isProductionEndpoint: Boolean
        get() = baseUrl.trimEnd('/') == BackendService.PRODUCTION_BASE_URL.trimEnd('/')

    val canWrite: Boolean
        get() = writesEnabled && !isProductionEndpoint && environment.lowercase() != "production"

    fun allows(method: String): Boolean {
        return method.uppercase() in READ_METHODS || canWrite
    }

    companion object {
        private val READ_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }
}
