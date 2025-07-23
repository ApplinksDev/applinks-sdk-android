package com.applinks.android.middleware

import android.content.Context
import android.net.Uri
import java.util.Date

/**
 * Result of link handling process
 */
data class LinkHandlingResult(
    val handled: Boolean,
    val originalUrl: Uri,
    val schemeUrl: Uri?,
    val path: String,
    val params: Map<String, String> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap(),
    val error: String? = null
)

/**
 * Context for link handling that can be passed between middlewares
 */
data class LinkHandlingContext(
    val isFirstLaunch: Boolean = false,
    val launchTimestamp: Date = Date(),
    var deepLinkPath: String? = null,
    var deepLinkParams: MutableMap<String, String> = mutableMapOf(),
    var additionalData: MutableMap<String, Any> = mutableMapOf(),
    var schemeUrl: Uri? = null
)