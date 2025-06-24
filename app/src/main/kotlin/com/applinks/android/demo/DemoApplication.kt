package com.applinks.android.demo

import android.app.Application
import com.applinks.android.AppLinksSDK

class DemoApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AppLinksSDK once at app startup using the builder pattern
        AppLinksSDK.builder(this)
            .autoHandleLinks(true)                          // Automatically handle deep links
            .enableLogging(true)                            // Enable logging in debug builds
            .apiKey("pk_thund3Qt1SAqvUtJtPzFBYg7aVMJ9BPD") // Your API key (use BuildConfig.APPLINKS_API_KEY in production)
            .supportedDomains("example.onapp.link")         // Add your domains for universal links
            .supportedSchemes("applinks")                   // Add your custom schemes
            .build()
    }
}