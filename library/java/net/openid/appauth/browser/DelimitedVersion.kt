/*
 * Copyright 2016 The AppAuth for Android Authors. All Rights Reserved.
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
package net.openid.appauth.browser

/**
 * Represents a delimited version number for an application. This can parse common version number
 * formats, treating any sequence of non-numeric characters as a delimiter, and discards these
 * to retain just the numeric content for comparison. Trailing zeroes in a version number
 * are discarded to produce a compact, canonical representation. Empty versions are equivalent to
 * "0". Each numeric part is expected to fit within a 64-bit integer.
 *
 * Creates a version with the specified parts, ordered from major to minor.
 */
class DelimitedVersion(private val numericParts: LongArray) : Comparable<DelimitedVersion> {
    override fun toString(): String {
        if (numericParts.isEmpty()) return "0"
        return numericParts.joinToString(separator = ".")
    }

    override fun equals(other: Any?): Boolean {
        return other is DelimitedVersion && this.compareTo(other) == 0
    }

    override fun hashCode(): Int {
        return numericParts.fold(0) { result, numericPart ->
            result * PRIME_HASH_FACTOR + (numericPart and BIT_MASK_32).toInt()
        }
    }

    override fun compareTo(other: DelimitedVersion): Int {
        for (index in numericParts.indices) {
            if (index >= other.numericParts.size) break
            val currentPartOrder = numericParts[index].compareTo(other.numericParts[index])
            if (currentPartOrder != 0) return currentPartOrder
        }

        return numericParts.size.compareTo(other.numericParts.size)
    }

    companion object {
        // See: http://stackoverflow.com/a/2816747
        private const val PRIME_HASH_FACTOR = 92821

        private const val BIT_MASK_32: Long = -0x1

        /**
         * Parses a delimited version number from the provided string.
         */
        @JvmStatic
        fun parse(versionString: String): DelimitedVersion {
            if (versionString.isEmpty()) return DelimitedVersion(LongArray(0))

            val stringParts = versionString.split("[^0-9]+".toRegex())
            val parsedParts = stringParts.mapNotNull { it.toLongOrNull() }

            val trimmedParts = parsedParts.dropLastWhile { it == 0L }.toLongArray()
            return DelimitedVersion(trimmedParts)
        }
    }
}
