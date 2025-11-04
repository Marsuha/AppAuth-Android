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
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A response to a token request.
 *
 * @see TokenRequest
 *
 * @see "The OAuth 2.0 Authorization Framework
 */
@Suppress("unused")
class TokenResponse internal constructor(
    /**
     * The token request associated with this response.
     */
    @JvmField val request: TokenRequest,
    /**
     * The type of the token returned. Typically this is [.TOKEN_TYPE_BEARER], or some
     * other token type that the client has negotiated with the authorization service.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val tokenType: String?,
    /**
     * The access token, if provided.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val accessToken: String?,
    /**
     * The expiration time of the access token, if provided. If an access token is provided but the
     * expiration time is not, then the expiration time is typically some default value specified
     * by the identity provider through some other means, such as documentation or an additional
     * non-standard field.
     */
    @JvmField val accessTokenExpirationTime: Long?,
    /**
     * The ID token describing the authenticated user, if provided.
     *
     * @see "OpenID Connect Core 1.0, Section 2
     * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.2>"
     */
    @JvmField val idToken: String?,
    /**
     * The refresh token, if provided.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val refreshToken: String?,
    /**
     * The scope of the access token. If the scope is identical to that originally
     * requested, then this value is optional.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val scope: String?,
    /**
     * Additional, non-standard parameters in the response.
     */
    val additionalParameters: Map<String, String>
) {
    /**
     * Creates instances of [TokenResponse].
     */
    class Builder(request: TokenRequest) {
        private var mRequest: TokenRequest = request

        private var mTokenType: String? = null

        private var mAccessToken: String? = null

        private var mAccessTokenExpirationTime: Long? = null

        private var mIdToken: String? = null

        private var mRefreshToken: String? = null

        private var mScope: String? = null

        private var mAdditionalParameters: Map<String, String> = emptyMap()

        /**
         * Extracts token response fields from a JSON string.
         *
         * @throws JSONException if the JSON is malformed or has incorrect value types for fields.
         */
        @Throws(JSONException::class)
        fun fromResponseJsonString(jsonStr: String): Builder {
            require(jsonStr.isNotEmpty()) { "json cannot be empty" }
            return fromResponseJson(JSONObject(jsonStr))
        }

        /**
         * Extracts token response fields from a JSON object.
         *
         * @throws JSONException if the JSON is malformed or has incorrect value types for fields.
         */
        @Throws(JSONException::class)
        fun fromResponseJson(json: JSONObject): Builder {
            setTokenType(json.getString(KEY_TOKEN_TYPE))
            setAccessToken(json.getStringIfDefined(KEY_ACCESS_TOKEN))
            setAccessTokenExpirationTime(json.getLongIfDefined(KEY_EXPIRES_AT))

            if (json.has(KEY_EXPIRES_IN)) {
                setAccessTokenExpiresIn(json.getLong(KEY_EXPIRES_IN))
            }

            setRefreshToken(json.getStringIfDefined(KEY_REFRESH_TOKEN))
            setIdToken(json.getStringIfDefined(KEY_ID_TOKEN))
            setScope(json.getStringIfDefined(KEY_SCOPE))
            setAdditionalParameters(json.extractAdditionalParams(BUILT_IN_PARAMS))

            return this
        }

        /**
         * Specifies the request associated with this response. Must not be null.
         */
        fun setRequest(request: TokenRequest): Builder {
            mRequest = request
            return this
        }

        /**
         * Specifies the token type of the access token in this response. If not null, the value
         * must be non-empty.
         */
        fun setTokenType(tokenType: String?): Builder {
            tokenType?.let { require(it.isNotEmpty()) { "token type must not be empty if defined" } }
            mTokenType = tokenType
            return this
        }

        /**
         * Specifies the access token. If not null, the value must be non-empty.
         */
        fun setAccessToken(accessToken: String?): Builder {
            accessToken?.let { require(it.isNotEmpty()) { "access token cannot be empty if specified" } }
            mAccessToken = accessToken
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
            mAccessTokenExpirationTime = expiresIn?.let {
                (clock.currentTimeMillis + TimeUnit.SECONDS.toMillis(it))
            }

            return this
        }

        /**
         * Sets the exact expiration time of the access token, in milliseconds since the UNIX epoch.
         */
        fun setAccessTokenExpirationTime(expiresAt: Long?): Builder {
            mAccessTokenExpirationTime = expiresAt
            return this
        }

        /**
         * Specifies the ID token. If not null, the value must be non-empty.
         */
        fun setIdToken(idToken: String?): Builder {
            idToken?.let { require(it.isNotEmpty()) { "id token must not be empty if defined" } }
            mIdToken = idToken
            return this
        }

        /**
         * Specifies the refresh token. If not null, the value must be non-empty.
         */
        fun setRefreshToken(refreshToken: String?): Builder {
            refreshToken?.let { require(it.isNotEmpty()) { "refresh token must not be empty if defined" } }
            mRefreshToken = refreshToken
            return this
        }

        /**
         * Specifies the encoded scope string, which is a space-delimited set of
         * case-sensitive scope identifiers. Replaces any previously specified scope.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScope(scope: String?): Builder {
            mScope = scope?.takeIf { it.isNotEmpty() }?.let { scope ->
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
            mScope = AsciiStringListUtil.iterableToString(scopes)
            return this
        }

        /**
         * Specifies the additional, non-standard parameters received as part of the response.
         */
        fun setAdditionalParameters(additionalParameters: Map<String, String>?): Builder {
            mAdditionalParameters = additionalParameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Creates the token response instance.
         */
        fun build(): TokenResponse {
            return TokenResponse(
                mRequest,
                mTokenType,
                mAccessToken,
                mAccessTokenExpirationTime,
                mIdToken,
                mRefreshToken,
                mScope,
                mAdditionalParameters
            )
        }
    }

    /**
     * Derives the set of scopes from the consolidated, space-delimited scopes in the
     * [.scope] field. If no scopes were specified on this response, the method will
     * return `null`.
     */
    val scopeSet: Set<String>?
        get() = scope?.let { AsciiStringListUtil.stringToSet(it) }

    /**
     * Produces a JSON string representation of the token response for persistent storage or
     * local transmission (e.g. between activities).
     */
    fun jsonSerialize() = JSONObject().apply {
        put(KEY_REQUEST, request.jsonSerialize())
        tokenType?.let { put(KEY_TOKEN_TYPE, it) }
        accessToken?.let { put(KEY_ACCESS_TOKEN, it) }
        accessTokenExpirationTime?.let { put(KEY_EXPIRES_AT, it) }
        idToken?.let { put(KEY_ID_TOKEN, it) }
        refreshToken?.let { put(KEY_REFRESH_TOKEN, it) }
        scope?.let { put(KEY_SCOPE, it) }
        put(KEY_ADDITIONAL_PARAMETERS, additionalParameters.toJsonObject())
    }

    /**
     * Produces a JSON string representation of the token response for persistent storage or
     * local transmission (e.g. between activities). This method is just a convenience wrapper
     * for [.jsonSerialize], converting the JSON object to its string form.
     */
    fun jsonSerializeString() = jsonSerialize().toString()

    companion object {
        /**
         * Indicates that a provided access token is a bearer token.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        const val TOKEN_TYPE_BEARER: String = "Bearer"

        @VisibleForTesting
        const val KEY_REQUEST: String = "request"

        @VisibleForTesting
        const val KEY_EXPIRES_AT: String = "expires_at"

        // TODO: rename all KEY_* below to PARAM_*
        @VisibleForTesting
        const val KEY_TOKEN_TYPE: String = "token_type"

        @VisibleForTesting
        const val KEY_ACCESS_TOKEN: String = "access_token"

        @VisibleForTesting
        const val KEY_EXPIRES_IN: String = "expires_in"

        @VisibleForTesting
        const val KEY_REFRESH_TOKEN: String = "refresh_token"

        @VisibleForTesting
        const val KEY_ID_TOKEN: String = "id_token"

        @VisibleForTesting
        const val KEY_SCOPE: String = "scope"

        @VisibleForTesting
        const val KEY_ADDITIONAL_PARAMETERS: String = "additionalParameters"

        private val BUILT_IN_PARAMS: Set<String> = setOf(
            KEY_TOKEN_TYPE,
            KEY_ACCESS_TOKEN,
            KEY_EXPIRES_IN,
            KEY_REFRESH_TOKEN,
            KEY_ID_TOKEN,
            KEY_SCOPE
        )

        /**
         * Reads a token response from a JSON string, and associates it with the provided request.
         * If a request is not provided, its serialized form is expected to be found in the JSON
         * (as if produced by a prior call to [.jsonSerialize].
         * @throws JSONException if the JSON is malformed or missing required fields.
         */
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject): TokenResponse {
            require(json.has(KEY_REQUEST)) { "token request not provided and not found in JSON" }
            return TokenResponse(
                TokenRequest.jsonDeserialize(json.getJSONObject(KEY_REQUEST)),
                json.getStringIfDefined(KEY_TOKEN_TYPE),
                json.getStringIfDefined(KEY_ACCESS_TOKEN),
                json.getLongIfDefined(KEY_EXPIRES_AT),
                json.getStringIfDefined(KEY_ID_TOKEN),
                json.getStringIfDefined(KEY_REFRESH_TOKEN),
                json.getStringIfDefined(KEY_SCOPE),
                json.getStringMap(KEY_ADDITIONAL_PARAMETERS)
            )
        }

        /**
         * Reads a token response from a JSON string, and associates it with the provided request.
         * If a request is not provided, its serialized form is expected to be found in the JSON
         * (as if produced by a prior call to [.jsonSerialize].
         * @throws JSONException if the JSON is malformed or missing required fields.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(jsonStr: String): TokenResponse {
            require(jsonStr.isNotEmpty()) { "jsonStr cannot be empty" }
            return jsonDeserialize(JSONObject(jsonStr))
        }
    }
}
