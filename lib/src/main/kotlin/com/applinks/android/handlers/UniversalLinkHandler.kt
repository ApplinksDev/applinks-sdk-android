package com.applinks.android.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Handles Universal Links (web URLs that the app can handle)
 */
class UniversalLinkHandler(
    private val supportedDomains: Set<String>,
    private val autoHandleLinks: Boolean = true,
    private val enableLogging: Boolean = true
) : LinkHandler {
    
    companion object {
        private const val TAG = "UniversalLinkHandler"
        private const val PRIORITY = 100
    }
    
    override fun canHandle(uri: Uri): Boolean {
        return when {
            uri.scheme !in setOf("http", "https") -> false
            uri.host == null -> false
            else -> supportedDomains.any { domain ->
                uri.host == domain || uri.host?.endsWith(".$domain") == true
            }
        }
    }
    
    override suspend fun handle(context: Context, uri: Uri, callback: LinkHandlerCallback) {
        if (enableLogging) {
            Log.d(TAG, "Handling universal link: $uri")
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setPackage(context.packageName)
            }
            
            // Check if the app has an activity that can handle this URL
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (activities.isNotEmpty()) {
                // Extract metadata from URL
                val metadata = mutableMapOf<String, String>().apply {
                    put("link_type", "universal")
                    put("scheme", uri.scheme ?: "")
                    put("host", uri.host ?: "")
                    put("path", uri.path ?: "")
                    
                    // Add query parameters as metadata
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
                        Log.d(TAG, "Successfully handled universal link with ${activities.size} matching activities")
                    }
                } else {
                    callback.onLinkHandled(uri, metadata)
                    
                    if (enableLogging) {
                        Log.d(TAG, "Universal link found but auto-handling disabled")
                    }
                }
            } else {
                val error = "No activity found to handle universal link: $uri"
                if (enableLogging) {
                    Log.w(TAG, error)
                }
                callback.onError(error)
            }
        } catch (e: Exception) {
            val error = "Error handling universal link: ${e.message}"
            if (enableLogging) {
                Log.e(TAG, error, e)
            }
            callback.onError(error)
        }
    }
    
    override fun getPriority(): Int = PRIORITY
}