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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AppLinks SDK using the link handling abstraction
 */
class AppLinksSDK private constructor(
    private val config: Config,
    private val customMiddlewares: List<Middleware> = emptyList(),
    private val preferences: AppLinksPreferences,
    private val middlewareChain: MiddlewareChain,
    private val installReferrerManager: InstallReferrerManager
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

                val appContext = context.applicationContext

                // Create dependencies using application context
                val preferences = AppLinksPreferences(appContext)
                val middlewareChain = MiddlewareChain(appContext)
                val installReferrerManager = InstallReferrerManager(appContext)
                
                instance = AppLinksSDK(config, customMiddlewares, preferences, middlewareChain, installReferrerManager)
                return instance!!
            }
        }
    }
    
    data class Config(
        val autoHandleLinks: Boolean = true,
        val serverUrl: String = "https://applinks.com",
        val apiKey: String? = null,
        val supportedDomains: Set<String> = emptySet(),
        val supportedSchemes: Set<String> = emptySet()
    )
    
    private val apiClient: AppLinksApiClient = AppLinksApiClient(
        serverUrl = config.serverUrl,
        apiKey = config.apiKey,
    )
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val linkListeners = CopyOnWriteArrayList<AppLinksListener>()
    
    init {
        setupMiddlewares()
        checkForDeferredDeepLinkIfFirstLaunch()
    }
    
    private fun setupMiddlewares() {
        // Add logging middleware first
        middlewareChain.addMiddleware(LoggingMiddleware())
        
        // Add Universal Link Middleware if domains are configured
        if (config.supportedDomains.isNotEmpty()) {
            middlewareChain.addMiddleware(
                UniversalLinkMiddleware(
                    supportedDomains = config.supportedDomains,
                    apiClient = apiClient,
                )
            )
        }
        
        // Add Custom Scheme Middleware if schemes are configured
        if (config.supportedSchemes.isNotEmpty()) {
            middlewareChain.addMiddleware(
                SchemeMiddleware(
                    supportedSchemes = config.supportedSchemes,
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
            Log.d(TAG, "Skipping deferred deep link check - not first launch")
            return
        }

        Log.d(TAG, "First launch detected - checking for deferred deep link")
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
                        Log.d(TAG, "Install referrer retrieved: ${referrerData.installReferrer}")

                        // Mark first launch as completed
                        preferences.markFirstLaunchCompleted()
                        
                        // Process the referrer data
                        
                    }
                    
                    override fun onError(error: String) {
                        Log.d(TAG, "No install referrer found or error: $error")

                        // Mark first launch as completed even if no referrer found
                        preferences.markFirstLaunchCompleted()
                    }
                }
            )
        }
    }

    /**
     * Listener interface for incoming deep links
     */
    interface AppLinksListener {
        fun onLinkReceived(link: String, metadata: Map<String, String>)
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