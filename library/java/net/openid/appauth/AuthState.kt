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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException.AuthorizationRequestErrors
import net.openid.appauth.AuthorizationException.Companion.TYPE_OAUTH_AUTHORIZATION_ERROR
import net.openid.appauth.AuthorizationException.Companion.TYPE_OAUTH_TOKEN_ERROR
import net.openid.appauth.AuthorizationException.Companion.fromTemplate
import net.openid.appauth.AuthorizationException.TokenRequestErrors.CLIENT_ERROR
import net.openid.appauth.ClientAuthentication.UnsupportedAuthenticationMethod
import net.openid.appauth.IdToken.Companion.from
import net.openid.appauth.IdToken.IdTokenException
import net.openid.appauth.internal.Logger.Companion.warn
import org.json.JSONException
import org.json.JSONObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Collects authorization state from authorization requests and responses. This facilitates
 * the creation of subsequent requests based on this state, and allows for this state to be
 * persisted easily.
 */
@Suppress("unused")
class AuthState {
    /**
     * Creates an empty, unauthenticated [AuthState].
     */
    constructor()

    /**
     * Creates an unauthenticated [AuthState], with the service configuration retained
     * for convenience.
     */
    constructor(config: AuthorizationServiceConfiguration) {
        this@AuthState.config = config
    }

    /**
     * Creates an [AuthState] based on an authorization exchange.
     */
    constructor(
        authResponse: AuthorizationResponse?,
        authError: AuthorizationException?
    ) {
        require((authResponse != null) xor (authError != null)) {
            "exactly one of authResponse or authError should be non-null"
        }

        pendingRefreshActions = null
        update(authResponse, authError)
    }

    /**
     * Creates an [AuthState] based on a dynamic registration client registration request.
     */
    constructor(regResponse: RegistrationResponse) {
        update(regResponse)
    }

    /**
     * Creates an [AuthState] based on an authorization exchange and subsequent token
     * exchange.
     */
    constructor(
        authResponse: AuthorizationResponse,
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) : this(authResponse, null) {
        update(tokenResponse, authException)
    }

    /**
     * The most recent refresh token received from the server, if available. Rather than using
     * this property directly as part of any request depending on authorization state, it is
     * recommended to call [performActionWithFreshTokens][.performActionWithFreshTokens] to ensure that fresh tokens are available.
     */
    var refreshToken: String? = null
        private set

    /**
     * The scope of the current authorization grant. This represents the latest scope returned by
     * the server and may be a subset of the scope that was initially granted.
     */
    var scope: String? = null
        private set

    private var config: AuthorizationServiceConfiguration? = null

    /**
     * The most recent authorization response used to update the authorization state. For the
     * implicit flow, this will contain the latest access token. It is rarely necessary to
     * directly use the response; instead convenience methods are provided to retrieve the
     * [access token][.getAccessToken],
     * [access token expiration][.getAccessTokenExpirationTime],
     * [ID token][.getIdToken]
     * and [scope][.getScopeSet] regardless of the flow used to retrieve them.
     */
    var lastAuthorizationResponse: AuthorizationResponse? = null
        private set

    /**
     * The most recent token response used to update this authorization state. For the
     * authorization code flow, this will contain the latest access token. It is rarely necessary
     * to directly use the response; instead convenience methods are provided to retrieve the
     * [access token][.getAccessToken],
     * [access token expiration][.getAccessTokenExpirationTime],
     * [ID token][.getIdToken]
     * and [scope][.getScopeSet] regardless of the flow used to retrieve them.
     */
    var lastTokenResponse: TokenResponse? = null
        private set

    /**
     * The most recent client registration response used to update this authorization state.
     *
     *
     *
     * It is rarely necessary to directly use the response; instead convenience methods are provided
     * to retrieve the [client secret][.getClientSecret] and
     * [client secret expiration][.getClientSecretExpirationTime].
     *
     */
    var lastRegistrationResponse: RegistrationResponse? = null
        private set

    /**
     * If the last response was an OAuth related failure, this returns the exception describing
     * the failure.
     */
    var authorizationException: AuthorizationException? = null
        private set

    private val mutex = Mutex()
    private var pendingRefreshActions: MutableList<suspend (FreshTokenResult) -> Unit>? = null
    private var isTokenRefreshForced = false

