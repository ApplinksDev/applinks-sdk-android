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
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_FIRST_LAUNCH_COMPLETED = "first_launch_completed"
        private const val KEY_VISIT_IDS = "visit_ids"
        
        // Limits
        private const val MAX_VISIT_IDS = 500
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_FILE_NAME, 
        Context.MODE_PRIVATE
    )
    
    var sessionId: String?
        get() = prefs.getString(KEY_SESSION_ID, null)
        set(value) = prefs.edit().putString(KEY_SESSION_ID, value).apply()
    
    var isFirstLaunchCompleted: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETED, value).apply()
    
    val isFirstLaunch: Boolean
        get() = !isFirstLaunchCompleted
    
    fun markFirstLaunchCompleted() {
        isFirstLaunchCompleted = true
    }
    
    // Visit ID management
    private var visitIds: Set<String>
        get() = prefs.getStringSet(KEY_VISIT_IDS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_VISIT_IDS, value).apply()
    
    fun addVisitId(visitId: String) {
        val currentIds = visitIds.toMutableList()
        if (!currentIds.contains(visitId)) {
            currentIds.add(visitId)
            // Remove oldest entries if we exceed the limit
            if (currentIds.size > MAX_VISIT_IDS) {
                val toRemove = currentIds.size - MAX_VISIT_IDS
                repeat(toRemove) {
                    currentIds.removeAt(0)
                }
            }
            visitIds = currentIds.toSet()
        }
    }
    
    fun hasVisitId(visitId: String): Boolean = visitId in visitIds
    
    val visitIdCount: Int
        get() = visitIds.size
    
    fun clearVisitIds() {
        prefs.edit().remove(KEY_VISIT_IDS).apply()
    }
    
    fun clear() {
        prefs.edit().clear().apply()
    }
}