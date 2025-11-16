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
import kotlinx.serialization.json.JsonObject

/**
 * Validates additional parameters to ensure they do not conflict with built-in parameters.
 *
 * @param builtInParams A set of built-in parameter names.
 * @return A map of validated additional parameters.
 * @throws IllegalArgumentException if any parameter conflicts with a built-in parameter.
 */
internal fun JsonObject?.checkAdditionalParams(
    builtInParams: Set<String>
): JsonObject {
    this ?: return JsonObject(emptyMap())

    keys.onEach {
        require(it !in builtInParams) {
            "Parameter $it is directly supported via the authorization request builder, " +
                    "use the builder method instead"
        }
    }

    return this
}

internal fun JsonObject.extractAdditionalParams(
    builtInParams: Set<String>
) = JsonObject(filterKeys { it !in builtInParams })

/**
 * Extracts additional parameters from a URI, excluding built-in parameters.
 *
 * @param builtInParams A set of built-in parameter names.
 * @return A map of additional parameters extracted from the URI.
 */
internal fun Uri.extractAdditionalParams(builtInParams: Set<String>) = JsonObject(
    getQueryParameterNames().asSequence()
        .filterNot { it in builtInParams }
        .filter { getQueryParameter(it) != null }
        .associateWith { getQueryParameter(it)!!.toJsonElement() }
)