    /**
     * A set representation of [.getScope], for convenience.
     */
    val scopeValues: Set<String>?
        get() = scope?.let { AsciiStringListUtil.stringToSet(it) }

    /**
     * The configuration of the authorization service associated with this authorization state.
     */
    val authorizationServiceConfiguration: AuthorizationServiceConfiguration?
        get() = lastAuthorizationResponse?.request?.configuration ?: config

    /**
     * The current access token, if available. Rather than using
     * this property directly as part of any request depending on authorization state, it s
     * recommended to call [performActionWithFreshTokens][.performActionWithFreshTokens] to ensure that fresh tokens are available.
     */
    val accessToken: String?
        get() = when {
            authorizationException != null -> null
            lastTokenResponse?.accessToken != null -> lastTokenResponse!!.accessToken
            lastAuthorizationResponse?.accessToken != null -> lastAuthorizationResponse!!.accessToken
            else -> null
        }

    /**
     * The expiration time of the current access token (if available), as milliseconds from the
     * UNIX epoch (consistent with [System.currentTimeMillis]).
     */
    val accessTokenExpirationTime: Long?
        get() = when {
            authorizationException != null -> null
            lastTokenResponse?.accessToken != null -> lastTokenResponse!!.accessTokenExpirationTime
            lastAuthorizationResponse?.accessToken != null -> lastAuthorizationResponse!!.accessTokenExpirationTime
            else -> null
        }

    /**
     * The current ID token, if available.
     */
    val idToken: String?
        get() = when {
            authorizationException != null -> null
            lastTokenResponse?.idToken != null -> lastTokenResponse!!.idToken
            lastAuthorizationResponse != null -> lastAuthorizationResponse!!.idToken
            else -> null
        }

    /**
     * The current parsed ID token, if available.
     */
    val parsedIdToken: IdToken?
        get() = idToken?.let {
            try {
                from(it)
            } catch (_: JSONException) {
                null
            } catch (_: IdTokenException) {
                null
            }
        }

    /**
     * The current client secret, if available.
     */
    val clientSecret: String?
        get() = lastRegistrationResponse?.clientSecret

    /**
     * The expiration time of the current client credentials (if available), as milliseconds from
     * the UNIX epoch (consistent with [System.currentTimeMillis]). If the value is 0, the
     * client credentials will not expire.
     */
    val clientSecretExpirationTime: Long?
        get() = lastRegistrationResponse?.clientSecretExpiresAt

    /**
     * Determines whether the current state represents a successful authorization,
     * from which at least either an access token or an ID token have been retrieved.
     */
    val isAuthorized: Boolean
        get() = authorizationException == null && (accessToken != null || idToken != null)

    /**
     * Determines whether the access token is considered to have expired. If no refresh token
     * has been acquired, then this method will always return `false`. A token refresh
     * can be forced, regardless of the validity of any currently acquired access token, by
     * calling [setNeedsTokenRefresh(true)][.setNeedsTokenRefresh].
     * Sets whether to force an access token refresh, regardless of the current access token's
     * expiration time.
     */
    var needsTokenRefresh: Boolean
        get() = getNeedsTokenRefresh(SystemClock)
        set(needsTokenRefresh) {
            isTokenRefreshForced = needsTokenRefresh
        }

    /**
     * Creates the required client authentication for the token endpoint based on information
     * in the most recent registration response (if it is set).
     *
     * @throws ClientAuthentication.UnsupportedAuthenticationMethod if the expected client
     * authentication method is unsupported by this client library.
     */
    @get:Throws(UnsupportedAuthenticationMethod::class)
    val clientAuthentication: ClientAuthentication
        get() {
            val clientSecret = clientSecret
            val tokenEndpointAuthMethod = lastRegistrationResponse?.tokenEndpointAuthMethod

            when {
                /* Without client credentials, or unspecified 'token_endpoint_auth_method',
                  * we can never authenticate */
                clientSecret == null -> return NoClientAuthentication
                /* 'token_endpoint_auth_method': "If omitted, the default is client_secret_basic",
                  * "OpenID Connect Dynamic Client Registration 1.0", Section 2 */
                tokenEndpointAuthMethod == null -> return ClientSecretBasic(clientSecret)
            }

            return when (tokenEndpointAuthMethod) {
                ClientSecretBasic.NAME -> ClientSecretBasic(clientSecret)
                ClientSecretPost.NAME -> ClientSecretPost(clientSecret)
                NoClientAuthentication.NAME -> NoClientAuthentication
                else -> throw UnsupportedAuthenticationMethod(tokenEndpointAuthMethod)
            }
        }

