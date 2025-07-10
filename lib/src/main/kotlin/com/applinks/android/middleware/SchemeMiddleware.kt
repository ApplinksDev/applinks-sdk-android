package com.applinks.android.middleware

import android.content.Context
import android.net.Uri
import android.util.Log
import com.applinks.android.handlers.LinkHandlingContext
import com.applinks.android.handlers.Middleware

/**
 * Middleware that handles custom scheme links (e.g., sweepy://path?foo=bar)
 */
class SchemeMiddleware(
    private val supportedSchemes: Set<String>,
    private val enableLogging: Boolean = true
) : Middleware {
    
    companion object {
        private const val TAG = "SchemeMiddleware"
    }
    
    override suspend fun process(
        context: LinkHandlingContext, 
        uri: Uri, 
        androidContext: Context, 
        next: suspend (LinkHandlingContext) -> LinkHandlingContext
    ): LinkHandlingContext {
        if (!isCustomScheme(uri)) {
            // Not a custom scheme, continue to next middleware
            return next(context)
        }
        
        if (enableLogging) {
            Log.d(TAG, "Processing custom scheme link: $uri")
        }
        
        // Parse the custom scheme link
        context.deepLinkPath = uri.path ?: ""
        context.additionalData["link_type"] = "custom_scheme"
        context.additionalData["scheme"] = uri.scheme ?: ""
        context.additionalData["host"] = uri.host ?: ""
        
        // Extract query parameters
        uri.queryParameterNames.forEach { param ->
            uri.getQueryParameter(param)?.let { value ->
                context.deepLinkParams[param] = value
            }
        }
        
        // Parse path segments for easier routing
        uri.pathSegments?.forEachIndexed { index, segment ->
            context.additionalData["path_segment_$index"] = segment
        }
        
        if (enableLogging) {
            Log.d(TAG, "Custom scheme link processed - path: ${context.deepLinkPath}, params: ${context.deepLinkParams}")
        }
        
        return next(context)
    }
    
    private fun isCustomScheme(uri: Uri): Boolean {
        return uri.scheme != null && uri.scheme in supportedSchemes
    }
}