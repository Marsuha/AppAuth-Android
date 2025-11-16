package net.openid.appauth.internal

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

internal object UriSerializer : KSerializer<Uri> {
    override val descriptor = PrimitiveSerialDescriptor(
        "Uri",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): Uri {
        return decoder.decodeString().toUri()
    }

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }
}

internal object AudienceSerializer :
    JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        return element as? JsonArray ?: JsonArray(listOf(element))
    }
}