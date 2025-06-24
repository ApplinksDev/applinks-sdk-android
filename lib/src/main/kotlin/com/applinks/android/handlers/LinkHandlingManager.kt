package com.applinks.android.handlers

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Manages multiple link handlers and routes links to the appropriate handler
 */
class LinkHandlingManager(
    private val context: Context,
    private val enableLogging: Boolean = true
) {
    
    companion object {
        private const val TAG = "LinkHandlingManager"
    }
    
    private val handlers = mutableListOf<LinkHandler>()
    
    /**
     * Add a handler to the manager
     */
    fun addHandler(handler: LinkHandler) {
        handlers.add(handler)
        // Sort by priority (highest first)
        handlers.sortByDescending { it.getPriority() }
        
        if (enableLogging) {
            Log.d(TAG, "Added handler: ${handler.javaClass.simpleName} with priority ${handler.getPriority()}")
        }
    }
    
    /**
     * Remove a handler from the manager
     */
    fun removeHandler(handler: LinkHandler) {
        handlers.remove(handler)
    }
    
    /**
     * Clear all handlers
     */
    fun clearHandlers() {
        handlers.clear()
    }
    
    /**
     * Process a URI with the appropriate handler
     */
    suspend fun handleLink(uri: Uri, callback: LinkHandlerCallback) {
        if (enableLogging) {
            Log.d(TAG, "Processing link: $uri")
        }
        
        // Find the first handler that can handle this URI
        val handler = handlers.firstOrNull { it.canHandle(uri) }
        
        if (handler != null) {
            if (enableLogging) {
                Log.d(TAG, "Found handler: ${handler.javaClass.simpleName} for URI: $uri")
            }
            handler.handle(context, uri, callback)
        } else {
            val error = "No handler found for URI: $uri"
            if (enableLogging) {
                Log.w(TAG, error)
            }
            callback.onError(error)
        }
    }
    
    /**
     * Check if any handler can handle the given URI
     */
    fun canHandle(uri: Uri): Boolean {
        return handlers.any { it.canHandle(uri) }
    }
    
    /**
     * Get all registered handlers
     */
    fun getHandlers(): List<LinkHandler> {
        return handlers.toList()
    }
}