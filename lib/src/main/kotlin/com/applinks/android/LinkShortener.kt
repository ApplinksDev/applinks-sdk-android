package com.applinks.android

import android.net.Uri
import com.applinks.android.api.AppLinksApiClient
import com.applinks.android.models.AliasPathAttributes
import com.applinks.android.models.CreateLinkRequest
import com.applinks.android.models.LinkData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Enum for link path types
 */
enum class PathType(val value: String) {
    /**
     * Generates a 32-character random path for security-sensitive links
     */
    UNGUESSABLE("UNGUESSABLE"),
    
    /**
     * Generates a 4-6 character random path for social media sharing
     */
    SHORT("SHORT")
}

class LinkShortener internal constructor(
    private val apiClient: AppLinksApiClient,
    private val coroutineScope: CoroutineScope
) {
    
    /**
     * API for creating short links
     */
    fun createShortLinkAsync(builder: ShortLinkBuilder.() -> Unit): Task<ShortLinkResult> {
        val linkBuilder = ShortLinkBuilder().apply(builder)
        return Task(apiClient, coroutineScope, linkBuilder)
    }
}

/**
 * DSL Builder for creating short links
 */
class ShortLinkBuilder {
    var web_link: Uri? = null
    var domain: String? = null
    var title: String? = null
    var deepLinkPath: String? = null
    var deepLinkParams: Map<String, String>? = null
    var expiresAt: Instant? = null
    var pathType: PathType = PathType.UNGUESSABLE
    
    internal fun build(): CreateLinkRequest {
        requireNotNull(web_link) { "web_link must be set" }
        requireNotNull(domain) { "domain must be set" }
        
        val linkData = LinkData(
            title = title ?: "AppLinks Link",
            originalUrl = web_link.toString(),
            deepLinkPath = deepLinkPath,
            deepLinkParams = deepLinkParams,
            expiresAt = expiresAt,
            aliasPathAttributes = AliasPathAttributes(type = pathType.value)
        )
        
        return CreateLinkRequest(
            domain = domain!!,
            link = linkData
        )
    }
}

/**
 * Task wrapper for async operations
 */
class Task<T> internal constructor(
    private val apiClient: AppLinksApiClient,
    private val coroutineScope: CoroutineScope,
    private val linkBuilder: ShortLinkBuilder
) {
    private var successListener: ((T) -> Unit)? = null
    private var failureListener: ((Exception) -> Unit)? = null
    
    fun addOnSuccessListener(listener: (T) -> Unit): Task<T> {
        successListener = listener
        executeRequest()
        return this
    }
    
    fun addOnFailureListener(listener: (Exception) -> Unit): Task<T> {
        failureListener = listener
        return this
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun executeRequest() {
        coroutineScope.launch {
            try {
                val request = linkBuilder.build()
                val result = apiClient.createLink(request)
                
                withContext(Dispatchers.Main) {
                    when (result) {
                        is AppLinksApiClient.Result.Success -> {
                            val shortLinkResult = ShortLinkResult(
                                shortLink = result.data.fullUrl.toUri(),
                            )
                            successListener?.invoke(shortLinkResult as T)
                        }
                        is AppLinksApiClient.Result.Error -> {
                            failureListener?.invoke(Exception(result.message))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    failureListener?.invoke(e)
                }
            }
        }
    }
}

/**
 * Result class for short link creation
 */
data class ShortLinkResult(
    val shortLink: Uri,
)

