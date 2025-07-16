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
import kotlinx.coroutines.withContext
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
                    supportedSchemes = config.supportedSchemes
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
    fun handleLink(uri: Uri) {
        coroutineScope.launch {
            val context = LinkHandlingContext(
                isFirstLaunch = preferences.isFirstLaunch
            )
            
            val result = middlewareChain.processLink(uri, context)
            
            if (result.error != null) {
                notifyListenersError(result.error)
            } else {
                notifyListenersLinkReceived(result)
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
                        
                        // Process the referrer data for deep links
                        processReferrerForDeepLink(referrerData.installReferrer)
                        
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
    
    private fun processReferrerForDeepLink(referrerString: String) {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Processing referrer for deep link: $referrerString")
                
                // Parse the URL-encoded referrer string as a URI
                val referrerUri = Uri.parse(referrerString)
                
                val context = LinkHandlingContext(
                    isFirstLaunch = preferences.isFirstLaunch
                )
                
                val result = middlewareChain.processLink(referrerUri, context)
                
                if (result.error != null) {
                    notifyListenersError(result.error)
                } else {
                    notifyListenersLinkReceived(result)
                    Log.d(TAG, "Deferred deep link processed: ${result.path}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing referrer for deep link", e)
                notifyListenersError("Error processing deferred deep link: ${e.message}")
            }
        }
    }
    

    /**
     * Listener interface for incoming deep links
     */
    interface AppLinksListener {
        fun onLinkReceived(result: LinkHandlingResult)
        fun onError(error: String)
    }
    
    /**
     * Add a listener for incoming deep links
     */
    fun addLinkListener(listener: AppLinksListener) {
        linkListeners.add(listener)
    }
    
    /**
     * Remove a listener for incoming deep links
     */
    fun removeLinkListener(listener: AppLinksListener) {
        linkListeners.remove(listener)
    }
    
    private suspend fun notifyListenersLinkReceived(result: LinkHandlingResult) {
        withContext(Dispatchers.Main) {
            linkListeners.forEach { listener ->
                try {
                    listener.onLinkReceived(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener", e)
                }
            }
        }
    }
    
    private suspend fun notifyListenersError(error: String) {
        withContext(Dispatchers.Main) {
            linkListeners.forEach { listener ->
                try {
                    listener.onError(error)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener", e)
                }
            }
        }
    }
    
    /**
     * Add a custom middleware
     */
    fun addCustomMiddleware(middleware: Middleware) {
        middlewareChain.addMiddleware(middleware)
    }
    
}