    @VisibleForTesting
    fun getNeedsTokenRefresh(clock: Clock): Boolean {
        if (isTokenRefreshForced) return true

        return accessTokenExpirationTime?.let {
            (it <= clock.currentTimeMillis + EXPIRY_TIME_TOLERANCE_MS)
        } ?: (accessToken == null)
    }

    /**
     * Determines whether the client credentials is considered to have expired. If no client
     * credentials have been acquired, then this method will always return `false`
     */
    fun hasClientSecretExpired() = hasClientSecretExpired(SystemClock)

    @VisibleForTesting
    fun hasClientSecretExpired(clock: Clock): Boolean {
        if (clientSecretExpirationTime == null || clientSecretExpirationTime == 0L) {
            // no explicit expiration time, and 0 means it will not expire
            return false
        }

        return clientSecretExpirationTime!! <= clock.currentTimeMillis
    }

    /**
     * Updates the authorization state based on a new authorization response.
     */
    @OptIn(ExperimentalContracts::class)
    fun update(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?
    ) {
        contract {
            returns() implies (((authResponse != null) && (authException == null))
                    || ((authResponse == null) && (authException != null)))
        }

        require((authResponse != null) xor (authException != null)) {
            "exactly one of authResponse or authException should be non-null"
        }

        authException?.let {
            if (it.type == TYPE_OAUTH_AUTHORIZATION_ERROR) authorizationException = it
            return
        }

        // the last token response and refresh token are now stale, as they are associated with
        // any previous authorization response
        lastAuthorizationResponse = authResponse
        config = null
        lastTokenResponse = null
        refreshToken = null
        authorizationException = null

        // if the response's mScope is null, it means that it equals that of the request
        // see: https://tools.ietf.org/html/rfc6749#section-5.1
        scope = authResponse?.scope ?: authResponse?.request?.scope

    }

    /**
     * Updates the authorization state based on a new token response.
     */
    @OptIn(ExperimentalContracts::class)
    fun update(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        contract {
            returns() implies (((tokenResponse != null) && (authException == null))
                    || ((tokenResponse == null) && (authException != null)))
        }

        require((tokenResponse != null) xor (authException != null)) {
            "exactly one of tokenResponse or authException should be non-null"
        }

        authorizationException?.let {
            // Calling updateFromTokenResponse while in an error state probably means the developer
            // obtained a new token and did the exchange without also calling
            // updateFromAuthorizationResponse. Attempt to handle this gracefully, but warn the
            // developer that this is unexpected.
            warn(
                "AuthState.update should not be called in an error state (%s), call update"
                        + " with the result of the fresh authorization response first",
                it
            )

            authorizationException = null
        }

        authException?.let {
            if (it.type == TYPE_OAUTH_TOKEN_ERROR) authorizationException = it
            return
        }

        lastTokenResponse = tokenResponse
        tokenResponse?.scope?.let { scope = it }
        tokenResponse?.refreshToken?.let { refreshToken = it }
    }

    /**
     * Updates the authorization state based on a new client registration response.
     */
    fun update(regResponse: RegistrationResponse?) {
        lastRegistrationResponse = regResponse

        // a new client registration will have a new client id, so invalidate the current session.
        // Note however that we do not discard the configuration; this is likely still applicable.
        config = authorizationServiceConfiguration

        refreshToken = null
        scope = null
        lastAuthorizationResponse = null
        lastTokenResponse = null
        authorizationException = null
    }

