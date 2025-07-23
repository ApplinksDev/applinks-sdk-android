package com.applinks.android.middleware

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Middleware that logs the start and end of link processing
 */
class LoggingMiddleware : LinkMiddleware {
    
    companion object {
        private const val TAG = "LoggingMiddleware"
    }
    
    override suspend fun process(
        context: LinkHandlingContext,
        uri: Uri,
        androidContext: Context,
        next: suspend (LinkHandlingContext) -> LinkHandlingContext
    ): LinkHandlingContext {
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "Starting link processing: $uri")
        
        // Add processing metadata to context
        context.additionalData["processing_started"] = startTime
        
        // Continue to next middleware
        val result = next(context)
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        Log.d(TAG, "Finished link processing: $uri (took ${duration}ms)")
        
        // Add completion metadata
        result.additionalData["processing_completed"] = endTime
        result.additionalData["processing_duration_ms"] = duration
        
        return result
    }

    override fun canHandle(uri: Uri): Boolean {
        return false
    }
}