package com.applinks.android.handlers

import android.content.Context
import android.net.Uri
import java.util.Date

/**
 * Result of link handling process
 */
data class LinkHandlingResult(
    val handled: Boolean,
    val originalUrl: Uri,
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
    var additionalData: MutableMap<String, Any> = mutableMapOf()
)

/**
 * Base interface for middleware that processes links
 */
interface Middleware {
    /**
     * Process the link and modify the context
     * @param context The current context
     * @param uri The original URI being processed
     * @param androidContext The Android context
     * @param next Callback to continue to the next middleware in the chain
     */
    suspend fun process(context: LinkHandlingContext, uri: Uri, androidContext: Context, next: suspend (LinkHandlingContext) -> LinkHandlingContext): LinkHandlingContext
}