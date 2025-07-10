package com.applinks.android.handlers

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Manages a chain of middlewares that process links sequentially
 */
class MiddlewareChain(
    private val context: Context,
    private val enableLogging: Boolean = true
) {
    
    companion object {
        private const val TAG = "MiddlewareChain"
    }
    
    private val middlewares = mutableListOf<Middleware>()
    
    /**
     * Add a middleware to the chain
     */
    fun addMiddleware(middleware: Middleware) {
        middlewares.add(middleware)
        
        if (enableLogging) {
            Log.d(TAG, "Added middleware: ${middleware.javaClass.simpleName}")
        }
    }
    
    /**
     * Remove a middleware from the chain
     */
    fun removeMiddleware(middleware: Middleware) {
        middlewares.remove(middleware)
    }
    
    /**
     * Clear all middlewares
     */
    fun clearMiddlewares() {
        middlewares.clear()
    }
    
    /**
     * Process a URI through the middleware chain
     */
    suspend fun processLink(uri: Uri, initialContext: LinkHandlingContext): LinkHandlingResult {
        if (enableLogging) {
            Log.d(TAG, "Processing link through middleware chain: $uri")
        }
        
        try {
            val finalContext = processMiddlewares(0, initialContext, uri)
            
            // Create final result from processed context
            return LinkHandlingResult(
                handled = true,
                originalUrl = uri,
                path = finalContext.deepLinkPath ?: "",
                params = finalContext.deepLinkParams,
                metadata = finalContext.additionalData
            )
            
        } catch (e: Exception) {
            val error = "Error processing link through middleware chain: ${e.message}"
            if (enableLogging) {
                Log.e(TAG, error, e)
            }
            return LinkHandlingResult(
                handled = false,
                originalUrl = uri,
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
        if (enableLogging) {
            Log.d(TAG, "Processing with middleware: ${middleware.javaClass.simpleName}")
        }
        
        return middleware.process(currentContext, uri, context) { nextContext ->
            processMiddlewares(index + 1, nextContext, uri)
        }
    }
    
    /**
     * Get all registered middlewares
     */
    fun getMiddlewares(): List<Middleware> {
        return middlewares.toList()
    }
}