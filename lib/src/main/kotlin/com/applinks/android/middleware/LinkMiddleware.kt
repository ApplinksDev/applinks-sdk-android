package com.applinks.android.middleware

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Manages a chain of middlewares that process links sequentially
 */
class MiddlewareChain(
    private val context: Context,
) {
    
    companion object {
        private const val TAG = "MiddlewareChain"
    }
    
    private val middlewares = mutableListOf<LinkMiddleware>()
    
    /**
     * Add a middleware to the chain
     */
    fun addMiddleware(middleware: LinkMiddleware) {
        middlewares.add(middleware)
        
        Log.d(TAG, "Added middleware: ${middleware.javaClass.simpleName}")
    }
    
    /**
     * Remove a middleware from the chain
     */
    fun removeMiddleware(middleware: LinkMiddleware) {
        middlewares.remove(middleware)
    }
    
    /**
     * Clear all middlewares
     */
    fun clearMiddlewares() {
        middlewares.clear()
    }

    fun canHandle(uri: Uri): Boolean {
        return middlewares.any { it.canHandle(uri) }
    }
    
    /**
     * Process a URI through the middleware chain
     */
    suspend fun processLink(uri: Uri, initialContext: LinkHandlingContext): LinkHandlingResult {
        Log.d(TAG, "Processing link through middleware chain: $uri")
        
        try {
            val finalContext = processMiddlewares(0, initialContext, uri)
            
            // Create final result from processed context
            return LinkHandlingResult(
                handled = true,
                originalUrl = uri,
                schemeUrl = finalContext.schemeUrl ?: uri,
                path = finalContext.deepLinkPath ?: "",
                params = finalContext.deepLinkParams,
                metadata = finalContext.additionalData
            )
            
        } catch (e: Exception) {
            val error = "Error processing link through middleware chain: ${e.message}"
            Log.e(TAG, error, e)

            return LinkHandlingResult(
                handled = false,
                originalUrl = uri,
                schemeUrl = uri,
                path = "",
                error = error
            )
        }
    }
    
    /**
     * Recursively process middlewares with next callback
     */
    private suspend fun processMiddlewares(index: Int, currentContext: LinkHandlingContext, uri: Uri): LinkHandlingContext {
        if (index >= middlewares.size) {
            return currentContext
        }
        
        val middleware = middlewares[index]
        Log.d(TAG, "Processing with middleware: ${middleware.javaClass.simpleName}")

        return middleware.process(currentContext, uri, context) { nextContext ->
            processMiddlewares(index + 1, nextContext, uri)
        }
    }
    
    /**
     * Get all registered middlewares
     */
    fun getMiddlewares(): List<LinkMiddleware> {
        return middlewares.toList()
    }
}

/**
 * Base interface for middleware that processes links
 */
interface LinkMiddleware {
    /**
     * Process the link and modify the context
     * @param context The current context
     * @param uri The original URI being processed
     * @param androidContext The Android context
     * @param next Callback to continue to the next middleware in the chain
     */
    suspend fun process(context: LinkHandlingContext, uri: Uri, androidContext: Context, next: suspend (LinkHandlingContext) -> LinkHandlingContext): LinkHandlingContext

    /**
     * Returns whether this middleware can handle the given uri
     * @param uri the URI to handle
     */
    fun canHandle(uri: Uri): Boolean
}