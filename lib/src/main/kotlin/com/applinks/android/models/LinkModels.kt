package com.applinks.android.models

import com.applinks.android.serializers.LongToIsoSerializer
import com.applinks.android.serializers.NullableLongToIsoSerializer
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
    @Serializable(with = NullableLongToIsoSerializer::class)
    val expiresAt: Long? = null,
    @SerialName("created_at")
    @Serializable(with = LongToIsoSerializer::class)
    val createdAt: Long,
    @SerialName("updated_at")
    @Serializable(with = LongToIsoSerializer::class)
    val updatedAt: Long,
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
    val subtitle: String? = null,
    @SerialName("original_url")
    val originalUrl: String? = null,
    @SerialName("deep_link_path")
    val deepLinkPath: String,
    @SerialName("deep_link_params")
    val deepLinkParams: Map<String, String>? = null,
    @SerialName("expires_at")
    @Serializable(with = NullableLongToIsoSerializer::class)
    val expiresAt: Long? = null,
    @SerialName("alias_path_attributes")
    val aliasPathAttributes: AliasPathAttributes? = null,
    @SerialName("background_type")
    val backgroundType: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("background_color_from")
    val backgroundColorFrom: String? = null,
    @SerialName("background_color_to")
    val backgroundColorTo: String? = null,
    @SerialName("background_color_direction")
    val backgroundColorDirection: String? = null
)

@Serializable
data class AliasPathAttributes(
    val type: String
)
