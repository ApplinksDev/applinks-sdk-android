package com.applinks.android.middleware

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Middleware that handles custom scheme links (e.g., sweepy://path?foo=bar)
 */
class SchemeMiddleware(
    private val supportedSchemes: Set<String>,
) : LinkMiddleware {
    
    companion object {
        private const val TAG = "SchemeMiddleware"
    }
    
    override suspend fun process(
        context: LinkHandlingContext,
        uri: Uri,
        androidContext: Context,
        next: suspend (LinkHandlingContext) -> LinkHandlingContext
    ): LinkHandlingContext {
        if (!canHandle(uri)) {
            // Not a custom scheme, continue to next middleware
            return next(context)
        }
        
        Log.d(TAG, "Processing custom scheme link: $uri")

        // Parse the custom scheme link
        // Include host in the path for custom schemes (e.g., myapp://host/path -> host/path)
        val host = uri.host ?: ""
        val path = uri.path ?: ""
        context.deepLinkPath = if (host.isNotEmpty()) {
            if (path.startsWith("/")) {
                "$host$path"
            } else {
                "$host/$path"
            }
        } else {
            path
        }

        // Extract query parameters
        uri.queryParameterNames.forEach { param ->
            uri.getQueryParameter(param)?.let { value ->
                context.deepLinkParams[param] = value
            }
        }
        
        // For scheme middleware, schemeUrl is same as originalUrl
        context.schemeUrl = uri
        
        Log.d(TAG, "Custom scheme link processed - path: ${context.deepLinkPath}, params: ${context.deepLinkParams}")

        return next(context)
    }

    override fun canHandle(uri: Uri): Boolean {
        return uri.scheme != null && uri.scheme in supportedSchemes
    }
}