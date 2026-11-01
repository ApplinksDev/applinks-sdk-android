package com.applinks.android.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * The small key-value storage the attribution state lives in, so it can be
 * replaced by an in-memory map in unit tests
 */
internal interface KeyValueStore {
    fun getString(key: String): String?

    /**
     * Stores the value, or removes the key when it's null. A durable write is
     * on disk when it returns (call it off the main thread).
     */
    fun putString(key: String, value: String?, durable: Boolean = false)

    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun remove(key: String)
}

/**
 * SharedPreferences wrapper for AppLinks SDK
 */
internal class AppLinksPreferences(private val context: Context) : KeyValueStore {

    companion object {
        private const val TAG = "AppLinksPreferences"
        private const val PREF_FILE_NAME = "com.applinks.android.prefs"
        private const val INSTALL_MARKER = "com.applinks.android.install"

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

    override fun putString(key: String, value: String?, durable: Boolean) {
        val editor = if (value == null) prefs.edit().remove(key) else prefs.edit().putString(key, value)
        if (durable) editor.commit() else editor.apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /**
     * Whether the SDK runs for the first time in this install of the app, then
     * remembers that it ran. The preferences can come from another device or
     * an earlier install (Auto Backup, device transfer), but this marker can't:
     * it lives in noBackupFilesDir, which both leave out.
     */
    fun markInstallIfNew(): Boolean {
        return try {
            File(context.noBackupFilesDir, INSTALL_MARKER).createNewFile()
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the install marker", e)
            false
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
