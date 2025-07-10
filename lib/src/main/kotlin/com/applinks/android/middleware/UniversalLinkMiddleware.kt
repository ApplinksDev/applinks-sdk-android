package com.applinks.android.middleware

import android.content.Context
import android.net.Uri
import android.util.Log
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.handlers.LinkHandlingContext
import com.applinks.android.handlers.Middleware

/**
 * Middleware that handles universal links (HTTP/HTTPS from supported domains)
 * Integrates with the AppLinks API to retrieve full deep link details
 */
class UniversalLinkMiddleware(
    private val supportedDomains: Set<String>,
    private val apiClient: AppLinksApiClient,
    private val enableLogging: Boolean = true
) : Middleware {
    
    companion object {
        private const val TAG = "UniversalLinkMiddleware"
    }
    
    override suspend fun process(
        context: LinkHandlingContext, 
        uri: Uri, 
        androidContext: Context, 
        next: suspend (LinkHandlingContext) -> LinkHandlingContext
    ): LinkHandlingContext {
        if (!isUniversalLink(uri)) {
            // Not a universal link, continue to next middleware
            return next(context)
        }
        
        if (enableLogging) {
            Log.d(TAG, "Processing universal link: $uri")
        }
        
        // Call the API to retrieve full link details
        when (val result = apiClient.retrieveLink(uri.toString())) {
            is AppLinksApiClient.Result.Success -> {
                val linkResponse = result.data
                
                if (enableLogging) {
                    Log.d(TAG, "Successfully retrieved link details from API")
                }
                
                // Set the deep link path and params from API response
                context.deepLinkPath = linkResponse.deepLinkPath
                context.deepLinkParams.putAll(linkResponse.deepLinkParams)
                
                // Add metadata from API response
                context.additionalData["link_type"] = "universal"
                context.additionalData["link_id"] = linkResponse.id
                context.additionalData["link_title"] = linkResponse.title
                context.additionalData["original_url"] = linkResponse.originalUrl
                context.additionalData["domain"] = linkResponse.domain
                context.additionalData["full_url"] = linkResponse.fullUrl
                context.additionalData["created_at"] = linkResponse.createdAt
                context.additionalData["updated_at"] = linkResponse.updatedAt
                
                // Add visit tracking if available
                linkResponse.visitId?.let { visitId ->
                    context.additionalData["visit_id"] = visitId
                }
                
                // Add expiration info if available
                linkResponse.expiresAt?.let { expiresAt ->
                    context.additionalData["expires_at"] = expiresAt
                }
                
                if (enableLogging) {
                    Log.d(TAG, "Universal link processed - path: ${context.deepLinkPath}, params: ${context.deepLinkParams}")
                }
            }
            
            is AppLinksApiClient.Result.Error -> {
                if (enableLogging) {
                    Log.w(TAG, "Failed to retrieve link details: ${result.message}")
                }
                
                // Fallback: Extract basic info from the URL
                context.deepLinkPath = uri.path ?: ""
                context.additionalData["link_type"] = "universal_fallback"
                context.additionalData["host"] = uri.host ?: ""
                context.additionalData["error"] = result.message
                
                // Extract query parameters as fallback
                uri.queryParameterNames.forEach { param ->
                    uri.getQueryParameter(param)?.let { value ->
                        context.deepLinkParams[param] = value
                    }
                }
                
                if (enableLogging) {
                    Log.d(TAG, "Using fallback link processing - path: ${context.deepLinkPath}, params: ${context.deepLinkParams}")
                }
            }
        }
        
        return next(context)
    }
    
    private fun isUniversalLink(uri: Uri): Boolean {
        return when {
            uri.scheme !in setOf("http", "https") -> false
            uri.host == null -> false
            else -> supportedDomains.any { domain ->
                uri.host == domain || uri.host?.endsWith(".$domain") == true
            }
        }
    }
}