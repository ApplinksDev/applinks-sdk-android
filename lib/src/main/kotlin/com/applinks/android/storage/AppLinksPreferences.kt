package com.applinks.android.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper for AppLinks SDK
 */
internal class AppLinksPreferences(context: Context) {
    
    companion object {
        private const val PREF_FILE_NAME = "com.applinks.android.prefs"
        
        // Preference keys
        private const val KEY_FIRST_LAUNCH_COMPLETED = "first_launch_completed"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_FILE_NAME, 
        Context.MODE_PRIVATE
    )
    
    var isFirstLaunchCompleted: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETED, value).apply()
    
    val isFirstLaunch: Boolean
        get() = !isFirstLaunchCompleted
    
    fun markFirstLaunchCompleted() {
        isFirstLaunchCompleted = true
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}