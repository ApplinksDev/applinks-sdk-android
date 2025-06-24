package com.applinks.android.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Handles custom scheme links (e.g., myapp://path/to/content)
 */
class CustomSchemeHandler(
    private val supportedSchemes: Set<String>,
    private val autoHandleLinks: Boolean = true,
    private val enableLogging: Boolean = true
) : LinkHandler {
    
    companion object {
        private const val TAG = "CustomSchemeHandler"
        private const val PRIORITY = 90
    }
    
    override fun canHandle(uri: Uri): Boolean {
        return uri.scheme != null && uri.scheme in supportedSchemes
    }
    
    override suspend fun handle(context: Context, uri: Uri, callback: LinkHandlerCallback) {
        if (enableLogging) {
            Log.d(TAG, "Handling custom scheme link: $uri")
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            // Check if the app has an activity that can handle this URL
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (activities.isNotEmpty()) {
                // Extract metadata from custom scheme URI
                val metadata = mutableMapOf<String, String>().apply {
                    put("link_type", "custom_scheme")
                    put("scheme", uri.scheme ?: "")
                    put("host", uri.host ?: "")
                    put("path", uri.path ?: "")
                    
                    // Parse path segments for routing
                    uri.pathSegments?.forEachIndexed { index, segment ->
                        put("path_segment_$index", segment)
                    }
                    
                    // Add query parameters
                    uri.queryParameterNames.forEach { param ->
                        uri.getQueryParameter(param)?.let { value ->
                            put("param_$param", value)
                        }
                    }
                }
                
                if (autoHandleLinks) {
                    context.startActivity(intent)
                    callback.onLinkHandled(uri, metadata)
                    
                    if (enableLogging) {
                        Log.d(TAG, "Successfully handled custom scheme link with ${activities.size} matching activities")
                    }
                } else {
                    callback.onLinkHandled(uri, metadata)
                    
                    if (enableLogging) {
                        Log.d(TAG, "Custom scheme link found but auto-handling disabled")
                    }
                }
            } else {
                val error = "No activity found to handle custom scheme: ${uri.scheme}"
                if (enableLogging) {
                    Log.w(TAG, error)
                }
                callback.onError(error)
            }
        } catch (e: Exception) {
            val error = "Error handling custom scheme link: ${e.message}"
            if (enableLogging) {
                Log.e(TAG, error, e)
            }
            callback.onError(error)
        }
    }
    
    override fun getPriority(): Int = PRIORITY
}