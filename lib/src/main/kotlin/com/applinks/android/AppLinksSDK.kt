package com.applinks.android

import android.content.Context
import android.net.Uri
import android.util.Log
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.handlers.*
import com.applinks.android.middleware.*
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
    private val customMiddlewares: List<Middleware> = emptyList()
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
            customMiddlewares: List<Middleware>
        ): AppLinksSDK {
            synchronized(this) {
                if (instance != null) {
                    Log.w(TAG, "AppLinksSDK already initialized")
                }
                instance = AppLinksSDK(context, config, customMiddlewares)
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
    private val middlewareChain = MiddlewareChain(context, config.enableLogging)
    private val installReferrerManager = InstallReferrerManager(context, config.enableLogging)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    init {
        setupMiddlewares()
        checkForDeferredDeepLinkIfFirstLaunch()
    }
    
    private fun setupMiddlewares() {
        // Add logging middleware first
        middlewareChain.addMiddleware(LoggingMiddleware(config.enableLogging))
        
        // Add Universal Link Middleware if domains are configured
        if (config.supportedDomains.isNotEmpty()) {
            middlewareChain.addMiddleware(
                UniversalLinkMiddleware(
                    supportedDomains = config.supportedDomains,
                    apiClient = apiClient,
                    enableLogging = config.enableLogging
                )
            )
        }
        
        // Add Custom Scheme Middleware if schemes are configured
        if (config.supportedSchemes.isNotEmpty()) {
            middlewareChain.addMiddleware(
                SchemeMiddleware(
                    supportedSchemes = config.supportedSchemes,
                    enableLogging = config.enableLogging
                )
            )
        }
        
        // Add custom middlewares provided via builder
        customMiddlewares.forEach { middleware ->
            middlewareChain.addMiddleware(middleware)
        }
    }
    
    /**
     * Process any type of link - universal or custom scheme
     */
    fun handleLink(uri: Uri, callback: LinkCallback) {
        coroutineScope.launch {
            val context = LinkHandlingContext(
                isFirstLaunch = preferences.isFirstLaunch
            )
            
            val result = middlewareChain.processLink(uri, context)
            
            if (result.error != null) {
                callback.onError(result.error)
            } else {
                // Convert metadata to String map for backward compatibility
                val stringMetadata = result.metadata.mapValues { it.value.toString() }
                callback.onSuccess(uri.toString(), stringMetadata)
            }
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
            installReferrerManager.retrieveInstallReferrer(
                object : InstallReferrerManager.InstallReferrerCallback {
                    override fun onReferrerRetrieved(referrerData: InstallReferrerManager.ReferrerData) {
                        if (config.enableLogging) {
                            Log.d(TAG, "Install referrer retrieved: ${referrerData.installReferrer}")
                        }
                        
                        // Mark first launch as completed
                        preferences.markFirstLaunchCompleted()
                        
                        // Process the referrer data
                        
                    }
                    
                    override fun onError(error: String) {
                        if (config.enableLogging) {
                            Log.d(TAG, "No install referrer found or error: $error")
                        }
                        
                        // Mark first launch as completed even if no referrer found
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
     * Add a custom middleware
     */
    fun addCustomMiddleware(middleware: Middleware) {
        middlewareChain.addMiddleware(middleware)
    }
    
    /**
     * Legacy interface for backward compatibility
     */
    interface DeferredDeepLinkCallback {
        fun onDeepLinkReceived(deepLink: String, metadata: Map<String, String>, handled: Boolean)
        fun onError(error: String)
    }
}