    /**
     * Ensures that a non-expired access token is available before invoking the provided action.
     *
     * This method will automatically attempt to refresh the access token if it is expired or
     * about to expire. If a token refresh is already in progress due to another concurrent call,
     * this action will be queued and executed after the new tokens are available.
     *
     * The provided `action` is guaranteed to be invoked with either a fresh token or an error.
     *
     * @param service The `AuthorizationService` to use for the token refresh request.
     * @param clientAuth The `ClientAuthentication` method to use for the request. Defaults to
     * the value derived from the last registration response.
     * @param refreshTokenAdditionalParams Additional parameters to include in the token refresh
     * request.
     * @param action The action to be executed with the result. This lambda will receive a
     * `FreshTokenResult` which is either a `Success` containing the tokens or a `Failure`
     * containing the `AuthorizationException`.
     */
    @JvmOverloads
    suspend fun performActionWithFreshTokens(
        service: AuthorizationService,
        clientAuth: ClientAuthentication = clientAuthentication,
        refreshTokenAdditionalParams: Map<String, String> = emptyMap(),
        action: suspend (FreshTokenResult) -> Unit
    ) {
        try {
            performActionWithFreshTokens(
                service,
                clientAuth,
                refreshTokenAdditionalParams,
                SystemClock,
                action
            )
        } catch (ex: UnsupportedAuthenticationMethod) {
            action(FreshTokenResult.Failure(fromTemplate(CLIENT_ERROR, ex)))
        }
    }

    /**
     * Ensures that a non-expired access token is available before invoking the provided action.
     *
     * This method will automatically attempt to refresh the access token if it is expired or
     * about to expire. The token is considered expired if `(expiration time - current time) <
     * 60 seconds`.
     *
     * If a token refresh is already in progress due to another concurrent call, the provided
     * `action` will be queued and executed after the new tokens are available.
     *
     * The `action` is guaranteed to be invoked with either a fresh token or an error.
     *
     * @param service The `AuthorizationService` to use for the token request.
     * @param clientAuth The client authentication method to use for the token request.
     * @param refreshTokenAdditionalParams Additional parameters to be sent in the refresh token
     * request.
     * @param clock The clock to use for checking token expiration.
     * @param action The suspendable lambda to execute with the result. This lambda will receive
     * a [FreshTokenResult] which is either a `Success` containing the tokens or a `Failure`
     * containing the [AuthorizationException].
     */
    @VisibleForTesting
    suspend fun performActionWithFreshTokens(
        service: AuthorizationService,
        clientAuth: ClientAuthentication,
        refreshTokenAdditionalParams: Map<String, String>,
        clock: Clock,
        action: suspend (result: FreshTokenResult) -> Unit
    ) {
        if (!getNeedsTokenRefresh(clock)) {
            return action(FreshTokenResult.Success(accessToken, idToken))
        }

        if (refreshToken == null) {
            val ex = fromTemplate(
                AuthorizationRequestErrors.CLIENT_ERROR,
                IllegalStateException("No refresh token available and token have expired")
            )

            return action(FreshTokenResult.Failure(ex))
        }

        mutex.withLock {
            //if a token request is currently executing, queue the actions instead
            pendingRefreshActions?.let {
                it.add(action)
                return
            }

            //creates a list of pending actions, starting with the current action
            pendingRefreshActions = mutableListOf(action)
        }

        val finalResult = try {
            val tokenResponse = withContext(Dispatchers.IO) {
                service.performTokenRequest(
                    createTokenRefreshRequest(refreshTokenAdditionalParams),
                    clientAuth
                )
            }

            update(tokenResponse = tokenResponse, authException = null)
            isTokenRefreshForced = false
            FreshTokenResult.Success(this@AuthState.accessToken, this@AuthState.idToken)
        } catch (ex: AuthorizationException) {
            update(tokenResponse = null, authException = ex)
            FreshTokenResult.Failure(ex)
        }

        val actionsToProcess = mutex.withLock {
            val actions = pendingRefreshActions!!.toList()
            pendingRefreshActions = null
            actions
        }

        actionsToProcess.forEach { it(finalResult) }
    }

    /**
     * Creates a token request for new tokens using the current refresh token, adding the
     * specified additional parameters.
     */
    /**
     * Creates a token request for new tokens using the current refresh token.
     */
    @JvmOverloads
    fun createTokenRefreshRequest(
        additionalParameters: Map<String, String> = emptyMap()
    ): TokenRequest {
        checkNotNull(refreshToken) { "No refresh token available for refresh request" }
        checkNotNull(lastAuthorizationResponse) { "No authorization configuration available for refresh request" }

        return TokenRequest.Builder(
            lastAuthorizationResponse!!.request.configuration,
            lastAuthorizationResponse!!.request.clientId
        )
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setScope(null)
            .setRefreshToken(refreshToken)
            .setAdditionalParameters(additionalParameters)
            .build()
    }

