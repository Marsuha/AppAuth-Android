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

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import net.openid.appauth.browser.BrowserDescriptor.Companion.generateSignatureHashes
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Represents a browser that may be used for an authorization flow.
 *
 * Creates a description of a browser from the core properties that are frequently used to
 * decide whether a browser can be used for an authorization flow. In most cases, it is
 * more convenient to use the other variant of the constructor that consumes a
 * [PackageInfo] object provided by the package manager.
 *
 * @param packageName     The Android package name of the browser.
 * @param signatureHashes The set of [signatures][Signature] of the browser app,
 * which have been hashed with SHA-512, and Base-64 URL-safe encoded.
 * @param version         The version string of the browser app.
 * @param useCustomTab    Whether it is intended that the browser will be used via a custom tab.
 */
@Suppress("unused")
data class BrowserDescriptor(
    /**
     * The package name of the browser app.
     */
    val packageName: String,
    /**
     * The set of [signatures][Signature] of the browser app,
     * which have been hashed with SHA-512, and Base-64 URL-safe encoded.
     */
    val signatureHashes: Set<String>,
    /**
     * The version string of the browser app.
     */
    val version: String,
    /**
     * Whether it is intended that the browser will be used via a custom tab.
     */
    val useCustomTab: Boolean
) {
    companion object {
        // See: http://stackoverflow.com/a/2816747
        private const val PRIME_HASH_FACTOR = 92821

        private const val DIGEST_SHA_512 = "SHA-512"

        /**
         * Generates a SHA-512 hash, Base64 url-safe encoded, from a [Signature].
         */
        fun generateSignatureHash(signature: Signature): String {
            try {
                val digest = MessageDigest.getInstance(DIGEST_SHA_512)
                val hashBytes = digest.digest(signature.toByteArray())
                return Base64.encodeToString(hashBytes, Base64.URL_SAFE or Base64.NO_WRAP)
            } catch (_: NoSuchAlgorithmException) {
                throw IllegalStateException("Platform does not support $DIGEST_SHA_512 hashing")
            }
        }

        /**
         * Generates a set of SHA-512, Base64 url-safe encoded signature hashes from the provided
         * array of signatures.
         */
        fun generateSignatureHashes(signatures: Array<Signature>): Set<String> {
            return signatures.map { generateSignatureHash(it) }.toSet()
        }
    }
}

/**
 * Creates a description of a browser from a [PackageInfo] object returned from the
 * [android.content.pm.PackageManager]. The object is expected to include the
 * signatures of the app, which can be retrieved with the
 * [GET_SIGNATURES][android.content.pm.PackageManager.GET_SIGNATURES] flag when
 * calling [android.content.pm.PackageManager.getPackageInfo].
 */
@Suppress("DEPRECATION")
fun BrowserDescriptor(packageInfo: PackageInfo, useCustomTab: Boolean) = BrowserDescriptor(
    packageName = packageInfo.packageName,
    signatureHashes = generateSignatureHashes(packageInfo.getSignatures()),
    version = checkNotNull(packageInfo.versionName),
    useCustomTab = useCustomTab
)


private fun PackageInfo.getSignatures(): Array<Signature> {
    val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.signingCertificateHistory ?: signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        signatures
    }

    return checkNotNull(signatures)
}