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
    private val supportedSchemes: Set<String> = emptySet()
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
        
        Log.d(TAG, "Processing universal link: $uri")

        // Call the API to retrieve full link details
        when (val result = apiClient.retrieveLink(uri.toString())) {
            is AppLinksApiClient.Result.Success -> {
                val linkResponse = result.data
                
                Log.d(TAG, "Successfully retrieved link details from API")

                // Set the deep link path and params from API response
                context.deepLinkPath = linkResponse.deepLinkPath
                context.deepLinkParams.putAll(linkResponse.deepLinkParams)
                
                // Add visit tracking if available
                linkResponse.visitId?.let { visitId ->
                    context.additionalData["visit_id"] = visitId
                }
                
                // Construct schemeUrl using first available supported scheme
                context.schemeUrl = buildSchemeUrl(context)
                
                Log.d(TAG, "Universal link processed - path: ${context.deepLinkPath}, params: ${context.deepLinkParams}")
            }
            
            is AppLinksApiClient.Result.Error -> {
                Log.w(TAG, "Failed to retrieve link details: ${result.message}")
            }
        }
        
        return next(context)
    }
    
    private fun isUniversalLink(uri: Uri): Boolean {
        return when {
            uri.scheme !in setOf("http", "https") -> false
            uri.host == null -> false
            else -> supportedDomains.any { domain ->
                uri.host == domain
            }
        }
    }
    
    private fun buildSchemeUrl(context: LinkHandlingContext): Uri? {
        if (supportedSchemes.isEmpty() || context.deepLinkPath == null) {
            return null
        }
        
        val firstScheme = supportedSchemes.first()
        val path = context.deepLinkPath ?: ""
        
        // Extract the first path segment as the authority/host for custom schemes
        // e.g., "/product/shoes-123" -> authority="product", path="/shoes-123"
        val pathSegments = path.split("/").filter { it.isNotEmpty() }
        val authority = pathSegments.firstOrNull() ?: ""
        val remainingPath = if (pathSegments.size > 1) {
            "/" + pathSegments.drop(1).joinToString("/")
        } else {
            ""
        }
        
        val uriBuilder = Uri.Builder()
            .scheme(firstScheme)
            .authority(authority)
            .path(remainingPath)
            
        // Add query parameters
        context.deepLinkParams.forEach { (key, value) ->
            uriBuilder.appendQueryParameter(key, value)
        }
        
        return uriBuilder.build()
    }
}