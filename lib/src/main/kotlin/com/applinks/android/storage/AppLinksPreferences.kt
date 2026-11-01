package com.applinks.android.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * The small key-value storage the attribution state lives in, so it can be
 * replaced by an in-memory map in unit tests
 */
internal interface KeyValueStore {
    fun getString(key: String): String?

    /** Stores the value, or removes the key when it's null */
    fun putString(key: String, value: String?)

    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)
}

/**
 * SharedPreferences wrapper for AppLinks SDK
 */
internal class AppLinksPreferences(context: Context) : KeyValueStore {

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

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
