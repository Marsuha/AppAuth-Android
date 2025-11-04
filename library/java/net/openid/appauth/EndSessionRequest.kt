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
import net.openid.appauth.AsciiStringListUtil.stringToSet
import org.json.JSONException
import org.json.JSONObject

/**
 * An OpenID end session request.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">
 *     OpenID Connect RP-Initiated Logout 1.0 - draft 01</a>
 */
@Suppress("unused")
class EndSessionRequest private constructor(
    /**
     * The service's [configuration][AuthorizationServiceConfiguration].
     * This configuration specifies how to connect to a particular OAuth provider.
     * Configurations may be created manually [AuthorizationServiceConfiguration],
     * or [AuthorizationServiceConfiguration.fetchFromUrl]
     * via an OpenID Connect Discovery Document}.
     */
    @JvmField val configuration: AuthorizationServiceConfiguration,
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
    @JvmField val idTokenHint: String?,
    /**
     * The client's redirect URI.
     *
     * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RedirectionAfterLogout">
     *     OpenID Connect RP-Initiated Logout 1.0 - draft 1, 3.  Redirection to RP After Logout</a>
     */
    @JvmField val postLogoutRedirectUri: Uri?,
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
    override val state: String?,
    /**
     * This is a space-separated list of BCP47 RFC5646 language tag values, ordered by preference.
     * It represents End-User's preferred languages and scripts for the user interface.
     *
     * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout">
     *     OpenID Connect RP-Initiated Logout 1.0 - draft 01</a>
     */
    @JvmField val uiLocales: String?,
    /**
     * Additional parameters to be passed as part of the request.
     *
     * @see "The OAuth 2.0 Authorization Framework"
     */
    @JvmField val additionalParameters: Map<String, String>
) : AuthorizationManagementRequest {
    /**
     * Creates instances of [EndSessionRequest].
     */
    class Builder(configuration: AuthorizationServiceConfiguration) {
        private var mConfiguration: AuthorizationServiceConfiguration = configuration

        private var mState: String? = AuthorizationManagementUtil.randomState

        private var mIdTokenHint: String? = null

        private var mPostLogoutRedirectUri: Uri? = null

        private var mUiLocales: String? = null

        private var mAdditionalParameters: Map<String, String> = emptyMap()

        /**
         *  @see [EndSessionRequest.configuration]
         */
        fun setAuthorizationServiceConfiguration(
            configuration: AuthorizationServiceConfiguration
        ): Builder {
            mConfiguration = configuration
            return this
        }

        /** @see EndSessionRequest.idTokenHint
         */
        fun setIdTokenHint(idTokenHint: String?): Builder {
            idTokenHint?.let { require(it.isNotEmpty()) { "idTokenHint must not be empty" } }
            mIdTokenHint = idTokenHint
            return this
        }

        /** @see EndSessionRequest.postLogoutRedirectUri
         */
        fun setPostLogoutRedirectUri(postLogoutRedirectUri: Uri?): Builder {
            mPostLogoutRedirectUri = postLogoutRedirectUri
            return this
        }

        /** @see EndSessionRequest.state
         */
        fun setState(state: String?): Builder {
            state?.let { require(it.isNotEmpty()) { "state must not be empty" } }
            mState = state
            return this
        }

        /** @see EndSessionRequest.uiLocales
         */
        fun setUiLocales(uiLocales: String?): Builder {
            uiLocales?.let { require(it.isNotEmpty()) { "uiLocales must not be empty" } }
            mUiLocales = uiLocales
            return this
        }

        /** @see EndSessionRequest.uiLocales
         */
        fun setUiLocalesValues(vararg uiLocalesValues: String): Builder {
            return setUiLocalesValues(listOf(*uiLocalesValues))
        }

        /** @see EndSessionRequest.uiLocales
         */
        fun setUiLocalesValues(uiLocalesValues: Iterable<String>?): Builder {
            mUiLocales = uiLocalesValues?.let { AsciiStringListUtil.iterableToString(it) }
            return this
        }

        /** @see EndSessionRequest.additionalParameters
         */
        fun setAdditionalParameters(additionalParameters: Map<String, String>?): Builder {
            mAdditionalParameters = additionalParameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Constructs an end session request. All fields must be set.
         * Failure to specify any of these parameters will result in a runtime exception.
         */
        fun build(): EndSessionRequest {
            return EndSessionRequest(
                configuration = mConfiguration,
                idTokenHint = mIdTokenHint,
                postLogoutRedirectUri = mPostLogoutRedirectUri,
                state = mState,
                uiLocales = mUiLocales,
                additionalParameters = mAdditionalParameters
            )
        }
    }

    fun getUiLocales(): Set<String>? {
        return uiLocales?.let { stringToSet(it) }
    }

    override fun toUri() = configuration.endSessionEndpoint?.buildUpon()?.run {
        idTokenHint?.let { appendQueryParameter(PARAM_ID_TOKEN_HINT, it) }
        state?.let { appendQueryParameter(PARAM_STATE, it) }
        uiLocales?.let { appendQueryParameter(PARAM_UI_LOCALES, it) }

        postLogoutRedirectUri?.let {
            appendQueryParameter(PARAM_POST_LOGOUT_REDIRECT_URI, it.toString())
        }

        additionalParameters.forEach { (key, value) ->
            appendQueryParameter(key, value)
        }

        build()
    }

    /**
     * Produces a JSON representation of the end session request for persistent storage or local
     * transmission (e.g. between activities).
     */
    override fun jsonSerialize() = JSONObject().apply {
        put(KEY_CONFIGURATION, configuration.toJson())
        idTokenHint?.let { put(KEY_ID_TOKEN_HINT, it) }
        postLogoutRedirectUri?.let { put(KEY_POST_LOGOUT_REDIRECT_URI, it.toString()) }
        state?.let { put(KEY_STATE, it) }
        uiLocales?.let { put(KEY_UI_LOCALES, it) }
        put(KEY_ADDITIONAL_PARAMETERS, additionalParameters.toJsonObject())
    }

    /**
     * Produces a JSON string representation of the request for persistent storage or
     * local transmission (e.g. between activities). This method is just a convenience wrapper
     * for [.jsonSerialize], converting the JSON object to its string form.
     */
    override fun jsonSerializeString(): String {
        return jsonSerialize().toString()
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

        private const val KEY_CONFIGURATION = "configuration"
        private const val KEY_ID_TOKEN_HINT = "id_token_hint"
        private const val KEY_POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri"
        private const val KEY_STATE = "state"
        private const val KEY_UI_LOCALES = "ui_locales"
        private const val KEY_ADDITIONAL_PARAMETERS = "additionalParameters"

        /**
         * Reads an authorization request from a JSON string representation produced by
         * [.jsonSerialize].
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject): EndSessionRequest {
            return EndSessionRequest(
                AuthorizationServiceConfiguration.fromJson(json.getJSONObject(KEY_CONFIGURATION)),
                json.getStringIfDefined(KEY_ID_TOKEN_HINT),
                json.getUriIfDefined(KEY_POST_LOGOUT_REDIRECT_URI),
                json.getStringIfDefined(KEY_STATE),
                json.getStringIfDefined(KEY_UI_LOCALES),
                json.getStringMap(KEY_ADDITIONAL_PARAMETERS)
            )
        }

        /**
         * Reads an authorization request from a JSON string representation produced by
         * [.jsonSerializeString]. This method is just a convenience wrapper for
         * [.jsonDeserialize], converting the JSON string to its JSON object form.
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(jsonStr: String) = jsonDeserialize(JSONObject(jsonStr))
    }
}
