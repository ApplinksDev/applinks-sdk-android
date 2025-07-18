package com.applinks.android.models

import com.applinks.android.serializers.InstantSerializer
import com.applinks.android.serializers.NullableInstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

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
    @Serializable(with = NullableInstantSerializer::class)
    val expiresAt: Instant? = null,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
    @SerialName("full_url")
    val fullUrl: String,
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
data class CreateLinkRequest(
    val domain: String,
    val link: LinkData
)

@Serializable
data class LinkData(
    val title: String,
    @SerialName("original_url")
    val originalUrl: String? = null,
    @SerialName("deep_link_path")
    val deepLinkPath: String? = null,
    @SerialName("deep_link_params")
    val deepLinkParams: Map<String, String>? = null,
    @SerialName("expires_at")
    @Serializable(with = NullableInstantSerializer::class)
    val expiresAt: Instant? = null,
    @SerialName("alias_path_attributes")
    val aliasPathAttributes: AliasPathAttributes? = null
)

@Serializable
data class AliasPathAttributes(
    val type: String
)