    /**
     * Produces a JSON representation of the authorization state for persistent storage or local
     * transmission (e.g. between activities).
     */
    fun jsonSerialize() = JSONObject().apply {
        refreshToken?.let { put(KEY_REFRESH_TOKEN, it) }
        scope?.let { put(KEY_SCOPE, it) }
        config?.let { put(KEY_CONFIG, it.toJson()) }
        authorizationException?.let { put(KEY_AUTHORIZATION_EXCEPTION, it.toJson()) }
        lastAuthorizationResponse?.let { put(KEY_LAST_AUTHORIZATION_RESPONSE, it.jsonSerialize()) }
        lastTokenResponse?.let { put(KEY_LAST_TOKEN_RESPONSE, it.jsonSerialize()) }
        lastRegistrationResponse?.let { put(KEY_LAST_REGISTRATION_RESPONSE, it.jsonSerialize()) }
    }

    /**
     * Produces a JSON string representation of the authorization state for persistent storage or
     * local transmission (e.g. between activities). This method is just a convenience wrapper
     * for [.jsonSerialize], converting the JSON object to its string form.
     */
    fun jsonSerializeString() = jsonSerialize().toString()

    sealed class FreshTokenResult {
        data class Success(val accessToken: String?, val idToken: String?) : FreshTokenResult()
        data class Failure(val exception: AuthorizationException) : FreshTokenResult()
    }

    companion object {
        /**
         * Tokens which have less time than this value left before expiry will be considered to be
         * expired for the purposes of calls to
         * [ performActionWithFreshTokens][.performActionWithFreshTokens].
         */
        const val EXPIRY_TIME_TOLERANCE_MS: Int = 60000

        private const val KEY_CONFIG = "config"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
        private const val KEY_SCOPE = "scope"
        private const val KEY_LAST_AUTHORIZATION_RESPONSE = "lastAuthorizationResponse"
        private const val KEY_LAST_TOKEN_RESPONSE = "mLastTokenResponse"
        private const val KEY_AUTHORIZATION_EXCEPTION = "mAuthorizationException"
        private const val KEY_LAST_REGISTRATION_RESPONSE = "lastRegistrationResponse"

        /**
         * Reads an authorization state instance from a JSON string representation produced by
         * [.jsonSerialize].
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject) = AuthState().apply {
            refreshToken = json.getStringIfDefined(KEY_REFRESH_TOKEN)
            scope = json.getStringIfDefined(KEY_SCOPE)

            if (json.has(KEY_CONFIG)) {
                config = AuthorizationServiceConfiguration.fromJson(
                    json.getJSONObject(KEY_CONFIG)
                )
            }

            if (json.has(KEY_AUTHORIZATION_EXCEPTION)) {
                authorizationException = AuthorizationException.fromJson(
                    json.getJSONObject(KEY_AUTHORIZATION_EXCEPTION)
                )
            }

            if (json.has(KEY_LAST_AUTHORIZATION_RESPONSE)) {
                lastAuthorizationResponse = AuthorizationResponse.jsonDeserialize(
                    json.getJSONObject(KEY_LAST_AUTHORIZATION_RESPONSE)
                )
            }

            if (json.has(KEY_LAST_TOKEN_RESPONSE)) {
                lastTokenResponse = TokenResponse.jsonDeserialize(
                    json.getJSONObject(KEY_LAST_TOKEN_RESPONSE)
                )
            }

            if (json.has(KEY_LAST_REGISTRATION_RESPONSE)) {
                lastRegistrationResponse = RegistrationResponse.jsonDeserialize(
                    json.getJSONObject(KEY_LAST_REGISTRATION_RESPONSE)
                )
            }
        }

        /**
         * Reads an authorization state instance from a JSON string representation produced by
         * [.jsonSerializeString]. This method is just a convenience wrapper for
         * [.jsonDeserialize], converting the JSON string to its JSON object form.
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(jsonStr: String) = jsonDeserialize(JSONObject(jsonStr))
    }
}
