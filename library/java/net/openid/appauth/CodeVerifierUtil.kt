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

import android.util.Base64
import net.openid.appauth.internal.Logger
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.regex.Pattern

/**
 * Generates code verifiers and challenges for PKCE exchange.
 *
 * @see "Proof Key for Code Exchange by OAuth Public Clients
 */
object CodeVerifierUtil {
    /**
     * The minimum permitted length for a code verifier.
     *
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    const val MIN_CODE_VERIFIER_LENGTH: Int = 43

    /**
     * The maximum permitted length for a code verifier.
     *
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    const val MAX_CODE_VERIFIER_LENGTH: Int = 128

    /**
     * The default entropy (in bytes) used for the code verifier.
     */
    const val DEFAULT_CODE_VERIFIER_ENTROPY: Int = 64

    /**
     * The minimum permitted entropy (in bytes) for use with
     * [.generateRandomCodeVerifier].
     */
    const val MIN_CODE_VERIFIER_ENTROPY: Int = 32

    /**
     * The maximum permitted entropy (in bytes) for use with
     * [.generateRandomCodeVerifier].
     */
    const val MAX_CODE_VERIFIER_ENTROPY: Int = 96

    /**
     * Base64 encoding settings used for generated code verifiers.
     */
    private const val PKCE_BASE64_ENCODE_SETTINGS =
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE

    /**
     * Regex for legal code verifier strings, as defined in the spec.
     *
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    private val REGEX_CODE_VERIFIER: Pattern =
        Pattern.compile("^[0-9a-zA-Z\\-._~]{43,128}$")


    /**
     * Throws an IllegalArgumentException if the provided code verifier is invalid.
     *
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    @JvmStatic
    fun checkCodeVerifier(codeVerifier: String) {
        require(MIN_CODE_VERIFIER_LENGTH <= codeVerifier.length) {
            "codeVerifier length is shorter than allowed by the PKCE specification"
        }

        require(codeVerifier.length <= MAX_CODE_VERIFIER_LENGTH) {
            "codeVerifier length is longer than allowed by the PKCE specification"
        }

        require(REGEX_CODE_VERIFIER.matcher(codeVerifier).matches()) {
            "codeVerifier string contains illegal characters"
        }
    }

    /**
     * Generates a random code verifier string using the provided entropy source and the specified
     * number of bytes of entropy.
     */
    /**
     * Generates a random code verifier string using [SecureRandom] as the source of
     * entropy, with the default entropy quantity as defined by
     * [.DEFAULT_CODE_VERIFIER_ENTROPY].
     */
    @JvmStatic
    @JvmOverloads
    fun generateRandomCodeVerifier(
        entropySource: SecureRandom = SecureRandom(),
        entropyBytes: Int = DEFAULT_CODE_VERIFIER_ENTROPY
    ): String {
        require(MIN_CODE_VERIFIER_ENTROPY <= entropyBytes) {
            "entropyBytes is less than the minimum permitted"
        }

        require(entropyBytes <= MAX_CODE_VERIFIER_ENTROPY) {
            "entropyBytes is greater than the maximum permitted"
        }

        val randomBytes = ByteArray(entropyBytes)
        entropySource.nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, PKCE_BASE64_ENCODE_SETTINGS)
    }

    /**
     * Produces a challenge from a code verifier, using SHA-256 as the challenge method if the
     * system supports it (all Android devices _should_ support SHA-256), and falls back
     * to the [&quot;plain&quot; challenge type][AuthorizationRequest.CODE_CHALLENGE_METHOD_PLAIN] if
     * unavailable.
     */
    @JvmStatic
    fun deriveCodeVerifierChallenge(codeVerifier: String): String {
        try {
            val sha256Digester = MessageDigest.getInstance("SHA-256")
            sha256Digester.update(codeVerifier.toByteArray(charset("ISO_8859_1")))
            val digestBytes = sha256Digester.digest()
            return Base64.encodeToString(digestBytes, PKCE_BASE64_ENCODE_SETTINGS)
        } catch (e: NoSuchAlgorithmException) {
            Logger.warn("SHA-256 is not supported on this device! Using plain challenge", e)
            return codeVerifier
        } catch (e: UnsupportedEncodingException) {
            Logger.error("ISO-8859-1 encoding not supported on this device!", e)
            throw IllegalStateException("ISO-8859-1 encoding not supported", e)
        }
    }

    @JvmStatic
    val codeVerifierChallengeMethod: String
        /**
         * Returns the challenge method utilized on this system: typically
         * [SHA-256][AuthorizationRequest.CODE_CHALLENGE_METHOD_S256] if supported by
         * the system, [plain][AuthorizationRequest.CODE_CHALLENGE_METHOD_PLAIN] otherwise.
         */
        get() {
            try {
                MessageDigest.getInstance("SHA-256")
                // no exception, so SHA-256 is supported
                return AuthorizationRequest.CODE_CHALLENGE_METHOD_S256
            } catch (_: NoSuchAlgorithmException) {
                return AuthorizationRequest.CODE_CHALLENGE_METHOD_PLAIN
            }
        }
}
