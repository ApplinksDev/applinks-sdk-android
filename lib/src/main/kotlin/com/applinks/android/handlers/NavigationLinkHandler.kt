package com.applinks.android.handlers

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavDeepLinkBuilder
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavOptions
import androidx.navigation.findNavController

/**
 * Handles deep links using Android Navigation Component
 * 
 * This handler integrates with Navigation Component to automatically navigate
 * to the appropriate destination based on the deep link URI.
 * 
 * IMPORTANT: To use this handler, your app must include Navigation Component dependencies:
 * ```
 * implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
 * implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
 * ```
 */
class NavigationLinkHandler(
    private val navControllerProvider: () -> NavController?,
    private val navGraphId: Int,
    private val enableLogging: Boolean = true
) : LinkHandler {
    
    companion object {
        private const val TAG = "NavigationLinkHandler"
        private const val PRIORITY = 110 // Higher than UniversalLinkHandler
    }
    
    /**
     * Alternative constructor using Activity context
     * The handler will find the NavController using the activity's nav host fragment
     */
    constructor(
        activityContext: Context,
        navHostFragmentId: Int,
        navGraphId: Int,
        enableLogging: Boolean = true
    ) : this(
        navControllerProvider = {
            (activityContext as? androidx.appcompat.app.AppCompatActivity)
                ?.findNavController(navHostFragmentId)
        },
        navGraphId = navGraphId,
        enableLogging = enableLogging
    )
    
    override fun canHandle(uri: Uri): Boolean {
        // Check if the Navigation Component can handle this URI
        return try {
            val navController = navControllerProvider()
            if (navController == null) {
                if (enableLogging) {
                    Log.w(TAG, "NavController not available")
                }
                return false
            }
            
            // Check if navigation graph is available
            if (navController.graph == null) {
                if (enableLogging) {
                    Log.w(TAG, "Navigation graph not available")
                }
                return false
            }
            
            // Check if there's a matching deep link in the navigation graph
            val request = NavDeepLinkRequest.Builder.fromUri(uri).build()
            navController.graph.matchDeepLink(request) != null
        } catch (e: Exception) {
            if (enableLogging) {
                Log.e(TAG, "Error checking if Navigation can handle URI: $uri", e)
            }
            false
        }
    }
    
    override suspend fun handle(context: Context, uri: Uri, callback: LinkHandlerCallback) {
        if (enableLogging) {
            Log.d(TAG, "Handling navigation deep link: $uri")
        }
        
        try {
            val navController = navControllerProvider()
            if (navController == null) {
                callback.onError("NavController not available")
                return
            }
            
            // Double-check navigation graph is available
            if (navController.graph == null) {
                callback.onError("Navigation graph not available")
                return
            }
            
            // Extract metadata
            val metadata = mutableMapOf<String, String>().apply {
                put("link_type", "navigation")
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
            
            // Create navigation options for better UX
            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .build()
            
            // Navigate using the deep link - must be on main thread
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                // Already on main thread, navigate directly
                performNavigation(navController, uri, navOptions, metadata, callback)
            } else {
                // Switch to main thread
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.post {
                    performNavigation(navController, uri, navOptions, metadata, callback)
                }
            }
        } catch (e: Exception) {
            val error = "Error handling navigation deep link: ${e.message}"
            if (enableLogging) {
                Log.e(TAG, error, e)
            }
            callback.onError(error)
        }
    }
    
    override fun getPriority(): Int = PRIORITY
    
    private fun performNavigation(
        navController: NavController,
        uri: Uri,
        navOptions: NavOptions,
        metadata: MutableMap<String, String>,
        callback: LinkHandlerCallback
    ) {
        try {
            // Check if NavController is in a valid state for navigation
            if (navController.graph == null) {
                callback.onError("Navigation graph not available")
                return
            }
            
            navController.navigate(uri, navOptions)
            
            // Add current destination info to metadata
            navController.currentDestination?.let { destination ->
                metadata["destination_id"] = destination.id.toString()
                metadata["destination_label"] = destination.label?.toString() ?: ""
            }
            
            callback.onLinkHandled(uri, metadata)
            
            if (enableLogging) {
                Log.d(TAG, "Successfully navigated to destination via deep link")
            }
        } catch (e: IllegalArgumentException) {
            // This happens when no matching deep link is found
            val error = "Navigation Component could not handle URI: $uri"
            if (enableLogging) {
                Log.w(TAG, error)
            }
            callback.onError(error)
        } catch (e: IllegalStateException) {
            // This happens when NavController is not in a valid state
            val error = "Navigation Component not ready for navigation: ${e.message}"
            if (enableLogging) {
                Log.w(TAG, error)
            }
            callback.onError(error)
        } catch (e: Exception) {
            val error = "Error during navigation: ${e.message}"
            if (enableLogging) {
                Log.e(TAG, error, e)
            }
            callback.onError(error)
        }
    }
}

/**
 * Extension function to create Navigation Component deep links with parameters
 */
fun createNavigationDeepLink(
    context: Context,
    navGraphId: Int,
    destinationId: Int,
    args: Bundle? = null
): Uri {
    val builder = NavDeepLinkBuilder(context)
        .setGraph(navGraphId)
        .setDestination(destinationId)
    
    args?.let { builder.setArguments(it) }
    
    return builder.createTaskStackBuilder()
        .intents
        .firstOrNull()
        ?.data ?: Uri.EMPTY
}

/**
 * Usage example:
 * 
 * // In your Activity or Application setup:
 * 
 * // Option 1: Using NavController provider
 * val navigationHandler = NavigationLinkHandler(
 *     navControllerProvider = { 
 *         findNavController(R.id.nav_host_fragment) 
 *     },
 *     navGraphId = R.navigation.main_navigation
 * )
 * 
 * // Option 2: Using Activity context (simpler)
 * val navigationHandler = NavigationLinkHandler(
 *     activityContext = this,
 *     navHostFragmentId = R.id.nav_host_fragment,
 *     navGraphId = R.navigation.main_navigation
 * )
 * 
 * AppLinksSDK.getInstance().addCustomHandler(navigationHandler)
 * 
 * // In your navigation graph (res/navigation/main_navigation.xml):
 * <navigation ...>
 *     <fragment
 *         android:id="@+id/productDetailFragment"
 *         android:name="com.example.ProductDetailFragment">
 *         
 *         <argument
 *             android:name="productId"
 *             app:argType="string" />
 *         
 *         <deepLink
 *             android:id="@+id/deepLinkProduct"
 *             app:uri="https://example.com/product/{productId}" />
 *             
 *         <deepLink
 *             android:id="@+id/deepLinkProductScheme"
 *             app:uri="myapp://product/{productId}" />
 *     </fragment>
 * </navigation>
 * 
 * // The NavigationLinkHandler will automatically:
 * // 1. Match incoming URIs against deep links defined in your navigation graph
 * // 2. Extract parameters and pass them as arguments
 * // 3. Navigate to the correct destination
 * // 4. Handle the navigation back stack properly
 */