package com.applinks.android.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LinkResponse(
    val id: String,
    val title: String,
    @SerialName("alias_path")
    val aliasPath: String,
    val domain: String,
    @SerialName("original_url")
    val originalUrl: String,
    @SerialName("deep_link_path")
    val deepLinkPath: String,
    @SerialName("deep_link_params")
    val deepLinkParams: Map<String, String>,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("full_url")
    val fullUrl: String,
    @SerialName("visit_id")
    val visitId: String? = null
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetails
)

@Serializable
data class ErrorDetails(
    val status: String,
    val code: Int,
    val message: String
)

@Serializable
data class RetrieveLinkRequest(
    val url: String
)

@Serializable
data class VisitDetailsResponse(
    val id: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("last_seen_at")
    val lastSeenAt: String,
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("ip_address")
    val ipAddress: String,
    @SerialName("user_agent")
    val userAgent: String,
    @SerialName("browser_fingerprint")
    val browserFingerprint: kotlinx.serialization.json.JsonElement? = null,
    val link: LinkResponse? = null
)