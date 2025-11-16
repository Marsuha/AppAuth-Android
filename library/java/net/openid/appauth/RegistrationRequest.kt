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
import androidx.annotation.VisibleForTesting
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.openid.appauth.internal.UriSerializer

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class RegistrationRequest private constructor(
    /**
     * The service's [configuration][AuthorizationServiceConfiguration].
     * This configuration specifies how to connect to a particular OAuth provider.
     * Configurations may be
     * [ ][AuthorizationServiceConfiguration], or
     * [ via an OpenID Connect Discovery Document][AuthorizationServiceConfiguration.fetchFromUrl].
     */
    val configuration: AuthorizationServiceConfiguration,
    /**
     * The client's redirect URI's.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    @SerialName(PARAM_REDIRECT_URIS)
    val redirectUris: List<@Serializable(with = UriSerializer::class) Uri>,
    /**
     * The response types to use.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3">
     *     OpenID Connect Core 1.0, Section 3</a>
     */
    @SerialName(PARAM_RESPONSE_TYPES)
    val responseTypes: List<String>? = null,
    /**
     * The grant types to use.
     *
     * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html#rfc.section.2">
     *     OpenID Connect Dynamic Client Registration 1.0, Section 2</a>
     */
    @SerialName(PARAM_GRANT_TYPES)
    val grantTypes: List<String>? = null,
    /**
     * The subject type to use.
     *
     * @see "OpenID Connect Core 1.0, Section 8 <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.8>"
     */
    @SerialName(PARAM_SUBJECT_TYPE)
    val subjectType: String? = null,
    /**
     * URL for the Client's JSON Web Key Set [JWK] document.
     *
     * @see <a href="https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata">
     *     OpenID Connect Dynamic Client Registration 1.0, Client Metadata</a>
     */
    @Serializable(with = UriSerializer::class)
    val jwksUri: Uri? = null,
    /**
     * Client's JSON Web Key Set [JWK] document.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Client Metadata
     * <https:></https:>//openid.net/specs/openid-connect-registration-1_0.html.ClientMetadata>"
     */
    @SerialName(PARAM_JWKS)
    val jwks: JsonObject? = null,
    /**
     * The client authentication method to use at the token endpoint.
     *
     * @see "OpenID Connect Core 1.0, Section 9 <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
     */
    @SerialName(PARAM_TOKEN_ENDPOINT_AUTHENTICATION_METHOD)
    val tokenEndpointAuthenticationMethod: String? = null,
    /**
     * Additional parameters to be passed as part of the request.
     */
    val additionalParameters: JsonObject = emptyJsonObject()
) {
    /**
     * The application type to register, will always be 'native'.
     */
    @EncodeDefault
    @SerialName(PARAM_APPLICATION_TYPE)
    val applicationType: String = APPLICATION_TYPE_NATIVE

    /**
     * A JSON representation of the registration request. This is primarily used for serializing
     * the request state in order to restore it later.
     */
    val asJsonString get() = Json.encodeToString(this)

    @VisibleForTesting
    val asJsonObject get() = Json.encodeToJsonElement(this).jsonObject

    /**
     * Returns the request parameters as a JSON string for use in the registration request.
     * This is the JSON representation of the body of the registration request.
     */
    val asRequestJsonString get() = toRequestJsonObject().toString()


    /**
     * Creates instances of [RegistrationRequest].
     */
    class Builder(
        private var configuration: AuthorizationServiceConfiguration,
        private var redirectUris: List<Uri>
    ) {

        private var responseTypes: List<String>? = null

        private var grantTypes: List<String>? = null

        private var subjectType: String? = null

        private var jwksUri: Uri? = null

        private var jwks: JsonObject? = null

        private var tokenEndpointAuthenticationMethod: String? = null

        private var additionalParameters: JsonObject = emptyJsonObject()


        /**
         * Creates a registration request builder with the specified mandatory properties.
         */
        init {
            require(redirectUris.isNotEmpty()) { "redirectUri cannot be empty" }
        }

        /**
         * Specifies the authorization service configuration for the request, which must not
         * be null or empty.
         */
        fun setConfiguration(config: AuthorizationServiceConfiguration): Builder {
            configuration = config
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
            this@Builder.redirectUris = redirectUriValues
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
            responseTypes = responseTypeValues
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
            grantTypes = grantTypeValues
            return this
        }

        /**
         * Specifies the subject types.
         *
         * @see "OpenID Connect Core 1.0, Section 8
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.8>"
         */
        fun setSubjectType(type: String?): Builder {
            subjectType = type
            return this
        }

        /**
         * Specifies the URL for the Client's JSON Web Key Set.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Client Metadata
         * <https:></https:>//openid.net/specs/openid-connect-registration-1_0.html.ClientMetadata>"
         */
        fun setJwksUri(keySetUri: Uri?): Builder {
            jwksUri = keySetUri
            return this
        }

        /**
         * Specifies the client's JSON Web Key Set.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Client Metadata
         * <https:></https:>//openid.net/specs/openid-connect-registration-1_0.html.ClientMetadata>"
         */
        fun setJwks(keySet: JsonObject?): Builder {
            jwks = keySet
            return this
        }

        /**
         * Specifies the client authentication method to use at the token endpoint.
         *
         * @see "OpenID Connect Core 1.0, Section 9
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
         */
        fun setTokenEndpointAuthenticationMethod(tokenEndpointAuthMethod: String?): Builder {
            tokenEndpointAuthenticationMethod = tokenEndpointAuthMethod
            return this
        }

        /**
         * Specifies additional parameters. Replaces any previously provided set of parameters.
         * Parameter keys and values cannot be null or empty.
         */
        fun setAdditionalParameters(parameters: JsonObject?): Builder {
            additionalParameters = parameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Constructs the registration request. At a minimum, the redirect URI must have been
         * set before calling this method.
         */
        fun build(): RegistrationRequest {
            return RegistrationRequest(
                configuration = configuration,
                redirectUris = this@Builder.redirectUris,
                responseTypes = responseTypes,
                grantTypes = grantTypes,
                subjectType = subjectType,
                jwksUri = jwksUri,
                jwks = jwks,
                tokenEndpointAuthenticationMethod = tokenEndpointAuthenticationMethod,
                additionalParameters = additionalParameters
            )
        }
    }

    @VisibleForTesting
    fun toRequestJsonObject(): JsonObject {
        val jsonRequest = buildJsonObject {
            putJsonArray(PARAM_REDIRECT_URIS) { redirectUris.forEach { add(it.toString()) } }
            put(PARAM_APPLICATION_TYPE, applicationType)
            responseTypes?.let { types ->
                putJsonArray(PARAM_RESPONSE_TYPES) { types.forEach { add(it) } }
            }

            grantTypes?.let { types ->
                putJsonArray(PARAM_GRANT_TYPES) { types.forEach { add(it) } }
            }

            subjectType?.let { put(PARAM_SUBJECT_TYPE, it) }
            jwksUri?.let { put(PARAM_JWKS_URI, it.toString()) }
            jwks?.let { put(PARAM_JWKS, it) }
            tokenEndpointAuthenticationMethod?.let {
                put(PARAM_TOKEN_ENDPOINT_AUTHENTICATION_METHOD, it)
            }
        }

        return JsonObject(jsonRequest + additionalParameters)
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
         * Reads a registration request from a JSON string.
         * @throws JsonException if the JSON is malformed or missing required fields.
         */
        fun fromJsonString(json: String) = Json.decodeFromString<RegistrationRequest>(json)
    }
}