package com.applinks.android.handlers

import android.content.Context
import android.net.Uri

/**
 * Base interface for handling different types of links
 */
interface LinkHandler {
    /**
     * Check if this handler can process the given URI
     */
    fun canHandle(uri: Uri): Boolean
    
    /**
     * Handle the link and return whether it was successfully handled
     */
    suspend fun handle(context: Context, uri: Uri, callback: LinkHandlerCallback)
    
    /**
     * Get the priority of this handler (higher values are tried first)
     */
    fun getPriority(): Int
}

/**
 * Callback for link handling results
 */
interface LinkHandlerCallback {
    fun onLinkHandled(uri: Uri, metadata: Map<String, String>)
    fun onError(error: String)
}

/**
 * Data class for link handling result
 */
data class LinkHandlingResult(
    val handled: Boolean,
    val uri: Uri,
    val metadata: Map<String, String> = emptyMap(),
    val error: String? = null
)

/**
 * Context for link handling that can be passed between handlers
 */
data class LinkHandlingContext(
    val isFirstLaunch: Boolean = false,
    val launchTimestamp: Long = System.currentTimeMillis(),
    val additionalData: Map<String, Any> = emptyMap()
)