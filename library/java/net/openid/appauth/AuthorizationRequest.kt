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
import net.openid.appauth.AsciiStringListUtil.stringToSet
import net.openid.appauth.CodeVerifierUtil.checkCodeVerifier
import net.openid.appauth.CodeVerifierUtil.codeVerifierChallengeMethod
import net.openid.appauth.CodeVerifierUtil.deriveCodeVerifierChallenge
import org.json.JSONException
import org.json.JSONObject

/**
 * An OAuth2 authorization request.
 *
 * @see "The OAuth 2.0 Authorization Framework
 * @see "The OAuth 2.0 Authorization Framework
 */
@Suppress("KDocUnresolvedReference", "SpellCheckingInspection")
class AuthorizationRequest private constructor(
    /**
     * The service's [configuration][AuthorizationServiceConfiguration].
     * This configuration specifies how to connect to a particular OAuth provider.
     * Configurations may be
     * [ ][AuthorizationServiceConfiguration]
     * created manually}, or [AuthorizationServiceConfiguration.fetchFromUrl] via an OpenID Connect
     * Discovery Document}.
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
     * The expected response type.
     *
     * @see "The OAuth 2.0 Authorization Framework"
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3">
     *     OpenID Connect Core 1.0, Section 3</a>
     */
    @JvmField val responseType: String,
    /**
     * The client's redirect URI.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val redirectUri: Uri,
    /**
     * The OpenID Connect 1.0 `display` parameter. This is a string that specifies how the
     * Authorization Server displays the authentication and consent user interface pages to the
     * End-User.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    @JvmField val display: String?,
    /**
     * The OpenID Connect 1.0 `login_hint` parameter. This is a string hint to the
     * Authorization Server about the login identifier the End-User might use to log in, typically
     * collected directly from the user in an identifier-first authentication flow.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    @JvmField val loginHint: String?,
    /**
     * The OpenID Connect 1.0 `prompt` parameter. This is a space delimited, case sensitive
     * list of ASCII strings that specifies whether the Authorization Server prompts the End-User
     * for re-authentication and consent.
     *
     * @see Prompt
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    @JvmField val prompt: String?,
    /**
     * The OpenID Connect 1.0 `ui_locales` parameter. This is a space-separated list of
     * BCP47 [RFC5646] language tag values, ordered by preference. It represents End-User's
     * preferred languages and scripts for the user interface.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    @JvmField val uiLocales: String?,
    /**
     * The optional set of scopes expressed as a space-delimited, case-sensitive string.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val scope: String?,
    /**
     * An opaque value used by the client to maintain state between the request and callback. If
     * this value is not explicitly set, this library will automatically add state and perform
     * appropriate  validation of the state in the authorization response. It is recommended that
     * the default implementation of this parameter be used wherever possible. Typically used to
     * prevent CSRF attacks, as recommended in
     * [RFC6819 Section 5.3.5](https://tools.ietf.org/html/rfc6819#section-5.3.5).
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    override val state: String?,
    /**
     * String value used to associate a Client session with an ID Token, and to mitigate replay
     * attacks. The value is passed through unmodified from the Authentication Request to the ID
     * Token. If this value is not explicitly set, this library will automatically add nonce and
     * perform appropriate validation of the ID Token. It is recommended that the default
     * implementation of this parameter be used wherever possible.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    @JvmField val nonce: String?,
    /**
     * The proof key for code exchange. This is an opaque value used to associate an authorization
     * request with a subsequent code exchange, in order to prevent any eavesdropping party from
     * intercepting and using the code before the original requestor. If PKCE is disabled due to
     * a non-compliant authorization server which rejects requests with PKCE parameters present,
     * this value will be `null`.
     *
     * @see Builder.setCodeVerifier
     * @see Builder.setCodeVerifier
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    @JvmField val codeVerifier: String?,
    /**
     * The challenge derived from the [code verifier][.codeVerifier], using the
     * [challenge method][.codeVerifierChallengeMethod]. If a code verifier is not being
     * used for this request, this value will be `null`.
     *
     * @see Builder.setCodeVerifier
     * @see Builder.setCodeVerifier
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    @JvmField val codeVerifierChallenge: String?,
    /**
     * The challenge method used to generate a [challenge][.codeVerifierChallenge] from
     * the [code verifier][.codeVerifier]. If a code verifier is not being used for this
     * request, this value will be `null`.
     *
     * @see Builder.setCodeVerifier
     * @see Builder.setCodeVerifier
     * @see "Proof Key for Code Exchange by OAuth Public Clients
     */
    @JvmField val codeVerifierChallengeMethod: String?,
    /**
     * Instructs the authorization service on the mechanism to be used for returning
     * response parameters from the authorization endpoint. This use of this parameter is
     * _not recommended_ when the response mode that would be requested is the default mode
     * specified for the response type.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    @JvmField val responseMode: String?,
    /**
     * Requests that specific Claims be returned.
     * The value is a JSON object listing the requested Claims.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.5">
     *     OpenID Connect Core 1.0, Section 5.5</a>
     */
    @JvmField val claims: JSONObject?,
    /**
     * End-User's preferred languages and scripts for Claims being returned, represented as a
     * space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.2">
     *     OpenID Connect Core 1.0, Section 5.2</a>
     */
    @JvmField val claimsLocales: String?,
    /**
     * Additional parameters to be passed as part of the request.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val additionalParameters: Map<String, String>
) : AuthorizationManagementRequest {
    /**
     * All spec-defined values for the OpenID Connect 1.0 `display` parameter.
     *
     * @see Builder.setDisplay
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    // SuppressWarnings justification: the constants defined are not directly used by the library,
    // existing only for convenience of the developer.
    @Suppress("unused")
    object Display {
        /**
         * The Authorization Server _SHOULD_ display the authentication and consent UI
         * consistent with a full User Agent page view. If the display parameter is not specified,
         * this is the default display mode.
         */
        const val PAGE: String = "page"

        /**
         * The Authorization Server _SHOULD_ display the authentication and consent UI
         * consistent with a popup User Agent window. The popup User Agent window should be of an
         * appropriate size for a login-focused dialog and should not obscure the entire window that
         * it is popping up over.
         */
        const val POPUP: String = "popup"

        /**
         * The Authorization Server _SHOULD_ display the authentication and consent UI
         * consistent with a device that leverages a touch interface.
         */
        const val TOUCH: String = "touch"

        /**
         * The Authorization Server _SHOULD_ display the authentication and consent UI
         * consistent with a "feature phone" type display.
         */
        const val WAP: String = "wap"
    }

    /**
     * All spec-defined values for the OpenID Connect 1.0 `prompt` parameter.
     *
     * @see Builder.setPrompt
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    // SuppressWarnings justification: the constants defined are not directly used by the library,
    // existing only for convenience of the developer.
    @Suppress("unused")
    object Prompt {
        /**
         * The Authorization Server _MUST NOT_ display any authentication or consent user
         * interface pages. An error is returned if an End-User is not already authenticated or the
         * Client does not have pre-configured consent for the requested Claims or does not fulfill
         * other conditions for processing the request. The error code will typically be
         * `login_required`, `interaction_required`, or another code defined in
         * [OpenID Connect Core 1.0, Section 3.1.2.6](
         * https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.6). This can be
         * used as a method to check for existing authentication and/or consent.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.6">
         *     OpenID Connect Core 1.0, Section 3.1.2.6</a>
         */
        const val NONE: String = "none"

        /**
         * The Authorization Server _SHOULD_ prompt the End-User for re-authentication. If
         * it cannot re-authenticate the End-User, it _MUST_ return an error, typically
         * `login_required`.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.6">
         *     OpenID Connect Core 1.0, Section 3.1.2.6</a>
         */
        const val LOGIN: String = "login"

        /**
         * The Authorization Server _SHOULD_ prompt the End-User for consent before
         * returning information to the Client. If it cannot obtain consent, it _MUST_
         * return an error, typically `consent_required`.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.6">
         *     OpenID Connect Core 1.0, Section 3.1.2.6</a>
         */
        const val CONSENT: String = "consent"

        /**
         * The Authorization Server _SHOULD_ prompt the End-User to select a user account.
         * This enables an End-User who has multiple accounts at the Authorization Server to select
         * amongst the multiple accounts that they might have current sessions for. If it cannot
         * obtain an account selection choice made by the End-User, it MUST return an error,
         * typically `account_selection_required`.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.6">
         *     OpenID Connect Core 1.0, Section 3.1.2.6</a>
         */
        const val SELECT_ACCOUNT: String = "select_account"
    }

    /**
     * All spec-defined values for the OAuth2 / OpenID Connect 1.0 `scope` parameter.
     *
     * @see Builder.setScope
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.4">
     *     OpenID Connect Core 1.0, Section 5.4</a>
     */
    // SuppressWarnings justification: the constants defined are not directly used by the library,
    // existing only for convenience of the developer.
    @Suppress("unused")
    object Scope {
        /**
         * A scope for the authenticated user's mailing address.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.4">
         *     OpenID Connect Core 1.0, Section 5.4</a>
         */
        const val ADDRESS: String = "address"

        /**
         * A scope for the authenticated user's email address.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.4">
         *     OpenID Connect Core 1.0, Section 5.4</a>
         */
        const val EMAIL: String = "email"

        /**
         * A scope for requesting an OAuth 2.0 refresh token to be issued, that can be used to
         * obtain an Access Token that grants access to the End-User's UserInfo Endpoint even
         * when the End-User is not present (not logged in).
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.11">
         *     OpenID Connect Core 1.0, Section 11</a>
         */
        const val OFFLINE_ACCESS: String = "offline_access"

        /**
         * A scope for OpenID based authorization.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        const val OPENID: String = "openid"

        /**
         * A scope for the authenticated user's phone number.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.4">
         *     OpenID Connect Core 1.0, Section 5.4</a>
         */
        const val PHONE: String = "phone"

        /**
         * A scope for the authenticated user's basic profile information.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.4">
         *     OpenID Connect Core 1.0, Section 5.4</a>
         */
        const val PROFILE: String = "profile"
    }

    /**
     * All spec-defined values for the OAuth2 / OpenID Connect `response_mode` parameter.
     *
     * @see Builder.setResponseMode
     * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#rfc.section.2.1">
     *     OAuth 2.0 Multiple Response Type Encoding Practices, Section 2.1</a>
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    // SuppressWarnings justification: the constants defined are not directly used by the library,
    // existing only for convenience of the developer.
    @Suppress("unused")
    object ResponseMode {
        /**
         * Instructs the authorization server to send response parameters using
         * the query portion of the redirect URI.
         *
         * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#rfc.section.2.1">
         *     OAuth 2.0 Multiple Response Type Encoding Practices, Section 2.1</a>
         */
        const val QUERY: String = "query"

        /**
         * Instructs the authorization server to send response parameters using
         * the fragment portion of the redirect URI.
         * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#rfc.section.2.1">
         *     OAuth 2.0 Multiple Response Type Encoding Practices, Section 2.1</a>
         */
        const val FRAGMENT: String = "fragment"
    }


    /**
     * Creates instances of [AuthorizationRequest].
     */
    @Suppress("unused")
    class Builder(
        configuration: AuthorizationServiceConfiguration,
        clientId: String,
        responseType: String,
        redirectUri: Uri
    ) {
        // SuppressWarnings justification: static analysis incorrectly determines that this field
        // is not initialized, as it is indirectly initialized by setConfiguration
        private var mConfiguration: AuthorizationServiceConfiguration = configuration

        // SuppressWarnings justification: static analysis incorrectly determines that this field
        // is not initialized, as it is indirectly initialized by setClientId
        private var mClientId: String = clientId

        private var mDisplay: String? = null

        private var mLoginHint: String? = null

        private var mPrompt: String? = null

        private var mUiLocales: String? = null

        // SuppressWarnings justification: static analysis incorrectly determines that this field
        // is not initialized, as it is indirectly initialized by setResponseType
        private var mResponseType: String = responseType

        // SuppressWarnings justification: static analysis incorrectly determines that this field
        // is not initialized, as it is indirectly initialized by setRedirectUri
        private var mRedirectUri: Uri = redirectUri

        private var mScope: String? = null

        private var mState: String? = null

        private var mNonce: String? = null

        private var mCodeVerifier: String? = null

        private var mCodeVerifierChallenge: String? = null

        private var mCodeVerifierChallengeMethod: String? = null

        private var mResponseMode: String? = null

        private var mClaims: JSONObject? = null

        private var mClaimsLocales: String? = null

        private var mAdditionalParameters: Map<String, String> = emptyMap()

        /**
         * Creates an authorization request builder with the specified mandatory properties,
         * and preset values for [AuthorizationRequest.state],
         * [AuthorizationRequest.nonce] and [AuthorizationRequest.codeVerifier].
         */
        init {
            setState(AuthorizationManagementUtil.randomState)
            setNonce(AuthorizationManagementUtil.randomState)
            setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())
        }

        /**
         * Specifies the service configuration to be used in dispatching this request.
         */
        fun setAuthorizationServiceConfiguration(
            configuration: AuthorizationServiceConfiguration
        ): Builder {
            mConfiguration = configuration
            return this
        }

        /**
         * Specifies the client ID. Cannot be null or empty.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setClientId(clientId: String): Builder {
            mClientId = clientId
            return this
        }

        /**
         * Specifies the OpenID Connect 1.0 `display` parameter.
         *
         * @see Display
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setDisplay(display: String?): Builder {
            display?.let { require(it.isNotEmpty()) { "display must be null or not empty" } }
            mDisplay = display
            return this
        }

        /**
         * Specifies the OpenID Connect 1.0 `login_hint` parameter.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setLoginHint(loginHint: String?): Builder {
            loginHint?.let { require(it.isNotEmpty()) { "login hint must be null or not empty" } }
            mLoginHint = loginHint
            return this
        }

        /**
         * Specifies the encoded OpenID Connect 1.0 `prompt` parameter, which is a
         * space-delimited set of case sensitive ASCII prompt values. Replaces any previously
         * specified prompt values.
         *
         * @see Prompt
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setPrompt(prompt: String?): Builder {
            prompt?.let { require(it.isNotEmpty()) { "prompt must be null or not empty" } }
            mPrompt = prompt
            return this
        }

        /**
         * Specifies the set of OpenID Connect 1.0 `prompt` parameter values, which are
         * space-delimited, case sensitive ASCII prompt values. Replaces any previously
         * specified prompt values.
         *
         * @see Prompt
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setPromptValues(vararg promptValues: String): Builder {
            return setPromptValues(listOf(*promptValues))
        }

        /**
         * Specifies the set of OpenID Connect 1.0 `prompt` parameter values, which are
         * space-delimited, case sensitive ASCII prompt values. Replaces any previously
         * specified prompt values.
         *
         * @see Prompt
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setPromptValues(promptValues: Iterable<String>): Builder {
            mPrompt = AsciiStringListUtil.iterableToString(promptValues)
            return this
        }

        /**
         * Specifies the OpenID Connect 1.0 `ui_locales` parameter, which is a space-separated list
         * of BCP47 [RFC5646] language tag values, ordered by preference. It represents End-User's
         * preferred languages and scripts for the user interface. Replaces any previously
         * specified ui_locales values.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setUiLocales(uiLocales: String?): Builder {
            uiLocales?.let { require(it.isNotEmpty()) { "uiLocales must be null or not empty" } }
            mUiLocales = uiLocales
            return this
        }

        /**
         * Specifies the OpenID Connect 1.0 `ui_locales` parameter, which is a space-separated list
         * of BCP47 [RFC5646] language tag values, ordered by preference. It represents End-User's
         * preferred languages and scripts for the user interface. Replaces any previously
         * specified ui_locales values.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setUiLocalesValues(vararg uiLocalesValues: String): Builder {
            return setUiLocalesValues(listOf(*uiLocalesValues))
        }

        /**
         * Specifies the OpenID Connect 1.0 `ui_locales` parameter, which is a space-separated list
         * of BCP47 [RFC5646] language tag values, ordered by preference. It represents End-User's
         * preferred languages and scripts for the user interface. Replaces any previously
         * specified ui_locales values.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setUiLocalesValues(uiLocalesValues: Iterable<String>): Builder {
            mUiLocales = AsciiStringListUtil.iterableToString(uiLocalesValues)
            return this
        }

        /**
         * Specifies the expected response type. Cannot be null or empty.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3">
         *     OpenID Connect Core 1.0, Section 3</a>
         */
        fun setResponseType(responseType: String): Builder {
            require(responseType.isNotEmpty()) { "response type cannot be empty" }
            mResponseType = responseType
            return this
        }

        /**
         * Specifies the client's redirect URI. Cannot be null or empty.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setRedirectUri(redirectUri: Uri): Builder {
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
            if (scope.isNullOrEmpty()) {
                mScope = null
            } else {
                setScopes(*scope.split(" +").dropLastWhile { it.isEmpty() }.toTypedArray())
            }
            return this
        }

        /**
         * Specifies the set of case-sensitive scopes. Replaces any previously specified set of
         * scopes. If no arguments are provided, the scope string will be set to `null`.
         * Individual scope strings cannot be empty.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScopes(vararg scopes: String): Builder {
            setScopes(listOf(*scopes))
            return this
        }

        /**
         * Specifies the set of case-sensitive scopes. Replaces any previously specified set of
         * scopes. If the iterable is empty, the scope string will be set to `null`.
         * Individual scope strings cannot be null or empty.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setScopes(scopes: Iterable<String>): Builder {
            mScope = AsciiStringListUtil.iterableToString(scopes)
            return this
        }

        /**
         * Specifies the opaque value used by the client to maintain state between the request and
         * callback. If this value is not explicitly set, this library will automatically add state
         * and perform appropriate validation of the state in the authorization response. It is
         * recommended that the default implementation of this parameter be used wherever possible.
         * Typically used to prevent CSRF attacks, as recommended in
         * [RFC6819 Section 5.3.5](https://tools.ietf.org/html/rfc6819#section-5.3.5).
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setState(state: String?): Builder {
            state?.let { require(it.isNotEmpty()) { "state cannot be empty if defined" } }
            mState = state
            return this
        }

        /**
         * Specifies the String value used to associate a Client session with an ID Token, and to
         * mitigate replay attacks. The value is passed through unmodified from the Authentication
         * Request to the ID Token. If this value is not explicitly set, this library will
         * automatically add nonce and perform appropriate validation of the ID Token. It is
         * recommended that the default implementation of this parameter be used wherever possible.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         */
        fun setNonce(nonce: String?): Builder {
            nonce?.let { require(it.isNotEmpty()) { "nonce cannot be empty if defined" } }
            mNonce = nonce
            return this
        }

        /**
         * Specifies the code verifier to use for this authorization request. The default challenge
         * method (typically [.CODE_CHALLENGE_METHOD_S256]) implemented by
         * [CodeVerifierUtil] will be used, and a challenge will be generated using this
         * method. If the use of a code verifier is not desired, set the code verifier
         * to `null`.
         *
         * @see "Proof Key for Code Exchange by OAuth Public Clients
         */
        fun setCodeVerifier(codeVerifier: String?): Builder {
            codeVerifier?.let { checkCodeVerifier(it) }
            mCodeVerifier = codeVerifier
            mCodeVerifierChallenge = codeVerifier?.let { deriveCodeVerifierChallenge(it) }
            mCodeVerifierChallengeMethod = codeVerifier?.let { codeVerifierChallengeMethod }
            return this
        }

        /**
         * Specifies the code verifier, challenge and method strings to use for this authorization
         * request. If these values are not explicitly set, they will be automatically generated
         * and used. It is recommended that this default behavior be used wherever possible. If
         * a null code verifier is set (to indicate that a code verifier is not to be used), then
         * the challenge and method must also be null. If a non-null code verifier is set, the
         * code verifier challenge and method must also be set.
         *
         * @see "Proof Key for Code Exchange by OAuth Public Clients
         */
        fun setCodeVerifier(
            codeVerifier: String?,
            codeVerifierChallenge: String?,
            codeVerifierChallengeMethod: String?
        ): Builder {
            if (codeVerifier != null) {
                checkCodeVerifier(codeVerifier)

                require(!codeVerifierChallenge.isNullOrEmpty()) {
                    "code verifier challenge cannot be null or empty if verifier is set"
                }

                require(!codeVerifierChallengeMethod.isNullOrEmpty()) {
                    "code verifier challenge method cannot be null or empty if verifier is set"
                }
            }

            mCodeVerifier = codeVerifier
            mCodeVerifierChallenge = codeVerifierChallenge.takeIf { codeVerifier != null }

            mCodeVerifierChallengeMethod =
                codeVerifierChallengeMethod.takeIf { codeVerifier != null }

            return this
        }

        /**
         * Specifies the response mode to be used for returning authorization response parameters
         * from the authorization endpoint.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
         *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
         *
         * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#rfc.section.2">
         *     OAuth 2.0 Multiple Response Type Encoding Practices, Section 2
         */
        fun setResponseMode(responseMode: String?): Builder {
            responseMode?.let { require(it.isNotEmpty()) { "responseMode must be null or not empty" } }
            mResponseMode = responseMode
            return this
        }

        /**
         * Requests that specific Claims be returned.
         * The value is a JSON object listing the requested Claims.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.5">
         *     OpenID Connect Core 1.0, Section 5.5</a>
         */
        fun setClaims(claims: JSONObject?): Builder {
            mClaims = claims
            return this
        }

        /**
         * End-User's preferred languages and scripts for Claims being returned, represented as a
         * space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.2">
         *     OpenID Connect Core 1.0, Section 5.2</a>
         */
        fun setClaimsLocales(claimsLocales: String?): Builder {
            claimsLocales?.let { require(it.isNotEmpty()) { "claimsLocales must be null or not empty" } }
            mClaimsLocales = claimsLocales
            return this
        }

        /**
         * End-User's preferred languages and scripts for Claims being returned, represented as a
         * space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.2">
         *     OpenID Connect Core 1.0, Section 5.2</a>
         */
        fun setClaimsLocalesValues(vararg claimsLocalesValues: String): Builder {
            return setClaimsLocalesValues(listOf(*claimsLocalesValues))
        }

        /**
         * End-User's preferred languages and scripts for Claims being returned, represented as a
         * space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.
         *
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.2">
         *     OpenID Connect Core 1.0, Section 5.2</a>
         */
        fun setClaimsLocalesValues(claimsLocalesValues: Iterable<String>): Builder {
            mClaimsLocales = AsciiStringListUtil.iterableToString(claimsLocalesValues)
            return this
        }

        /**
         * Specifies additional parameters. Replaces any previously provided set of parameters.
         * Parameter keys and values cannot be null or empty.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setAdditionalParameters(additionalParameters: Map<String, String>?): Builder {
            mAdditionalParameters = additionalParameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Constructs the authorization request. At a minimum the following fields must have been
         * set:
         *
         * - The client ID
         * - The expected response type
         * - The redirect URI
         *
         * Failure to specify any of these parameters will result in a runtime exception.
         */
        fun build(): AuthorizationRequest {
            return AuthorizationRequest(
                mConfiguration,
                mClientId,
                mResponseType,
                mRedirectUri,
                mDisplay,
                mLoginHint,
                mPrompt,
                mUiLocales,
                mScope,
                mState,
                mNonce,
                mCodeVerifier,
                mCodeVerifierChallenge,
                mCodeVerifierChallengeMethod,
                mResponseMode,
                mClaims,
                mClaimsLocales,
                mAdditionalParameters
            )
        }
    }

    /**
     * Derives the set of scopes from the consolidated, space-delimited scopes in the
     * [.scope] field. If no scopes were specified for this request, the method will
     * return `null`.
     */
    val scopeSet: Set<String>?
        get() = scope?.let { stringToSet(it) }

    /**
     * Derives the set of prompt values from the consolidated, space-delimited prompt values in
     * the [.prompt] field. If no prompt values were specified for this request, the method
     * will return `null`.
     */
    val promptValues: Set<String>?
        get() = prompt?.let { stringToSet(it) }

    /**
     * Derives the set of ui_locales values from the consolidated, space-separated list of
     * BCP47 [RFC5646] language tag values in the [.uiLocales] field. If no ui_locales values
     * were specified for this request, the method will return `null`.
     */
    fun getUiLocales(): Set<String>? {
        return uiLocales?.let { stringToSet(it) }
    }

    /**
     * Derives the set of claims_locales values from the consolidated, space-separated list of
     * BCP47 [RFC5646] language tag values in the [.claimsLocales] field. If no claims_locales
     * values were specified for this request, the method will return `null`.
     */
    fun getClaimsLocales(): Set<String>? {
        return claimsLocales?.let { stringToSet(it) }
    }

    /**
     * Produces a request URI, that can be used to dispatch the authorization request.
     */
    override fun toUri(): Uri = configuration.authorizationEndpoint.buildUpon().run {
        appendQueryParameter(PARAM_REDIRECT_URI, redirectUri.toString())
        appendQueryParameter(PARAM_CLIENT_ID, clientId)
        appendQueryParameter(PARAM_RESPONSE_TYPE, responseType)

        display?.let { appendQueryParameter(PARAM_DISPLAY, it) }
        loginHint?.let { appendQueryParameter(PARAM_LOGIN_HINT, it) }
        prompt?.let { appendQueryParameter(PARAM_PROMPT, it) }
        uiLocales?.let { appendQueryParameter(PARAM_UI_LOCALES, it) }
        state?.let { appendQueryParameter(PARAM_STATE, it) }
        nonce?.let { appendQueryParameter(PARAM_NONCE, it) }
        scope?.let { appendQueryParameter(PARAM_SCOPE, it) }
        responseMode?.let { appendQueryParameter(PARAM_RESPONSE_MODE, it) }

        codeVerifier?.let {
            appendQueryParameter(PARAM_CODE_CHALLENGE, codeVerifierChallenge)
            appendQueryParameter(PARAM_CODE_CHALLENGE_METHOD, codeVerifierChallengeMethod)
        }

        claims?.let { appendQueryParameter(PARAM_CLAIMS, it.toString()) }
        claimsLocales?.let { appendQueryParameter(PARAM_CLAIMS_LOCALES, it) }

        additionalParameters.forEach { (key, value) ->
            appendQueryParameter(key, value)
        }

        build()
    }


    /**
     * Produces a JSON representation of the authorization request for persistent storage or local
     * transmission (e.g. between activities).
     */
    override fun jsonSerialize() = JSONObject().apply {
        put(KEY_CONFIGURATION, configuration.toJson())
        put(KEY_CLIENT_ID, clientId)
        put(KEY_RESPONSE_TYPE, responseType)
        put(KEY_REDIRECT_URI, redirectUri.toString())
        display?.let { put(KEY_DISPLAY, it) }
        loginHint?.let { put(KEY_LOGIN_HINT, it) }
        scope?.let { put(KEY_SCOPE, it) }
        prompt?.let { put(KEY_PROMPT, it) }
        uiLocales?.let { put(KEY_UI_LOCALES, it) }
        state?.let { put(KEY_STATE, it) }
        nonce?.let { put(KEY_NONCE, it) }
        codeVerifier?.let { put(KEY_CODE_VERIFIER, it) }
        codeVerifierChallenge?.let { put(KEY_CODE_VERIFIER_CHALLENGE, it) }
        codeVerifierChallengeMethod?.let { put(KEY_CODE_VERIFIER_CHALLENGE_METHOD, it) }
        responseMode?.let { put(KEY_RESPONSE_MODE, it) }
        claims?.let { put(KEY_CLAIMS, it) }
        claimsLocales?.let { put(KEY_CLAIMS_LOCALES, it) }
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
        /**
         * SHA-256 based code verifier challenge method.
         *
         * @see "Proof Key for Code Exchange by OAuth Public Clients
         */
        const val CODE_CHALLENGE_METHOD_S256: String = "S256"

        /**
         * Plain-text code verifier challenge method. This is only used by AppAuth for Android if
         * SHA-256 is not supported on this platform.
         *
         * @see "Proof Key for Code Exchange by OAuth Public Clients
         */
        const val CODE_CHALLENGE_METHOD_PLAIN: String = "plain"

        @VisibleForTesting
        const val PARAM_CLIENT_ID: String = "client_id"

        @VisibleForTesting
        const val PARAM_CODE_CHALLENGE: String = "code_challenge"

        @VisibleForTesting
        const val PARAM_CODE_CHALLENGE_METHOD: String = "code_challenge_method"

        @VisibleForTesting
        const val PARAM_DISPLAY: String = "display"

        @VisibleForTesting
        const val PARAM_LOGIN_HINT: String = "login_hint"

        @VisibleForTesting
        const val PARAM_PROMPT: String = "prompt"

        @VisibleForTesting
        const val PARAM_UI_LOCALES: String = "ui_locales"

        @VisibleForTesting
        const val PARAM_REDIRECT_URI: String = "redirect_uri"

        @VisibleForTesting
        const val PARAM_RESPONSE_MODE: String = "response_mode"

        @VisibleForTesting
        const val PARAM_RESPONSE_TYPE: String = "response_type"

        @VisibleForTesting
        const val PARAM_SCOPE: String = "scope"

        @VisibleForTesting
        const val PARAM_STATE: String = "state"

        @VisibleForTesting
        const val PARAM_NONCE: String = "nonce"

        @VisibleForTesting
        const val PARAM_CLAIMS: String = "claims"

        @VisibleForTesting
        const val PARAM_CLAIMS_LOCALES: String = "claims_locales"

        private val BUILT_IN_PARAMS: Set<String> = setOf(
            PARAM_CLIENT_ID,
            PARAM_CODE_CHALLENGE,
            PARAM_CODE_CHALLENGE_METHOD,
            PARAM_DISPLAY,
            PARAM_LOGIN_HINT,
            PARAM_PROMPT,
            PARAM_UI_LOCALES,
            PARAM_REDIRECT_URI,
            PARAM_RESPONSE_MODE,
            PARAM_RESPONSE_TYPE,
            PARAM_SCOPE,
            PARAM_STATE,
            PARAM_CLAIMS,
            PARAM_CLAIMS_LOCALES
        )

        private const val KEY_CONFIGURATION = "configuration"
        private const val KEY_CLIENT_ID = "clientId"
        private const val KEY_DISPLAY = "display"
        private const val KEY_LOGIN_HINT = "login_hint"
        private const val KEY_PROMPT = "prompt"
        private const val KEY_UI_LOCALES = "ui_locales"
        private const val KEY_RESPONSE_TYPE = "responseType"
        private const val KEY_REDIRECT_URI = "redirectUri"
        private const val KEY_SCOPE = "scope"
        private const val KEY_STATE = "state"
        private const val KEY_NONCE = "nonce"
        private const val KEY_CODE_VERIFIER = "codeVerifier"
        private const val KEY_CODE_VERIFIER_CHALLENGE = "codeVerifierChallenge"
        private const val KEY_CODE_VERIFIER_CHALLENGE_METHOD = "codeVerifierChallengeMethod"
        private const val KEY_RESPONSE_MODE = "responseMode"
        private const val KEY_CLAIMS = "claims"
        private const val KEY_CLAIMS_LOCALES = "claimsLocales"
        private const val KEY_ADDITIONAL_PARAMETERS = "additionalParameters"

        /**
         * Reads an authorization request from a JSON string representation produced by
         * [.jsonSerialize].
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject): AuthorizationRequest {
            return AuthorizationRequest(
                configuration = AuthorizationServiceConfiguration.fromJson(
                    json.getJSONObject(
                        KEY_CONFIGURATION
                    )
                ),
                clientId = json.getString(KEY_CLIENT_ID),
                responseType = json.getString(KEY_RESPONSE_TYPE),
                redirectUri = json.getUri(KEY_REDIRECT_URI),
                display = json.getStringIfDefined(KEY_DISPLAY),
                loginHint = json.getStringIfDefined(KEY_LOGIN_HINT),
                prompt = json.getStringIfDefined(KEY_PROMPT),
                uiLocales = json.getStringIfDefined(KEY_UI_LOCALES),
                scope = json.getStringIfDefined(KEY_SCOPE),
                state = json.getStringIfDefined(KEY_STATE),
                nonce = json.getStringIfDefined(KEY_NONCE),
                codeVerifier = json.getStringIfDefined(KEY_CODE_VERIFIER),
                codeVerifierChallenge = json.getStringIfDefined(KEY_CODE_VERIFIER_CHALLENGE),
                codeVerifierChallengeMethod = json.getStringIfDefined(
                    KEY_CODE_VERIFIER_CHALLENGE_METHOD
                ),
                responseMode = json.getStringIfDefined(KEY_RESPONSE_MODE),
                claims = json.getJsonObjectIfDefined(KEY_CLAIMS),
                claimsLocales = json.getStringIfDefined(KEY_CLAIMS_LOCALES),
                additionalParameters = json.getStringMap(KEY_ADDITIONAL_PARAMETERS)
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
        fun jsonDeserialize(jsonStr: String): AuthorizationRequest {
            return jsonDeserialize(JSONObject(jsonStr))
        }
    }
}
