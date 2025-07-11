package com.applinks.android

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails

class InstallReferrerManager(
    private val context: Context,
) {
    
    companion object {
        private const val TAG = "InstallReferrerManager"
        private const val REFERRER_PARAM_LINK = "applinks_link"
    }
    
    private var referrerClient: InstallReferrerClient? = null
    
    interface InstallReferrerCallback {
        fun onReferrerRetrieved(referrerData: ReferrerData)
        fun onError(error: String)
    }
    
    interface DeferredLinkCallback {
        fun onLinkRetrieved(link: String, metadata: Map<String, String>)
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
}