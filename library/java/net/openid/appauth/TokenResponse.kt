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

import androidx.annotation.VisibleForTesting
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.concurrent.TimeUnit

/**
 * A response to a token request.
 *
 * @see TokenRequest
 *
 * @see "The OAuth 2.0 Authorization Framework
 */
//@Suppress("unused")
@Serializable
class TokenResponse internal constructor(
    /**
     * The token request associated with this response.
     */
    val request: TokenRequest,
    /**
     * The type of the token returned. Typically this is [.TOKEN_TYPE_BEARER], or some
     * other token type that the client has negotiated with the authorization service.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    val tokenType: String? = null,
    /**
     * The access token, if provided.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    val accessToken: String? = null,
    /**
     * The expiration time of the access token, if provided. If an access token is provided but the
     * expiration time is not, then the expiration time is typically some default value specified
     * by the identity provider through some other means, such as documentation or an additional
     * non-standard field.
     */
    val accessTokenExpirationTime: Long? = null,
    /**
     * The ID token describing the authenticated user, if provided.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.2">
     *     OpenID Connect Core 1.0, Section 2</a>
     */
    val idToken: String? = null,
    /**
     * The refresh token, if provided.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    val refreshToken: String? = null,
    /**
     * The scope of the access token. If the scope is identical to that originally
     * requested, then this value is optional.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    val scope: String? = null,
    /**
     * Additional, non-standard parameters in the response.
     */
    val additionalParameters: JsonObject = JsonObject(emptyMap())
) {

    /**
     * The set of scopes returned in the response. This is derived from the
     * [scope] property. If the scope is `null`, this will also be `null`.
     *
     * @see <a href="https://tools.ietf.org/html/rfc6749#section-5.1">
     *     The OAuth 2.0 Authorization Framework, Section 5.1</a>
     */
    val scopeValues: Set<String>?
        get() = scope?.let { AsciiStringListUtil.stringToSet(it) }

    /**
     * Serializes the token response to a JSON string.
     */
    val asJsonString get() = Json.encodeToString(this)

    /**
     * Creates instances of [TokenResponse].
     */
    class Builder(private var request: TokenRequest) {

        private var tokenType: String? = null

        private var accessToken: String? = null

        private var accessTokenExpirationTime: Long? = null

        private var idToken: String? = null

        private var refreshToken: String? = null

        private var scope: String? = null

        private var additionalParameters: JsonObject = JsonObject(emptyMap())

        /**
         * Extracts token response fields from a JSON object.
         *
         * @throws SerializationException if the JSON is malformed or has incorrect value types for fields.
         */
        @VisibleForTesting
        fun fromResponseJsonString(jsonStr: String): Builder {
            require(jsonStr.isNotEmpty()) { "json cannot be empty" }
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            return fromResponseJson(json)
        }

        /**
         * Extracts token response fields from a JSON string.
         *
         * @throws SerializationException if the JSON is malformed or has incorrect value types for fields.
         */
        @Throws(IllegalArgumentException::class)
        fun fromResponseJson(json: JsonObject): Builder {
            require(json.isNotEmpty()) { "json cannot be empty" }
            val additionalParams = json.extractAdditionalParams(BUILT_IN_PARAMS)
            setTokenType(json[PARAM_TOKEN_TYPE]?.jsonPrimitive?.content)
            setAccessToken(json[PARAM_ACCESS_TOKEN]?.jsonPrimitive?.content)
            setAccessTokenExpirationTime(json[KEY_EXPIRES_AT]?.jsonPrimitive?.long)
            json[PARAM_EXPIRES_IN]?.let { setAccessTokenExpiresIn(it.jsonPrimitive.long) }
            setRefreshToken(json[PARAM_REFRESH_TOKEN]?.jsonPrimitive?.content)
            setIdToken(json[PARAM_ID_TOKEN]?.jsonPrimitive?.content)
            setScope(json[PARAM_SCOPE]?.jsonPrimitive?.content)
            setAdditionalParameters(additionalParams)
            return this
        }

        /**
         * Specifies the request associated with this response. Must not be null.
         */
        @Suppress("unused")
        fun setRequest(tokenRequest: TokenRequest): Builder {
            request = tokenRequest
            return this
        }

        /**
         * Specifies the token type of the access token in this response. If not null, the value
         * must be non-empty.
         */
        fun setTokenType(type: String?): Builder {
            type?.let { require(it.isNotEmpty()) { "token type must not be empty if defined" } }
            tokenType = type
            return this
        }

        /**
         * Specifies the access token. If not null, the value must be non-empty.
         */
        fun setAccessToken(token: String?): Builder {
            token?.let { require(it.isNotEmpty()) { "access token cannot be empty if specified" } }
            accessToken = token
            return this
        }

        /**
         * Sets the relative expiration time of the access token, in seconds, using the default
         * system clock as the source of the current time.
         */
        fun setAccessTokenExpiresIn(expiresIn: Long): Builder {
            return setAccessTokenExpiresIn(expiresIn, SystemClock)
        }

        /**
         * Sets the relative expiration time of the access token, in seconds, using the provided
         * clock as the source of the current time.
         */
        @VisibleForTesting
        fun setAccessTokenExpiresIn(expiresIn: Long?, clock: Clock): Builder {
            accessTokenExpirationTime = expiresIn?.let {
                (clock.currentTimeMillis + TimeUnit.SECONDS.toMillis(it))
            }

            return this
        }

        /**
         * Sets the exact expiration time of the access token, in milliseconds since the UNIX epoch.
         */
        fun setAccessTokenExpirationTime(expiresAt: Long?): Builder {
            accessTokenExpirationTime = expiresAt
            return this
        }

        /**
         * Specifies the ID token. If not null, the value must be non-empty.
         */
        fun setIdToken(token: String?): Builder {
            token?.let { require(it.isNotEmpty()) { "id token must not be empty if defined" } }
            idToken = token
            return this
        }

        /**
         * Specifies the refresh token. If not null, the value must be non-empty.
         */
        fun setRefreshToken(token: String?): Builder {
            token?.let { require(it.isNotEmpty()) { "refresh token must not be empty if defined" } }
            refreshToken = token
            return this
        }

        /**
         * Specifies the encoded scope string, which is a space-delimited set of
         * case-sensitive scope identifiers. Replaces any previously specified scope.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScope(scope: String?): Builder {
            this@Builder.scope = scope?.takeIf { it.isNotEmpty() }?.let { scope ->
                setScopes(*scope.split(" +").dropLastWhile { it.isEmpty() }.toTypedArray())
                scope
            }

            return this
        }

        /**
         * Specifies the set of case-sensitive scopes. Replaces any previously specified set of
         * scopes. Individual scope strings cannot be null or empty.
         *
         *
         * Scopes specified here are used to obtain a "down-scoped" access token, where the
         * set of scopes specified _must_ be a subset of those already granted in
         * previous requests.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScopes(vararg scopes: String): Builder {
            setScopes(listOf(*scopes))
            return this
        }

        /**
         * Specifies the set of case-sensitive scopes. Replaces any previously specified set of
         * scopes. Individual scope strings cannot be null or empty.
         *
         *
         * Scopes specified here are used to obtain a "down-scoped" access token, where the
         * set of scopes specified _must_ be a subset of those already granted in
         * previous requests.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScopes(scopes: Iterable<String>): Builder {
            scope = AsciiStringListUtil.iterableToString(scopes)
            return this
        }

        /**
         * Specifies the additional, non-standard parameters received as part of the response.
         */
        fun setAdditionalParameters(parameters: JsonObject?): Builder {
            additionalParameters = parameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Creates the token response instance.
         */
        fun build(): TokenResponse {
            return TokenResponse(
                request,
                tokenType,
                accessToken,
                accessTokenExpirationTime,
                idToken,
                refreshToken,
                scope,
                additionalParameters
            )
        }
    }

    companion object {
        /**
         * Indicates that a provided access token is a bearer token.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        const val TOKEN_TYPE_BEARER: String = "Bearer"

        @VisibleForTesting
        const val KEY_EXPIRES_AT: String = "expires_at"

        @VisibleForTesting
        const val PARAM_TOKEN_TYPE: String = "token_type"

        @VisibleForTesting
        const val PARAM_ACCESS_TOKEN: String = "access_token"

        @VisibleForTesting
        const val PARAM_EXPIRES_IN: String = "expires_in"

        @VisibleForTesting
        const val PARAM_REFRESH_TOKEN: String = "refresh_token"

        @VisibleForTesting
        const val PARAM_ID_TOKEN: String = "id_token"

        @VisibleForTesting
        const val PARAM_SCOPE: String = "scope"

        private val BUILT_IN_PARAMS: Set<String> = setOf(
            PARAM_TOKEN_TYPE,
            PARAM_ACCESS_TOKEN,
            PARAM_EXPIRES_IN,
            PARAM_REFRESH_TOKEN,
            PARAM_ID_TOKEN,
            PARAM_SCOPE
        )

        /**
         * Reads a token response from a JSON string.
         * @throws kotlinx.serialization.SerializationException if the JSON is malformed or missing required
         *     fields.
         */
        fun fromJsonString(json: String) = Json.decodeFromString<TokenResponse>(json)
    }
}