package com.applinks.android.api

import android.util.Log
import com.applinks.android.models.ErrorResponse
import com.applinks.android.models.LinkResponse
import com.applinks.android.models.VisitDetailsResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class AppLinksApiClient(
    private val serverUrl: String,
    private val apiKey: String?,
    private val enableLogging: Boolean = true
) {
    companion object {
        private const val TAG = "AppLinksApiClient"
        private const val CONNECT_TIMEOUT = 10L
        private const val READ_TIMEOUT = 10L
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
    }
    
    private fun buildRequest(url: String): Request {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
        
        apiKey?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        return requestBuilder.build()
    }
    
    private inline fun <reified T> executeRequest(request: Request, resourceType: String): Result<T> {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                
                if (enableLogging) {
                    Log.d(TAG, "Response code: ${response.code}")
                }
                
                when (response.code) {
                    200 -> {
                        body?.let {
                            try {
                                val result = json.decodeFromString<T>(it)
                                Result.Success(result)
                            } catch (e: Exception) {
                                if (enableLogging) {
                                    Log.e(TAG, "Failed to parse $resourceType response", e)
                                }
                                Result.Error("Failed to parse response: ${e.message}")
                            }
                        } ?: Result.Error("Empty response body")
                    }
                    401 -> Result.Error("Unauthorized: Invalid or missing API token", 401)
                    403 -> Result.Error("Forbidden: Access denied", 403)
                    404 -> Result.Error("${resourceType.replaceFirstChar { it.uppercase() }} not found", 404)
                    else -> {
                        val errorMessage = try {
                            body?.let {
                                val errorResponse = json.decodeFromString<ErrorResponse>(it)
                                errorResponse.error.message
                            } ?: "Unknown error"
                        } catch (e: Exception) {
                            "Server error: ${response.code}"
                        }
                        Result.Error(errorMessage, response.code)
                    }
                }
            }
        } catch (e: IOException) {
            if (enableLogging) {
                Log.e(TAG, "Network error", e)
            }
            Result.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            if (enableLogging) {
                Log.e(TAG, "Unexpected error", e)
            }
            Result.Error("Unexpected error: ${e.message}")
        }
    }
    
    fun fetchLink(linkId: String): Result<LinkResponse> {
        if (enableLogging) {
            Log.d(TAG, "Fetching link with ID: $linkId")
        }
        
        val url = "$serverUrl/api/v1/links/$linkId"
        val request = buildRequest(url)
        
        return executeRequest<LinkResponse>(request, "link")
    }
    
    fun fetchVisitDetails(visitId: String): Result<VisitDetailsResponse> {
        if (enableLogging) {
            Log.d(TAG, "Fetching visit details with ID: $visitId")
        }
        
        val url = "$serverUrl/api/v1/visits/$visitId/details"
        val request = buildRequest(url)
        
        return executeRequest<VisitDetailsResponse>(request, "visit")
    }
}