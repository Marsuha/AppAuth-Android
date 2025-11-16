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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.openid.appauth.CodeVerifierUtil.checkCodeVerifier
import net.openid.appauth.GrantTypeValues.AUTHORIZATION_CODE
import net.openid.appauth.GrantTypeValues.REFRESH_TOKEN
import net.openid.appauth.internal.UriSerializer

/**
 * An OAuth2 token request. These are used to exchange codes for tokens, or exchange a refresh
 * token for updated tokens.
 *
 * @see "The OAuth 2.0 Authorization Framework
 */
@Suppress("unused")
@Serializable
class TokenRequest private constructor(
    /**
     * The service's [configuration][AuthorizationServiceConfiguration].
     * This configuration specifies how to connect to a particular OAuth provider.
     * Configurations may be
     * [ ][AuthorizationServiceConfiguration], or
     * [ via an OpenID Connect Discovery Document][AuthorizationServiceConfiguration.fetchFromUrl].
     */
    val configuration: AuthorizationServiceConfiguration,
    /**
     * The client identifier.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    val clientId: String,
    /**
     * The (optional) nonce associated with the current session.
     */
    val nonce: String?,
    /**
     * The type of token being sent to the token endpoint.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    val grantType: String,
    /**
     * The client's redirect URI. Required if this token request is to exchange an authorization
     * code for one or more tokens, and must be identical to the value specified in the original
     * authorization request.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    @Serializable(with = UriSerializer::class)
    val redirectUri: Uri?,
    /**
     * A space-delimited set of scopes used to determine the scope of any returned tokens.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    val scope: String?,
    /**
     * An authorization code to be exchanged for one or more tokens.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    val authorizationCode: String?,
    /**
     * A refresh token to be exchanged for a new token.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    val refreshToken: String?,
    /**
     * The code verifier that was used to generate the challenge in the original authorization
     * request, if one was used.
     *
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    val codeVerifier: String?,
    /**
     * Additional parameters to be passed as part of the request.
     */
    val additionalParameters: JsonObject
) {
    /**
     * Derives the set of scopes from the consolidated, space-delimited scopes in the
     * [.scope] field. If no scopes were specified for this request, will return `null`.
     */
    val scopeValues: Set<String>?
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
            additionalParameters.forEach { put(it.key, it.value.toUnquotedString()) }
        }

    /**
     * A JSON string representation of the token request, intended for persistent storage or
     * local transmission (e.g., between activities).
     */
    val asJsonString get() = Json.encodeToString(this)

    /**
     * Creates instances of [TokenRequest].
     */
    class Builder(
        private var configuration: AuthorizationServiceConfiguration,
        private var clientId: String
    ) {

        private var nonce: String? = null

        private var grantType: String? = null

        private var redirectUri: Uri? = null

        private var scope: String? = null

        private var authorizationCode: String? = null

        private var refreshToken: String? = null

        private var codeVerifier: String? = null

        private var additionalParameters: JsonObject = JsonObject(emptyMap())

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
        fun setConfiguration(serviceConfig: AuthorizationServiceConfiguration): Builder {
            configuration = serviceConfig
            return this
        }

        /**
         * Specifies the client ID for the token request, which must not be null or empty.
         */
        fun setClientId(clientIdValue: String): Builder {
            require(clientIdValue.isNotEmpty()) { "clientId cannot be empty" }
            clientId = clientIdValue
            return this
        }

        /**
         * Specifies the (optional) nonce for the current session.
         */
        fun setNonce(nonceValue: String?): Builder {
            nonce = nonceValue?.takeIf { it.isNotEmpty() }
            return this
        }

        /**
         * Specifies the grant type for the request, which must not be null or empty.
         */
        fun setGrantType(grantTypeValue: String): Builder {
            require(grantTypeValue.isNotEmpty()) { "grantType cannot be empty" }
            grantType = grantTypeValue
            return this
        }

        /**
         * Specifies the redirect URI for the request. This is required for authorization code
         * exchanges, but otherwise optional. If specified, the redirect URI must have a scheme.
         */
        fun setRedirectUri(uri: Uri?): Builder {
            uri?.let { checkNotNull(it.scheme) { "redirectUri must have a scheme" } }
            redirectUri = uri
            return this
        }

        /**
         * Specifies the encoded scope string, which is a space-delimited set of
         * case-sensitive scope identifiers. Replaces any previously specified scope.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScope(scopeValue: String?): Builder {
            if (!scopeValue.isNullOrEmpty()) setScopes(
                *scopeValue.split(" +")
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            ) else scope = null

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
            require(scopes.all { it.isNotBlank() }) { "scopes values must not be empty" }
            scope = AsciiStringListUtil.iterableToString(scopes)
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
        fun setAuthorizationCode(authCode: String?): Builder {
            authCode?.let { require(it.isNotEmpty()) { "authorization code must not be empty" } }
            authorizationCode = authCode
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
        fun setRefreshToken(newRefreshToken: String?): Builder {
            newRefreshToken?.let { require(it.isNotEmpty()) { "refresh token must not be empty" } }
            refreshToken = newRefreshToken
            return this
        }

        /**
         * Specifies the code verifier for an authorization code exchange request. This must match
         * the code verifier that was used to generate the challenge sent in the request that
         * produced the authorization code.
         */
        fun setCodeVerifier(verifier: String?): Builder {
            verifier?.let { checkCodeVerifier(it) }
            codeVerifier = verifier
            return this
        }

        /**
         * Specifies an additional set of parameters to be sent as part of the request.
         */
        fun setAdditionalParameters(additionalParams: JsonObject): Builder {
            additionalParameters = additionalParams.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Produces a [TokenRequest] instance, if all necessary values have been provided.
         */
        fun build(): TokenRequest {
            val grantType = inferGrantType()

            if (AUTHORIZATION_CODE == grantType) checkNotNull(authorizationCode) {
                "authorization code must be specified for grant_type = $AUTHORIZATION_CODE"
            }

            if (REFRESH_TOKEN == grantType) checkNotNull(refreshToken) {
                "refresh token must be specified for grant_type = $REFRESH_TOKEN"
            }


            check(!(grantType == AUTHORIZATION_CODE && redirectUri == null)) {
                "no redirect URI specified on token request for code exchange"
            }

            return TokenRequest(
                configuration,
                clientId,
                nonce,
                grantType,
                redirectUri,
                scope,
                authorizationCode,
                refreshToken,
                codeVerifier,
                additionalParameters
            )
        }

        private fun inferGrantType() = when {
            grantType != null -> grantType!!
            authorizationCode != null -> AUTHORIZATION_CODE
            refreshToken != null -> REFRESH_TOKEN
            else -> throw IllegalStateException("grant type not specified and cannot be inferred")
        }
    }

    companion object {
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

        fun fromJsonString(json: String) =
            Json.decodeFromString<TokenRequest>(json)
    }
}
