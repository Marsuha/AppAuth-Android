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
package net.openid.appauth.internal

import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsService
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.core.os.bundleOf

/**
 * Encodes this string into application/x-www-form-urlencoded format using UTF-8 encoding.
 *
 * @return the URL-encoded string
 * @throws IllegalStateException if UTF-8 encoding is not supported
 */
@JvmName("formUrlEncodeValue")
internal fun String.formUrlEncode() = try {
    URLEncoder.encode(this, "utf-8")
} catch (_: UnsupportedEncodingException) {
    // utf-8 should always be supported
    throw IllegalStateException("Unable to encode using UTF-8")
}

internal fun String.formUrlDecode(): List<Pair<String, String>> {
    if (this.isEmpty()) return emptyList()

    val params = buildList {
        this@formUrlDecode.split("&").forEach { parts ->
            val (param, encodedValue) = parts.split("=")

            try {
                add(param to URLDecoder.decode(encodedValue, "utf-8"))
            } catch (ex: UnsupportedEncodingException) {
                Logger.error("Unable to decode parameter, ignoring", ex)
            }
        }
    }

    return params
}

internal fun String.formUrlDecodeUnique() = this.formUrlDecode().toMap()

internal fun Array<out Uri?>.toCustomTabUriBundle(startIndex: Int = 0): List<Bundle> {
    require(startIndex >= 0) { "startIndex must be positive" }
    if (size <= startIndex) return emptyList()

    return drop(startIndex)
        .filterNotNull()
        .map { bundleOf(CustomTabsService.KEY_URL to it) }
}

internal fun Map<String, String>?.formUrlEncode(): String {
    return this?.toList()?.joinToString("&") {
        "${it.first}=${it.second.formUrlEncode()}"
    } ?: ""
}
