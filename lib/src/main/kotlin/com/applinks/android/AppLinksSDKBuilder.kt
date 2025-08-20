package com.applinks.android

import android.content.Context
import android.util.Log
import com.applinks.android.middleware.LinkMiddleware

/**
 * Builder for AppLinksSDK initialization
 */
class AppLinksSDKBuilder(private val context: Context) {
    private var config: AppLinksSDK.Config = AppLinksSDK.Config()
    private val customMiddlewares = mutableListOf<LinkMiddleware>()
    
    fun config(config: AppLinksSDK.Config): AppLinksSDKBuilder {
        this.config = config
        return this
    }
    
    fun autoHandleLinks(enabled: Boolean): AppLinksSDKBuilder {
        config = config.copy(autoHandleLinks = enabled)
        return this
    }

    fun serverUrl(url: String): AppLinksSDKBuilder {
        config = config.copy(serverUrl = url)
        return this
    }
    
    fun apiKey(key: String): AppLinksSDKBuilder {
        config = config.copy(apiKey = key)
        return this
    }
    
    fun supportedDomains(vararg domains: String): AppLinksSDKBuilder {
        config = config.copy(supportedDomains = domains.toSet())
        return this
    }
    
    fun supportedSchemes(vararg schemes: String): AppLinksSDKBuilder {
        config = config.copy(supportedSchemes = schemes.toSet())
        return this
    }
    
    fun deferredDeepLinkingEnabled(enabled: Boolean): AppLinksSDKBuilder {
        config = config.copy(deferredDeepLinkingEnabled = enabled)
        return this
    }
    
    fun addCustomMiddleware(middleware: LinkMiddleware): AppLinksSDKBuilder {
        customMiddlewares.add(middleware)
        return this
    }
    
    fun build(): AppLinksSDK {
        // Validate API key format
        config.apiKey?.let { key ->
            if (key.startsWith("sk_")) {
                throw IllegalArgumentException(
                    "Private keys (sk_*) should never be used in mobile applications. " +
                    "Please use a public key (pk_*) instead."
                )
            }
            if (!key.startsWith("pk_") && key.isNotEmpty()) {
                Log.w("AppLinksSDK", "API key should start with 'pk_' for public keys. Current key: ${key.take(3)}...")
            }
        }
        
        return AppLinksSDK.initialize(
            context.applicationContext, 
            config,
            customMiddlewares.toList()
        )
    }
}