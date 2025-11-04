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
@file:Suppress("KDocUnresolvedReference")

package net.openid.appauth

import android.net.Uri
import net.openid.appauth.AuthorizationServiceConfiguration.Companion.fromJson
import org.json.JSONException
import org.json.JSONObject

@Suppress("unused")
class RegistrationRequest private constructor(
    /**
     * The service's [configuration][AuthorizationServiceConfiguration].
     * This configuration specifies how to connect to a particular OAuth provider.
     * Configurations may be
     * [ ][AuthorizationServiceConfiguration], or
     * [ via an OpenID Connect Discovery Document][AuthorizationServiceConfiguration.fetchFromUrl].
     */
    @JvmField val configuration: AuthorizationServiceConfiguration,
    /**
     * The client's redirect URI's.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val redirectUris: List<Uri>,
    /**
     * The response types to use.
     *
     * @see "OpenID Connect Core 1.0, Section 3
     * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.3>"
     */
    @JvmField val responseTypes: List<String>?,
    /**
     * The grant types to use.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.2>"
     */
    @JvmField val grantTypes: List<String>?,
    /**
     * The subject type to use.
     *
     * @see "OpenID Connect Core 1.0, Section 8 <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.8>"
     */
    @JvmField val subjectType: String?,
    /**
     * URL for the Client's JSON Web Key Set [JWK] document.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Client Metadata
     * <https:></https:>//openid.net/specs/openid-connect-registration-1_0.html.ClientMetadata>"
     */
    val jwksUri: Uri?,
    /**
     * Client's JSON Web Key Set [JWK] document.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Client Metadata
     * <https:></https:>//openid.net/specs/openid-connect-registration-1_0.html.ClientMetadata>"
     */
    @JvmField val jwks: JSONObject?,
    /**
     * The client authentication method to use at the token endpoint.
     *
     * @see "OpenID Connect Core 1.0, Section 9 <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
     */
    val tokenEndpointAuthenticationMethod: String?,
    /**
     * Additional parameters to be passed as part of the request.
     */
    @JvmField val additionalParameters: Map<String, String>
) {
    /**
     * The application type to register, will always be 'native'.
     */
    @JvmField
    val applicationType: String = APPLICATION_TYPE_NATIVE


    /**
     * Creates instances of [RegistrationRequest].
     */
    class Builder(
        configuration: AuthorizationServiceConfiguration,
        redirectUri: List<Uri>
    ) {
        private var mConfiguration: AuthorizationServiceConfiguration = configuration
        private var mRedirectUris: List<Uri> = redirectUri

        private var mResponseTypes: List<String>? = null

        private var mGrantTypes: List<String>? = null

        private var mSubjectType: String? = null

        private var mJwksUri: Uri? = null

        private var mJwks: JSONObject? = null

        private var mTokenEndpointAuthenticationMethod: String? = null

        private var mAdditionalParameters: Map<String, String> = emptyMap()


        /**
         * Creates a registration request builder with the specified mandatory properties.
         */
        init {
            require(redirectUri.isNotEmpty()) { "redirectUri cannot be empty" }
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
         * Specifies the redirect URI's.
         *
         * @see [ "The OAuth 2.0
         * Authorization Framework"
        ](https://tools.ietf.org/html/rfc6749.section-3.1.2) */
        fun setRedirectUriValues(vararg redirectUriValues: Uri): Builder {
            return setRedirectUriValues(listOf(*redirectUriValues))
        }

        /**
         * Specifies the redirect URI's.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setRedirectUriValues(redirectUriValues: List<Uri>): Builder {
            require(redirectUriValues.isNotEmpty()) { "redirectUriValues cannot be empty" }
            mRedirectUris = redirectUriValues
            return this
        }

        /**
         * Specifies the response types.
         *
         * @see "OpenID Connect Core 1.0, Section 3
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.3>"
         */
        fun setResponseTypeValues(vararg responseTypeValues: String): Builder {
            return setResponseTypeValues(listOf(*responseTypeValues))
        }

        /**
         * Specifies the response types.
         *
         * @see "OpenID Connect Core 1.0, Section X
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.X>"
         */
        fun setResponseTypeValues(responseTypeValues: List<String>?): Builder {
            mResponseTypes = responseTypeValues
            return this
        }

        /**
         * Specifies the grant types.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.2>"
         */
        fun setGrantTypeValues(vararg grantTypeValues: String): Builder {
            return setGrantTypeValues(listOf(*grantTypeValues))
        }

        /**
         * Specifies the grant types.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.2>"
         */
        fun setGrantTypeValues(grantTypeValues: List<String>?): Builder {
            mGrantTypes = grantTypeValues
            return this
        }

        /**
         * Specifies the subject types.
         *
         * @see "OpenID Connect Core 1.0, Section 8
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.8>"
         */
        fun setSubjectType(subjectType: String?): Builder {
            mSubjectType = subjectType
            return this
        }

        /**
         * Specifies the URL for the Client's JSON Web Key Set.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Client Metadata
         * <https:></https:>//openid.net/specs/openid-connect-registration-1_0.html.ClientMetadata>"
         */
        fun setJwksUri(jwksUri: Uri?): Builder {
            mJwksUri = jwksUri
            return this
        }

        /**
         * Specifies the client's JSON Web Key Set.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Client Metadata
         * <https:></https:>//openid.net/specs/openid-connect-registration-1_0.html.ClientMetadata>"
         */
        fun setJwks(jwks: JSONObject?): Builder {
            mJwks = jwks
            return this
        }

        /**
         * Specifies the client authentication method to use at the token endpoint.
         *
         * @see "OpenID Connect Core 1.0, Section 9
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
         */
        fun setTokenEndpointAuthenticationMethod(
            tokenEndpointAuthenticationMethod: String?
        ): Builder {
            this.mTokenEndpointAuthenticationMethod = tokenEndpointAuthenticationMethod
            return this
        }

        /**
         * Specifies additional parameters. Replaces any previously provided set of parameters.
         * Parameter keys and values cannot be null or empty.
         */
        fun setAdditionalParameters(additionalParameters: Map<String, String>?): Builder {
            mAdditionalParameters = additionalParameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Constructs the registration request. At a minimum, the redirect URI must have been
         * set before calling this method.
         */
        fun build(): RegistrationRequest {
            return RegistrationRequest(
                configuration = mConfiguration,
                redirectUris = mRedirectUris,
                responseTypes = mResponseTypes,
                grantTypes = mGrantTypes,
                subjectType = mSubjectType,
                jwksUri = mJwksUri,
                jwks = mJwks,
                tokenEndpointAuthenticationMethod = mTokenEndpointAuthenticationMethod,
                additionalParameters = mAdditionalParameters
            )
        }
    }

    /**
     * Converts the registration request to JSON for transmission to an authorization service.
     * For local persistence and transmission, use [.jsonSerialize].
     */
    fun toJsonString(): String {
        val json = jsonSerializeParams()
        additionalParameters.forEach { json.put(it.key, it.value) }
        return json.toString()
    }

    /**
     * Produces a JSON representation of the registration request for persistent storage or
     * local transmission (e.g. between activities).
     */
    fun jsonSerialize(): JSONObject {
        val json = jsonSerializeParams()
        json.put(KEY_CONFIGURATION, configuration.toJson())
        json.put(KEY_ADDITIONAL_PARAMETERS, additionalParameters.toJsonObject())
        return json
    }

    /**
     * Produces a JSON string representation of the registration request for persistent storage or
     * local transmission (e.g. between activities). This method is just a convenience wrapper
     * for [.jsonSerialize], converting the JSON object to its string form.
     */
    fun jsonSerializeString() = jsonSerialize().toString()

    private fun jsonSerializeParams() = JSONObject().apply {
        put(PARAM_REDIRECT_URIS, redirectUris.toJsonArray())
        put(PARAM_APPLICATION_TYPE, applicationType)
        responseTypes?.let { put(PARAM_RESPONSE_TYPES, it.toJsonArray()) }
        grantTypes?.let { put(PARAM_GRANT_TYPES, it.toJsonArray()) }
        subjectType?.let { put(PARAM_SUBJECT_TYPE, it) }

        jwksUri?.let { put(PARAM_JWKS_URI, it.toString()) }
        jwks?.let { put(PARAM_JWKS, it) }

        tokenEndpointAuthenticationMethod?.let {
            put(
                PARAM_TOKEN_ENDPOINT_AUTHENTICATION_METHOD,
                it
            )
        }
    }

    companion object {
        /**
         * OpenID Connect 'application_type'.
         */
        const val APPLICATION_TYPE_NATIVE: String = "native"

        const val PARAM_REDIRECT_URIS: String = "redirect_uris"
        const val PARAM_RESPONSE_TYPES: String = "response_types"
        const val PARAM_GRANT_TYPES: String = "grant_types"
        const val PARAM_APPLICATION_TYPE: String = "application_type"
        const val PARAM_SUBJECT_TYPE: String = "subject_type"
        const val PARAM_JWKS_URI: String = "jwks_uri"
        const val PARAM_JWKS: String = "jwks"
        const val PARAM_TOKEN_ENDPOINT_AUTHENTICATION_METHOD: String = "token_endpoint_auth_method"

        private val BUILT_IN_PARAMS: Set<String> = setOf(
            PARAM_REDIRECT_URIS,
            PARAM_RESPONSE_TYPES,
            PARAM_GRANT_TYPES,
            PARAM_APPLICATION_TYPE,
            PARAM_SUBJECT_TYPE,
            PARAM_JWKS_URI,
            PARAM_JWKS,
            PARAM_TOKEN_ENDPOINT_AUTHENTICATION_METHOD
        )

        const val KEY_ADDITIONAL_PARAMETERS: String = "additionalParameters"
        const val KEY_CONFIGURATION: String = "configuration"

        /**
         * Instructs the authorization server to generate a pairwise subject identifier.
         *
         * @see "OpenID Connect Core 1.0, Section 8
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.8>"
         */
        const val SUBJECT_TYPE_PAIRWISE: String = "pairwise"

        /**
         * Instructs the authorization server to generate a public subject identifier.
         *
         * @see "OpenID Connect Core 1.0, Section 8
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.8>"
         */
        const val SUBJECT_TYPE_PUBLIC: String = "public"

        /**
         * Reads a registration request from a JSON string representation produced by
         * [.jsonSerialize].
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject) = RegistrationRequest(
            configuration = fromJson(json.getJSONObject(KEY_CONFIGURATION)),
            redirectUris = json.getUriList(PARAM_REDIRECT_URIS),
            responseTypes = json.getStringListIfDefined(PARAM_RESPONSE_TYPES),
            grantTypes = json.getStringListIfDefined(PARAM_GRANT_TYPES),
            subjectType = json.getStringIfDefined(PARAM_SUBJECT_TYPE),
            jwksUri = json.getUriIfDefined(PARAM_JWKS_URI),
            jwks = json.getJsonObjectIfDefined(PARAM_JWKS),
            tokenEndpointAuthenticationMethod = json.getStringIfDefined(
                PARAM_TOKEN_ENDPOINT_AUTHENTICATION_METHOD
            ),
            additionalParameters = json.getStringMap(KEY_ADDITIONAL_PARAMETERS)
        )

        /**
         * Reads a registration request from a JSON string representation produced by
         * [.jsonSerializeString]. This method is just a convenience wrapper for
         * [.jsonDeserialize], converting the JSON string to its JSON object form.
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @Throws(JSONException::class)
        fun jsonDeserialize(jsonStr: String): RegistrationRequest {
            require(jsonStr.isNotEmpty()) { "jsonStr must not be empty" }
            return jsonDeserialize(JSONObject(jsonStr))
        }
    }
}
