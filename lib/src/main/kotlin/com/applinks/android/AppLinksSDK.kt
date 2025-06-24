package com.applinks.android

import android.content.Context
import android.net.Uri
import android.util.Log
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.handlers.*
import com.applinks.android.storage.AppLinksPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AppLinks SDK using the link handling abstraction
 */
class AppLinksSDK private constructor(
    private val context: Context, 
    private val config: Config,
    private val customHandlers: List<LinkHandler> = emptyList()
) {
    companion object {
        private const val TAG = "AppLinksSDK"
        
        @Volatile
        private var instance: AppLinksSDK? = null
        
        fun builder(context: Context): AppLinksSDKBuilder {
            return AppLinksSDKBuilder(context)
        }
        
        fun getInstance(): AppLinksSDK {
            return instance ?: throw IllegalStateException(
                "AppLinksSDK not initialized. Call AppLinksSDK.builder().build() first."
            )
        }
        
        internal fun initialize(
            context: Context, 
            config: Config, 
            customHandlers: List<LinkHandler>
        ): AppLinksSDK {
            synchronized(this) {
                if (instance != null) {
                    Log.w(TAG, "AppLinksSDK already initialized")
                }
                instance = AppLinksSDK(context, config, customHandlers)
                return instance!!
            }
        }
    }
    
    data class Config(
        val autoHandleLinks: Boolean = true,
        val enableLogging: Boolean = true,
        val serverUrl: String = "https://applinks.com",
        val apiKey: String? = null,
        val supportedDomains: Set<String> = emptySet(),
        val supportedSchemes: Set<String> = emptySet()
    )
    
    private val apiClient: AppLinksApiClient = AppLinksApiClient(
        serverUrl = config.serverUrl,
        apiKey = config.apiKey,
        enableLogging = config.enableLogging
    )
    
    private val preferences = AppLinksPreferences(context)
    private val linkHandlingManager = LinkHandlingManager(context, config.enableLogging)
    private val installReferrerManager = InstallReferrerManager(context, apiClient, preferences, config.enableLogging)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    init {
        setupHandlers()
        checkForDeferredDeepLinkIfFirstLaunch()
    }
    
    private fun setupHandlers() {
        // Add Universal Link Handler if domains are configured
        if (config.supportedDomains.isNotEmpty()) {
            linkHandlingManager.addHandler(
                UniversalLinkHandler(
                    supportedDomains = config.supportedDomains,
                    autoHandleLinks = config.autoHandleLinks,
                    enableLogging = config.enableLogging
                )
            )
        }
        
        // Add Custom Scheme Handler if schemes are configured
        if (config.supportedSchemes.isNotEmpty()) {
            linkHandlingManager.addHandler(
                CustomSchemeHandler(
                    supportedSchemes = config.supportedSchemes,
                    autoHandleLinks = config.autoHandleLinks,
                    enableLogging = config.enableLogging
                )
            )
        }
        
        // Add custom handlers provided via builder
        customHandlers.forEach { handler ->
            linkHandlingManager.addHandler(handler)
        }
    }
    
    /**
     * Process any type of link - universal or custom scheme
     */
    fun handleLink(uri: Uri, callback: LinkCallback) {
        coroutineScope.launch {
            linkHandlingManager.handleLink(uri, object : LinkHandlerCallback {
                override fun onLinkHandled(uri: Uri, metadata: Map<String, String>) {
                    callback.onSuccess(uri.toString(), metadata)
                }
                
                override fun onError(error: String) {
                    callback.onError(error)
                }
            })
        }
    }
    
    /**
     * Check for deferred deep link only on first app launch
     */
    private fun checkForDeferredDeepLinkIfFirstLaunch() {
        if (!preferences.isFirstLaunch) {
            if (config.enableLogging) {
                Log.d(TAG, "Skipping deferred deep link check - not first launch")
            }
            return
        }
        
        if (config.enableLogging) {
            Log.d(TAG, "First launch detected - checking for deferred deep link")
        }
        
        checkForDeferredDeepLink()
    }
    
    /**
     * Automatically check for deferred deep link during SDK initialization
     */
    private fun checkForDeferredDeepLink() {
        coroutineScope.launch {
            installReferrerManager.retrieveDeferredDeepLink(
                object : InstallReferrerManager.DeferredLinkCallback {
                    override fun onLinkRetrieved(uri: Uri, metadata: Map<String, String>) {
                        if (config.enableLogging) {
                            Log.d(TAG, "Deferred deep link retrieved automatically: $uri")
                        }
                        
                        // Mark first launch as completed
                        preferences.markFirstLaunchCompleted()
                        
                        // Handle the retrieved link using the standard link handlers
                        coroutineScope.launch {
                            linkHandlingManager.handleLink(uri, object : LinkHandlerCallback {
                                override fun onLinkHandled(handledUri: Uri, handledMetadata: Map<String, String>) {
                                    if (config.enableLogging) {
                                        Log.d(TAG, "Deferred deep link handled automatically: $handledUri")
                                    }
                                }
                                
                                override fun onError(error: String) {
                                    if (config.enableLogging) {
                                        Log.w(TAG, "Error handling deferred deep link: $error")
                                    }
                                }
                            })
                        }
                    }
                    
                    override fun onError(error: String) {
                        if (config.enableLogging) {
                            Log.d(TAG, "No deferred deep link found or error: $error")
                        }
                        
                        // Mark first launch as completed even if no deferred link found
                        preferences.markFirstLaunchCompleted()
                    }
                }
            )
        }
    }

    /**
     * Unified callback interface for all link types
     */
    interface LinkCallback {
        fun onSuccess(link: String, metadata: Map<String, String>)
        fun onError(error: String)
    }
    
    /**
     * Add a custom link handler
     */
    fun addCustomHandler(handler: LinkHandler) {
        linkHandlingManager.addHandler(handler)
    }
    
    /**
     * Legacy interface for backward compatibility
     */
    interface DeferredDeepLinkCallback {
        fun onDeepLinkReceived(deepLink: String, metadata: Map<String, String>, handled: Boolean)
        fun onError(error: String)
    }
}