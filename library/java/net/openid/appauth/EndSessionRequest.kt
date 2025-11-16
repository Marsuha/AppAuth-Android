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
package net.openid.appauth

import android.net.Uri
import androidx.annotation.VisibleForTesting
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.openid.appauth.AsciiStringListUtil.stringToSet
import net.openid.appauth.internal.UriSerializer

/**
 * An OpenID end session request.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">
 *     OpenID Connect RP-Initiated Logout 1.0 - draft 01</a>
 */
@Serializable
class EndSessionRequest private constructor(
    /**
     * The service's [configuration][AuthorizationServiceConfiguration].
     * This configuration specifies how to connect to a particular OAuth provider.
     * Configurations may be created manually [AuthorizationServiceConfiguration],
     * or [AuthorizationServiceConfiguration.fetchFromUrl]
     * via an OpenID Connect Discovery Document}.
     */
    val configuration: AuthorizationServiceConfiguration,
    /**
     * Previously issued ID Token passed to the end session endpoint as a hint about the End-User's
     * current authenticated session with the Client
     *
     * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout">
     *     OpenID Connect Session Management 1.0 - draft 28, 5 RP-Initiated Logout</a>
     *
     * @see <a href="http://openid.net/specs/openid-connect-core-1_0.html#IDToken">
     *     OpenID Connect Core ID Token, Section 2</a>
     */
    @SerialName(PARAM_ID_TOKEN_HINT)
    val idTokenHint: String?,
    /**
     * The client's redirect URI.
     *
     * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RedirectionAfterLogout">
     *     OpenID Connect RP-Initiated Logout 1.0 - draft 1, 3.  Redirection to RP After Logout</a>
     */
    @Serializable(with = UriSerializer::class)
    @SerialName(PARAM_POST_LOGOUT_REDIRECT_URI)
    val postLogoutRedirectUri: Uri?,
    /**
     * An opaque value used by the client to maintain state between the request and callback. If
     * this value is not explicitly set, this library will automatically add state and perform
     * appropriate  validation of the state in the authorization response. It is recommended that
     * the default implementation of this parameter be used wherever possible. Typically used to
     * prevent CSRF attacks, as recommended in
     *
     * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout">
     *     OpenID Connect RP-Initiated Logout 1.0 - draft 1, 2.  RP-Initiated Logout</a>
     *
     * @see "The OAuth 2.0 Authorization Framework"
     */
    @SerialName(PARAM_STATE)
    override val state: String?,
    /**
     * This is a space-separated list of BCP47 RFC5646 language tag values, ordered by preference.
     * It represents End-User's preferred languages and scripts for the user interface.
     *
     * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout">
     *     OpenID Connect RP-Initiated Logout 1.0 - draft 01</a>
     */
    @SerialName(PARAM_UI_LOCALES)
    val uiLocales: String?,
    /**
     * Additional parameters to be passed as part of the request.
     *
     * @see "The OAuth 2.0 Authorization Framework"
     */
    val additionalParameters: JsonObject
) : AuthorizationManagementRequest {
    /**
     * The end-user's preferred languages and scripts for the user interface,
     * represented as a space-separated list of BCP47 `RFC5646` language tag values,
     * ordered by preference.
     *
     * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout">
     *     OpenID Connect RP-Initiated Logout 1.0 - draft 01</a>
     */
    val uiLocalesValues get() = uiLocales?.let { stringToSet(it) }

    /**
     * A JSON representation of the end session request for persistent storage or local
     * transmission (e.g. between activities).
     */
    override val asJsonString get() = Json.encodeToString(this)

    /**
     * Creates instances of [EndSessionRequest].
     */
    class Builder(private var configuration: AuthorizationServiceConfiguration) {

        private var state: String? = AuthorizationManagementUtil.randomState

        private var idTokenHint: String? = null

        private var postLogoutRedirectUri: Uri? = null

        private var uiLocales: String? = null

        private var additionalParameters: JsonObject = JsonObject(emptyMap())

        /**
         *  @see [EndSessionRequest.configuration]
         */
        @Suppress("unused")
        fun setAuthorizationServiceConfiguration(
            authServiceConfig: AuthorizationServiceConfiguration
        ): Builder {
            configuration = authServiceConfig
            return this
        }

        /** @see EndSessionRequest.idTokenHint
         */
        fun setIdTokenHint(hint: String?): Builder {
            hint?.let { require(it.isNotEmpty()) { "idTokenHint must not be empty" } }
            idTokenHint = hint
            return this
        }

        /** @see EndSessionRequest.postLogoutRedirectUri
         */
        fun setPostLogoutRedirectUri(uri: Uri?): Builder {
            postLogoutRedirectUri = uri
            return this
        }

        /** @see EndSessionRequest.state
         */
        fun setState(state: String?): Builder {
            state?.let { require(it.isNotEmpty()) { "state must not be empty" } }
            this@Builder.state = state
            return this
        }

        /** @see EndSessionRequest.uiLocales
         */
        fun setUiLocales(locales: String?): Builder {
            locales?.let { require(it.isNotEmpty()) { "uiLocales must not be empty" } }
            uiLocales = locales
            return this
        }

        /** @see EndSessionRequest.uiLocales
         */
        fun setUiLocalesValues(vararg uiLocalesValues: String): Builder {
            return setUiLocalesValues(listOf(*uiLocalesValues))
        }

        /** @see EndSessionRequest.uiLocales
         */
        fun setUiLocalesValues(values: Iterable<String>?): Builder {
            uiLocales = values?.let { uiLocales ->
                require(uiLocales.all { it.isNotBlank() }) { "uiLocales values must not be empty" }
                AsciiStringListUtil.iterableToString(uiLocales)
            }

            return this
        }

        /** @see EndSessionRequest.additionalParameters
         */
        fun setAdditionalParameters(parameters: JsonObject?): Builder {
            additionalParameters = parameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Constructs an end session request. All fields must be set.
         * Failure to specify any of these parameters will result in a runtime exception.
         */
        fun build(): EndSessionRequest {
            return EndSessionRequest(
                configuration = configuration,
                idTokenHint = idTokenHint,
                postLogoutRedirectUri = postLogoutRedirectUri,
                state = state,
                uiLocales = uiLocales,
                additionalParameters = additionalParameters
            )
        }
    }

    override fun toUri() = configuration.endSessionEndpoint?.buildUpon()?.run {
        idTokenHint?.let { appendQueryParameter(PARAM_ID_TOKEN_HINT, it) }
        state?.let { appendQueryParameter(PARAM_STATE, it) }
        uiLocales?.let { appendQueryParameter(PARAM_UI_LOCALES, it) }

        postLogoutRedirectUri?.let {
            appendQueryParameter(PARAM_POST_LOGOUT_REDIRECT_URI, it.toString())
        }

        additionalParameters.forEach { appendQueryParameter(it.key, it.value.toUnquotedString()) }
        build()
    }

    companion object {
        @VisibleForTesting
        const val PARAM_ID_TOKEN_HINT: String = "id_token_hint"

        @VisibleForTesting
        const val PARAM_POST_LOGOUT_REDIRECT_URI: String = "post_logout_redirect_uri"

        @VisibleForTesting
        const val PARAM_STATE: String = "state"

        @VisibleForTesting
        const val PARAM_UI_LOCALES: String = "ui_locales"

        private val BUILT_IN_PARAMS: Set<String> = setOf(
            PARAM_ID_TOKEN_HINT,
            PARAM_POST_LOGOUT_REDIRECT_URI,
            PARAM_STATE,
            PARAM_UI_LOCALES
        )

        fun fromJsonString(jsonStr: String): EndSessionRequest =
            Json.decodeFromString(jsonStr)
    }
}
