package com.applinks.android.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.format.DateTimeFormatter

object LongToIsoSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LongToIso", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        val instant = Instant.ofEpochMilli(value)
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(instant))
    }

    override fun deserialize(decoder: Decoder): Long {
        val isoString = decoder.decodeString()
        return Instant.parse(isoString).toEpochMilli()
    }
}

object NullableLongToIsoSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NullableLongToIso", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            val instant = Instant.ofEpochMilli(value)
            encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(instant))
        }
    }

    override fun deserialize(decoder: Decoder): Long? {
        return if (decoder.decodeNotNullMark()) {
            val isoString = decoder.decodeString()
            Instant.parse(isoString).toEpochMilli()
        } else {
            decoder.decodeNull()
        }
    }
}