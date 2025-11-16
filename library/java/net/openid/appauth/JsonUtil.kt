/*
 * Copyright 2015 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openid.appauth

import android.net.Uri
import androidx.annotation.VisibleForTesting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

fun emptyJsonObject() = JsonObject(emptyMap())

/**
 * Converts a nullable string to a [JsonElement] by attempting to parse it as a JSON value
 * or inferring its primitive type if possible.
 *
 * This function tries to interpret the string as a boolean, number (double, long, or ulong),
 * or a valid JSON structure. If none of these apply, it falls back to treating the string
 * as a JSON string literal.
 *
 * @return a [JsonElement] representing the parsed value:
 *   - [JsonNull] if the string is null
 *   - [JsonPrimitive] with appropriate type if it's a valid boolean or number
 *   - [JsonElement] from full JSON parsing if it's a valid JSON object, array, etc.
 *   - [JsonPrimitive] containing the original string if no parsing succeeds
 */
fun String?.toJsonElement(): JsonElement {
    if (this == null || this == JsonNull.content) return JsonNull

    return toBooleanStrictOrNull()?.let { JsonPrimitive(it) }
        ?: toLongOrNull()?.let { JsonPrimitive(it) }
        ?: toDoubleOrNull()?.let { JsonPrimitive(it) }
        ?: runCatching { Json.parseToJsonElement(this) }
            .getOrElse { JsonPrimitive(this) }
}

@VisibleForTesting
fun List<String>.toJsonArray() = buildJsonArray { forEach { add(it) } }

@VisibleForTesting
@JvmName("toUriListJsonArray")
fun List<Uri>.toJsonArray() = map { it.toString() }.toJsonArray()
