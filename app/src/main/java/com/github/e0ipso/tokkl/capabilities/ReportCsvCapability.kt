package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.ApiError

/**
 * The CSV routes answer POST only. A 404 or 410 from that POST is the endpoint missing.
 * Connectivity, authentication, trust and rate-limit failures are not.
 */
internal fun ApiError.csvEndpointAbsent(): Boolean = this is ApiError.UnexpectedStatus && status in setOf(404, 410)
