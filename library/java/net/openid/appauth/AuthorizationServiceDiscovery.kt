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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import net.openid.appauth.internal.UriSerializer

/**
 * An OpenID Connect 1.0 Discovery Document.
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html#rfc.section.3">
 *     OpenID Connect discovery 1.0, Section 3</a>
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class AuthorizationServiceDiscovery(
    /**
     * The asserted issuer identifier.
     */
    @Required
    @SerialName("issuer")
    val issuer: String,

    /**
     * The OAuth 2 authorization endpoint URI.
     */
    @Required
    @Serializable(with = UriSerializer::class)
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: Uri,

    /**
     * The OAuth 2 token endpoint URI. Not specified if only the implicit flow is used.
     */
    @Serializable(with = UriSerializer::class)
    @SerialName("token_endpoint")
    val tokenEndpoint: Uri? = null,

    /**
     * The OAuth 2 emd session endpoint URI. Not specified test OAuth implementation
     */
    @Serializable(with = UriSerializer::class)
    @SerialName("end_session_endpoint")
    val endSessionEndpoint: Uri? = null,

    /**
     * The OpenID Connect UserInfo endpoint URI.
     */
    @Serializable(with = UriSerializer::class)
    @SerialName("userinfo_endpoint")
    val userinfoEndpoint: Uri? = null,

    /**
     * The JSON web key set document URI.
     *
     * @see "JSON Web Key
     */
    @Required
    @Serializable(with = UriSerializer::class)
    @SerialName("jwks_uri")
    val jwksUri: Uri,

    /**
     * The dynamic client registration endpoint URI.
     */
    @Serializable(with = UriSerializer::class)
    @SerialName("registration_endpoint")
    val registrationEndpoint: Uri? = null,

    /**
     * The OAuth 2 `scope` values supported.
     *
     * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">
     *     OpenID Connect Dynamic Client Registration 1.0
     */
    @SerialName("scopes_supported")
    val scopesSupported: List<String>? = null,

    /**
     * The OAuth 2 `response_type` values supported.
     */
    @Required
    @SerialName("response_types_supported")
    val responseTypesSupported: List<String>,

    /**
     * The OAuth 2 `response_mode` values supported.
     *
     * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html">
     *     OAuth 2.0 Multiple Response Type Encoding Practices</a>
     */
    @SerialName("response_modes_supported")
    val responseModesSupported: List<String>? = null,

    /**
     * The OAuth 2 `grant_type` values supported. Defaults to `authorization_code` and `implicit`
     * if not specified in the discovery document, as suggested by the discovery specification.
     */
    @SerialName("grant_types_supported")
    val grantTypesSupported: List<String> = listOf("authorization_code", "implicit"),

    /**
     * The authentication context class references supported.
     */
    @SerialName("acr_values_supported")
    val acrValuesSupported: List<String>? = null,

    /**
     * The subject identifier types supported.
     */
    @Required
    @SerialName("subject_types_supported")
    val subjectTypesSupported: List<String>,

    /**
     * The JWS signing algorithms (alg values) supported for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    @Required
    @SerialName("id_token_signing_alg_values_supported")
    val idTokenSigningAlgorithmValuesSupported: List<String>,

    /**
     * The JWE encryption algorithms (alg values) supported for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    @SerialName("id_token_encryption_alg_values_supported")
    val idTokenEncryptionAlgorithmValuesSupported: List<String>? = null,

    /**
     * The JWE encryption encodings (enc values) supported for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    @SerialName("id_token_encryption_enc_values_supported")
    val idTokenEncryptionEncodingValuesSupported: List<String>? = null,

    /**
     * The JWS signing algorithms (alg values) supported by the UserInfo Endpoint
     * for encoding ID token claims.
     *
     * @see "JSON Web Signature
     * @see "JSON Web Algorithms
     * @see "JSON Web Token
     */
    @SerialName("userinfo_signing_alg_values_supported")
    val userinfoSigningAlgorithmValuesSupported: List<String>? = null,

    /**
     * The JWE encryption algorithms (alg values) supported by the UserInfo Endpoint
     * for encoding ID token claims.
     *
     * @see "JSON Web Signature
     * @see "JSON Web Algorithms
     * @see "JSON Web Token
     */
    @SerialName("userinfo_encryption_alg_values_supported")
    val userinfoEncryptionAlgorithmValuesSupported: List<String>? = null,

    /**
     * The JWE encryption encodings (enc values) supported by the UserInfo Endpoint
     * for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    @SerialName("userinfo_encryption_enc_values_supported")
    val userinfoEncryptionEncodingValuesSupported: List<String>? = null,

    /**
     * The JWS signing algorithms (alg values) supported for Request Objects.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.1">
     *     OpenID Connect Core 1.0, Section 6.1</a>
     */
    @SerialName("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgorithmValuesSupported: List<String>? = null,

    /**
     * The JWE encryption algorithms (alg values) supported for Request Objects.
     */
    @SerialName("request_object_encryption_alg_values_supported")
    val requestObjectEncryptionAlgorithmValuesSupported: List<String>? = null,

    /**
     * The JWE encryption encodings (enc values) supported for Request Objects.
     */
    @SerialName("request_object_encryption_enc_values_supported")
    val requestObjectEncryptionEncodingValuesSupported: List<String>? = null,

    /**
     * The client authentication methods supported by the token endpoint. Defaults to
     * `client_secret_basic` if the discovery document does not specify a value, as suggested
     * by the discovery specification.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.9">
     *     OpenID Connect Core 1.0, Section 9</a>
     *
     * @see "The OAuth 2.0 Authorization Framework"
     */
    @SerialName("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String> = listOf("client_secret_basic"),

    /**
     * The JWS signing algorithms (alg values) supported by the token endpoint for the signature on
     * the JWT used to authenticate the client for the `private_key_jwt` and
     * `client_secret_jwt` authentication methods.
     *
     * @see "JSON Web Token"
     */
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgorithmValuesSupported: List<String>? = null,

    /**
     * The `display` parameter values supported.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    @SerialName("display_values_supported")
    val displayValuesSupported: List<String>? = null,

    /**
     * The claim types supported. Defaults to `normal` if not specified by the discovery
     * document JSON, as suggested by the discovery specification.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.6">
     *     OpenID Connect Core 1.0, Section 5.6</a>
     */
    @SerialName("claim_types_supported")
    val claimTypesSupported: List<String> = listOf("normal"),

    /**
     * The claim names of the claims that the provider _may_ be able to supply values for.
     */
    @SerialName("claims_supported")
    val claimsSupported: List<String>? = null,

    /**
     * A page containing human-readable information that developers might want or need to know when
     * using this provider.
     */
    @Serializable(with = UriSerializer::class)
    @SerialName("service_documentation")
    val serviceDocumentation: Uri? = null,

    /**
     * Languages and scripts supported for values in claims being returned.
     * Represented as a list of BCP47 language tag values.
     *
     * @see "Tags for Identifying Languages"
     */
    @SerialName("claims_locales_supported")
    val claimsLocalesSupported: List<String>? = null,

    /**
     * Languages and scripts supported for the user interface.
     * Represented as a list of BCP47 language tag values.
     *
     * @see "Tags for Identifying Languages"
     */
    @SerialName("ui_locales_supported")
    val uiLocalesSupported: List<String>? = null,

    /**
     * Specifies whether the `claims` parameter is supported for authorization requests.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.5">
     *     OpenID Connect Core 1.0, Section 5.5</a>
     */
    @SerialName("claims_parameter_supported")
    val isClaimsParameterSupported: Boolean = false,

    /**
     * Specifies whether the `request` parameter is supported for authorization requests.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.1">
     *     OpenID Connect Core 1.0, Section 6.1</a>
     */
    @SerialName("request_parameter_supported")
    val isRequestParameterSupported: Boolean = false,

    /**
     * Specifies whether the `request_uri` parameter is supported for authorization requests.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2">
     *     OpenID Connect Core 1.0, Section 6.2</a>
     */
    @SerialName("request_uri_parameter_supported")
    val isRequestUriParameterSupported: Boolean = true,

    /**
     * Specifies whether `request_uri` values are required to be pre-registered before use.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2">
     *     OpenID Connect Core 1.0, Section 6.2</a>
     */
    @SerialName("require_request_uri_registration")
    val requireRequestUriRegistration: Boolean = false,

    /**
     * A page articulating the policy regarding the use of data provided by the provider.
     */
    @Serializable(with = UriSerializer::class)
    @SerialName("op_policy_uri")
    val opPolicyUri: Uri? = null,

    /**
     * A page articulating the terms of service for the provider.
     */
    @Serializable(with = UriSerializer::class)
    @SerialName("op_tos_uri")
    val opTosUri: Uri? = null,
) {
    val asJsonString get() = Json.encodeToString(this)

    companion object {
        /**
         * Creates a discovery document from a JSON string.
         * @param json The JSON string.
         * @throws IllegalArgumentException if the JSON is malformed or missing required properties.
         */
        fun fromJsonString(json: String) =
            Json.decodeFromString<AuthorizationServiceDiscovery>(json)
    }
}