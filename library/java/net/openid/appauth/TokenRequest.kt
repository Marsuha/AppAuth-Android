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
import net.openid.appauth.AuthorizationServiceConfiguration.Companion.fromJson
import net.openid.appauth.CodeVerifierUtil.checkCodeVerifier
import net.openid.appauth.GrantTypeValues.AUTHORIZATION_CODE
import net.openid.appauth.GrantTypeValues.REFRESH_TOKEN
import org.json.JSONException
import org.json.JSONObject

/**
 * An OAuth2 token request. These are used to exchange codes for tokens, or exchange a refresh
 * token for updated tokens.
 *
 * @see "The OAuth 2.0 Authorization Framework
 */
@Suppress("unused")
class TokenRequest private constructor(
    /**
     * The service's [configuration][AuthorizationServiceConfiguration].
     * This configuration specifies how to connect to a particular OAuth provider.
     * Configurations may be
     * [ ][AuthorizationServiceConfiguration], or
     * [ via an OpenID Connect Discovery Document][AuthorizationServiceConfiguration.fetchFromUrl].
     */
    @JvmField val configuration: AuthorizationServiceConfiguration,
    /**
     * The client identifier.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val clientId: String,
    /**
     * The (optional) nonce associated with the current session.
     */
    @JvmField val nonce: String?,
    /**
     * The type of token being sent to the token endpoint.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val grantType: String,
    /**
     * The client's redirect URI. Required if this token request is to exchange an authorization
     * code for one or more tokens, and must be identical to the value specified in the original
     * authorization request.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    val redirectUri: Uri?,
    /**
     * A space-delimited set of scopes used to determine the scope of any returned tokens.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val scope: String?,
    /**
     * An authorization code to be exchanged for one or more tokens.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val authorizationCode: String?,
    /**
     * A refresh token to be exchanged for a new token.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val refreshToken: String?,
    /**
     * The code verifier that was used to generate the challenge in the original authorization
     * request, if one was used.
     *
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    @JvmField val codeVerifier: String?,
    /**
     * Additional parameters to be passed as part of the request.
     */
    val additionalParameters: Map<String, String>
) {
    /**
     * Creates instances of [TokenRequest].
     */
    class Builder(
        configuration: AuthorizationServiceConfiguration,
        clientId: String
    ) {
        private var mConfiguration: AuthorizationServiceConfiguration = configuration

        private var mClientId: String = clientId

        private var mNonce: String? = null

        private var mGrantType: String? = null

        private var mRedirectUri: Uri? = null

        private var mScope: String? = null

        private var mAuthorizationCode: String? = null

        private var mRefreshToken: String? = null

        private var mCodeVerifier: String? = null

        private var mAdditionalParameters: Map<String, String> = emptyMap()

        /**
         * Creates a token request builder with the specified mandatory properties.
         */
        init {
            require(clientId.isNotEmpty()) { "clientId cannot be empty" }
        }

        /**
         * Specifies the authorization service configuration for the request, which must not
         * be null or empty.
         */
        fun setConfiguration(configuration: AuthorizationServiceConfiguration): Builder {
            mConfiguration = configuration
            return this
        }

        /**
         * Specifies the client ID for the token request, which must not be null or empty.
         */
        fun setClientId(clientId: String): Builder {
            require(clientId.isNotEmpty()) { "clientId cannot be empty" }
            mClientId = clientId
            return this
        }

        /**
         * Specifies the (optional) nonce for the current session.
         */
        fun setNonce(nonce: String?): Builder {
            mNonce = nonce?.takeIf { it.isNotEmpty() }
            return this
        }

        /**
         * Specifies the grant type for the request, which must not be null or empty.
         */
        fun setGrantType(grantType: String): Builder {
            require(grantType.isNotEmpty()) { "grantType cannot be empty" }
            mGrantType = grantType
            return this
        }

        /**
         * Specifies the redirect URI for the request. This is required for authorization code
         * exchanges, but otherwise optional. If specified, the redirect URI must have a scheme.
         */
        fun setRedirectUri(redirectUri: Uri?): Builder {
            redirectUri?.let { checkNotNull(it.scheme) { "redirectUri must have a scheme" } }
            mRedirectUri = redirectUri
            return this
        }

        /**
         * Specifies the encoded scope string, which is a space-delimited set of
         * case-sensitive scope identifiers. Replaces any previously specified scope.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScope(scope: String?): Builder {
            if (!scope.isNullOrEmpty()) {
                setScopes(*scope.split(" +").dropLastWhile { it.isEmpty() }.toTypedArray())
            } else mScope = null

            return this
        }

        /**
         * Specifies the set of case-sensitive scopes. Replaces any previously specified set of
         * scopes. Individual scope strings cannot be null or empty.
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
         * Specifies the authorization code for the request. If provided, the authorization code
         * must not be empty.
         *
         * Specifying an authorization code normally implies that this is a request to exchange
         * this authorization code for one or more tokens. If this is not intended, the grant type
         * should be explicitly set.
         */
        fun setAuthorizationCode(authorizationCode: String?): Builder {
            authorizationCode?.let { require(it.isNotEmpty()) { "authorization code must not be empty" } }
            mAuthorizationCode = authorizationCode
            return this
        }

        /**
         * Specifies the refresh token for the request. If a non-null value is provided, it must
         * not be empty.
         *
         * Specifying a refresh token normally implies that this is a request to exchange the
         * refresh token for a new token. If this is not intended, the grant type should be
         * explicit set.
         */
        fun setRefreshToken(refreshToken: String?): Builder {
            refreshToken?.let { require(it.isNotEmpty()) { "refresh token must not be empty" } }
            mRefreshToken = refreshToken
            return this
        }

        /**
         * Specifies the code verifier for an authorization code exchange request. This must match
         * the code verifier that was used to generate the challenge sent in the request that
         * produced the authorization code.
         */
        fun setCodeVerifier(codeVerifier: String?): Builder {
            codeVerifier?.let { checkCodeVerifier(it) }
            mCodeVerifier = codeVerifier
            return this
        }

        /**
         * Specifies an additional set of parameters to be sent as part of the request.
         */
        fun setAdditionalParameters(additionalParameters: Map<String, String>?): Builder {
            mAdditionalParameters = additionalParameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Produces a [TokenRequest] instance, if all necessary values have been provided.
         */
        fun build(): TokenRequest {
            val grantType = inferGrantType()

            if (AUTHORIZATION_CODE == grantType) checkNotNull(mAuthorizationCode) {
                "authorization code must be specified for grant_type = $AUTHORIZATION_CODE"
            }

            if (REFRESH_TOKEN == grantType) checkNotNull(mRefreshToken) {
                "refresh token must be specified for grant_type = $REFRESH_TOKEN"
            }


            check(!(grantType == AUTHORIZATION_CODE && mRedirectUri == null)) {
                "no redirect URI specified on token request for code exchange"
            }

            return TokenRequest(
                mConfiguration,
                mClientId,
                mNonce,
                grantType,
                mRedirectUri,
                mScope,
                mAuthorizationCode,
                mRefreshToken,
                mCodeVerifier,
                mAdditionalParameters
            )
        }

        private fun inferGrantType() = when {
            mGrantType != null -> mGrantType!!
            mAuthorizationCode != null -> AUTHORIZATION_CODE
            mRefreshToken != null -> REFRESH_TOKEN
            else -> throw IllegalStateException("grant type not specified and cannot be inferred")
        }
    }

    /**
     * Derives the set of scopes from the consolidated, space-delimited scopes in the
     * [.scope] field. If no scopes were specified for this request, will return `null`.
     */
    val scopeSet: Set<String>?
        get() = scope?.let { AsciiStringListUtil.stringToSet(it) }

    /**
     * Produces the set of request parameters for this query, which can be further
     * processed into a request body.
     */
    val requestParameters: Map<String, String>
        get() = buildMap {
            put(PARAM_GRANT_TYPE, grantType)
            redirectUri?.let { put(PARAM_REDIRECT_URI, it.toString()) }
            authorizationCode?.let { put(PARAM_CODE, it) }
            refreshToken?.let { put(PARAM_REFRESH_TOKEN, it) }
            codeVerifier?.let { put(PARAM_CODE_VERIFIER, it) }
            scope?.let { put(PARAM_SCOPE, it) }
            additionalParameters.forEach { put(it.key, it.value) }
        }

    /**
     * Produces a JSON string representation of the token request for persistent storage or
     * local transmission (e.g. between activities).
     */
    fun jsonSerialize() = JSONObject().apply {
        put(KEY_CONFIGURATION, configuration.toJson())
        put(KEY_CLIENT_ID, clientId)
        nonce?.let { put(KEY_NONCE, it) }
        put(KEY_GRANT_TYPE, grantType)
        redirectUri?.let { put(KEY_REDIRECT_URI, it.toString()) }
        scope?.let { put(KEY_SCOPE, it) }
        authorizationCode?.let { put(KEY_AUTHORIZATION_CODE, it) }
        refreshToken?.let { put(KEY_REFRESH_TOKEN, it) }
        codeVerifier?.let { put(KEY_CODE_VERIFIER, it) }
        put(KEY_ADDITIONAL_PARAMETERS, additionalParameters.toJsonObject())
    }

    /**
     * Produces a JSON string representation of the token request for persistent storage or
     * local transmission (e.g. between activities). This method is just a convenience wrapper
     * for [.jsonSerialize], converting the JSON object to its string form.
     */
    fun jsonSerializeString() = jsonSerialize().toString()

    companion object {
        @VisibleForTesting
        const val KEY_CONFIGURATION: String = "configuration"

        @VisibleForTesting
        const val KEY_CLIENT_ID: String = "clientId"

        @VisibleForTesting
        const val KEY_NONCE: String = "nonce"

        @VisibleForTesting
        const val KEY_GRANT_TYPE: String = "grantType"

        @VisibleForTesting
        const val KEY_REDIRECT_URI: String = "redirectUri"

        @VisibleForTesting
        const val KEY_SCOPE: String = "scope"

        @VisibleForTesting
        const val KEY_AUTHORIZATION_CODE: String = "authorizationCode"

        @VisibleForTesting
        const val KEY_REFRESH_TOKEN: String = "refreshToken"

        @VisibleForTesting
        const val KEY_CODE_VERIFIER: String = "codeVerifier"

        @VisibleForTesting
        const val KEY_ADDITIONAL_PARAMETERS: String = "additionalParameters"

        const val PARAM_CLIENT_ID: String = "client_id"

        @VisibleForTesting
        const val PARAM_CODE: String = "code"

        @VisibleForTesting
        const val PARAM_CODE_VERIFIER: String = "code_verifier"

        @VisibleForTesting
        const val PARAM_GRANT_TYPE: String = "grant_type"

        @VisibleForTesting
        const val PARAM_REDIRECT_URI: String = "redirect_uri"

        @VisibleForTesting
        const val PARAM_REFRESH_TOKEN: String = "refresh_token"

        @VisibleForTesting
        const val PARAM_SCOPE: String = "scope"

        private val BUILT_IN_PARAMS: Set<String> = setOf(
            PARAM_CLIENT_ID,
            PARAM_CODE,
            PARAM_CODE_VERIFIER,
            PARAM_GRANT_TYPE,
            PARAM_REDIRECT_URI,
            PARAM_REFRESH_TOKEN,
            PARAM_SCOPE
        )


        /**
         * The grant type used when requesting an access token using a username and password.
         * This grant type is not directly supported by this library.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        const val GRANT_TYPE_PASSWORD: String = "password"

        /**
         * The grant type used when requesting an access token using client credentials, typically
         * TLS client certificates. This grant type is not directly supported by this library.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        const val GRANT_TYPE_CLIENT_CREDENTIALS: String = "client_credentials"

        /**
         * Reads a token request from a JSON string representation produced by
         * [.jsonSerialize].
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject) = TokenRequest(
            configuration = fromJson(json.getJSONObject(KEY_CONFIGURATION)),
            clientId = json.getString(KEY_CLIENT_ID),
            nonce = json.getStringIfDefined(KEY_NONCE),
            grantType = json.getString(KEY_GRANT_TYPE),
            redirectUri = json.getUriIfDefined(KEY_REDIRECT_URI),
            scope = json.getStringIfDefined(KEY_SCOPE),
            authorizationCode = json.getStringIfDefined(KEY_AUTHORIZATION_CODE),
            refreshToken = json.getStringIfDefined(KEY_REFRESH_TOKEN),
            codeVerifier = json.getStringIfDefined(KEY_CODE_VERIFIER),
            additionalParameters = json.getStringMap(KEY_ADDITIONAL_PARAMETERS)
        )

        /**
         * Reads a token request from a JSON string representation produced by
         * [.jsonSerializeString]. This method is just a convenience wrapper for
         * [.jsonDeserialize], converting the JSON string to its JSON object form.
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @Throws(JSONException::class)
        fun jsonDeserialize(json: String) = jsonDeserialize(JSONObject(json))
    }
}
