package com.applinks.android

import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.storage.AppLinksPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class InstallReferrerManager(
    private val context: Context,
    private val enableLogging: Boolean = true
) {
    internal constructor(
        context: Context,
        apiClient: AppLinksApiClient?,
        preferences: AppLinksPreferences?,
        enableLogging: Boolean = true
    ) : this(context, enableLogging) {
        this.apiClient = apiClient
        this.preferences = preferences
    }
    
    private var apiClient: AppLinksApiClient? = null
    private var preferences: AppLinksPreferences? = null
    
    companion object {
        private const val TAG = "InstallReferrerManager"
        private const val REFERRER_PARAM_VISIT_ID = "applinks_visit_id"
    }
    
    private var referrerClient: InstallReferrerClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    interface InstallReferrerCallback {
        fun onReferrerRetrieved(referrerData: ReferrerData)
        fun onError(error: String)
    }
    
    interface DeferredLinkCallback {
        fun onLinkRetrieved(uri: Uri, metadata: Map<String, String>)
        fun onError(error: String)
    }
    
    data class ReferrerData(
        val installReferrer: String,
        val referrerClickTimestampSeconds: Long,
        val installBeginTimestampSeconds: Long,
        val googlePlayInstantParam: Boolean
    )
    
    fun retrieveInstallReferrer(callback: InstallReferrerCallback) {
        referrerClient = InstallReferrerClient.newBuilder(context).build()
        
        referrerClient?.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        Log.d(TAG, "Install referrer connection established")
                        getReferrerDetails(callback)
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        Log.e(TAG, "Install referrer not supported")
                        callback.onError("Install referrer not supported")
                        disconnectReferrerClient()
                    }
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Log.e(TAG, "Install referrer service unavailable")
                        callback.onError("Install referrer service unavailable")
                        disconnectReferrerClient()
                    }
                    else -> {
                        Log.e(TAG, "Install referrer response code: $responseCode")
                        callback.onError("Unknown error: $responseCode")
                        disconnectReferrerClient()
                    }
                }
            }
            
            override fun onInstallReferrerServiceDisconnected() {
                Log.d(TAG, "Install referrer service disconnected")
            }
        })
    }
    
    private fun getReferrerDetails(callback: InstallReferrerCallback) {
        try {
            val response: ReferrerDetails = referrerClient?.installReferrer ?: run {
                callback.onError("Failed to get install referrer")
                disconnectReferrerClient()
                return
            }
            
            val referrerData = ReferrerData(
                installReferrer = response.installReferrer,
                referrerClickTimestampSeconds = response.referrerClickTimestampSeconds,
                installBeginTimestampSeconds = response.installBeginTimestampSeconds,
                googlePlayInstantParam = response.googlePlayInstantParam
            )
            
            Log.d(TAG, "Install referrer: ${referrerData.installReferrer}")
            Log.d(TAG, "Referrer click timestamp: ${referrerData.referrerClickTimestampSeconds}")
            Log.d(TAG, "Install begin timestamp: ${referrerData.installBeginTimestampSeconds}")
            Log.d(TAG, "Google Play instant: ${referrerData.googlePlayInstantParam}")
            
            callback.onReferrerRetrieved(referrerData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving install referrer", e)
            callback.onError("Error retrieving install referrer: ${e.message}")
        } finally {
            disconnectReferrerClient()
        }
    }
    
    private fun disconnectReferrerClient() {
        referrerClient?.endConnection()
        referrerClient = null
    }
    
    /**
     * Retrieve deferred deep link from install referrer
     * This combines referrer retrieval with server API call to get the actual deep link
     */
    fun retrieveDeferredDeepLink(callback: DeferredLinkCallback) {
        if (apiClient == null) {
            callback.onError("API client not configured")
            return
        }
        
        retrieveInstallReferrer(object : InstallReferrerCallback {
            override fun onReferrerRetrieved(referrerData: ReferrerData) {
                val parsedParams = parseReferrerParameters(referrerData.installReferrer)
                val visitId = parsedParams[REFERRER_PARAM_VISIT_ID]
                
                if (visitId != null) {
                    val metadata = mutableMapOf(
                        "source" to "install_referrer",
                        "referrerClickTime" to referrerData.referrerClickTimestampSeconds.toString(),
                        "installBeginTime" to referrerData.installBeginTimestampSeconds.toString()
                    )
                    fetchLinkFromServer(visitId, metadata, callback)
                } else {
                    if (enableLogging) {
                        Log.d(TAG, "No AppLinks visit ID found in referrer")
                    }
                    callback.onError("No deferred deep link found in install referrer")
                }
            }
            
            override fun onError(error: String) {
                callback.onError(error)
            }
        })
    }
    
    private fun parseReferrerParameters(referrer: String): Map<String, String> {
        return try {
            referrer.split("&")
                .map { it.split("=", limit = 2) }
                .filter { it.size == 2 }
                .associate { it[0] to it[1] }
        } catch (e: Exception) {
            if (enableLogging) {
                Log.e(TAG, "Error parsing referrer parameters", e)
            }
            emptyMap()
        }
    }
    
    private fun fetchLinkFromServer(
        visitId: String,
        metadata: MutableMap<String, String>,
        callback: DeferredLinkCallback
    ) {
        // Check if we've already processed this visit ID
        if (preferences?.hasVisitId(visitId) == true) {
            if (enableLogging) {
                Log.d(TAG, "Visit ID $visitId already processed, skipping")
            }
            callback.onError("Visit ID already processed")
            return
        }
        
        coroutineScope.launch {
            when (val result = apiClient!!.fetchVisitDetails(visitId)) {
                is AppLinksApiClient.Result.Success -> {
                    val visitData = result.data
                    val linkData = visitData.link
                    
                    if (linkData == null) {
                        withContext(Dispatchers.Main) {
                            callback.onError("No link data found in visit")
                        }
                        return@launch
                    }
                    
                    // Check if link is expired
                    if (isLinkExpired(linkData.expiresAt)) {
                        withContext(Dispatchers.Main) {
                            callback.onError("Link has expired")
                        }
                        return@launch
                    }
                    
                    // Build the deep link URI
                    val deepLinkUri = if (linkData.deepLinkPath.contains("://")) {
                        Uri.parse(linkData.deepLinkPath)
                    } else {
                        Uri.parse(linkData.originalUrl)
                    }
                    
                    // Add visit metadata
                    metadata["visitId"] = visitData.id
                    metadata["linkTitle"] = linkData.title
                    
                    // Store the visit ID if preferences are available
                    preferences?.addVisitId(visitData.id)
                    
                    withContext(Dispatchers.Main) {
                        callback.onLinkRetrieved(deepLinkUri, metadata)
                    }
                }
                is AppLinksApiClient.Result.Error -> {
                    if (enableLogging) {
                        Log.e(TAG, "Failed to fetch visit details: ${result.message}")
                    }
                    withContext(Dispatchers.Main) {
                        callback.onError(result.message)
                    }
                }
            }
        }
    }
    
    private fun isLinkExpired(expiresAt: String?): Boolean {
        if (expiresAt == null) return false
        
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val expiryDate = sdf.parse(expiresAt)
            val now = System.currentTimeMillis()
            expiryDate?.time?.let { it < now } ?: false
        } catch (e: Exception) {
            if (enableLogging) {
                Log.e(TAG, "Failed to parse expiry date: $expiresAt", e)
            }
            false
        }
    }
}