package com.applinks.android

import android.os.Build

object AppLinksSDKVersion {
    val current: String get() = BuildConfig.SDK_VERSION
    
    val name: String get() = BuildConfig.SDK_NAME
    
    val fullName: String get() = "$name/$current"
    
    val buildDate: String get() = BuildConfig.BUILD_DATE
    
    internal val userAgent: String
        get() = "$fullName (Android/${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT})"
    
    val asDictionary: Map<String, String>
        get() = mapOf(
            "version" to current,
            "name" to name,
            "fullName" to fullName,
            "buildDate" to buildDate
        )
}