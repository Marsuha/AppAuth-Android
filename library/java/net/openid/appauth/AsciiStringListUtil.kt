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

/**
 * Convenience methods for building and parsing space-delimited string lists, which are
 * frequently used in OAuth2 and OpenID Connect parameters.
 */
internal object AsciiStringListUtil {
    /**
     * Converts an iterable collection of strings into a consolidated, space-delimited
     * format. If the provided iterable contains no elements, then
     * `null` will be returned.
     */
    fun iterableToString(strings: Iterable<String>): String? {
        val stringSet = strings.toSet()
        if (stringSet.isEmpty()) return null
        return stringSet.joinToString(separator = " ")
    }

    /**
     * Converts the consolidated, space-delimited scope string to a set. If the supplied scope
     * string is empty, then `null` will be returned.
     */
    @JvmStatic
    fun stringToSet(spaceDelimitedStr: String): Set<String>? {
        if (spaceDelimitedStr.isBlank()) return null
        return spaceDelimitedStr.split(' ').toSet()
    }
}
