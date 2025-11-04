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
import org.json.JSONException
import org.json.JSONObject

/**
 * An OpenID Connect 1.0 Discovery Document.
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html.rfc#section.3">
 *     OpenID Connect discovery 1.0, Section 3</a>
 */
@Suppress("unused")
class AuthorizationServiceDiscovery(
    /**
     * The JSON representation of the discovery document.
     */
    @JvmField
    val discoveryDoc: JSONObject
) {
    /**
     * Extracts a discovery document from its standard JSON representation.
     * @throws org.json.JSONException if the provided JSON does not match the expected structure.
     * @throws MissingArgumentException if a mandatory property is missing from the discovery
     * document.
     */
    init {
        for (mandatory in MANDATORY_METADATA) {
            if (!discoveryDoc.has(mandatory)) {
                throw MissingArgumentException(mandatory)
            } else {
                try {
                    discoveryDoc[mandatory]
                } catch (_: JSONException) {
                    throw MissingArgumentException(mandatory)
                }

            }
        }
    }

    /**
     * Thrown when a mandatory property is missing from the discovery document.
     * Indicates that the specified mandatory field is missing from the discovery document.
     */
    class MissingArgumentException(val missingField: String) :
        Exception("Missing mandatory configuration field: $missingField")

    /**
     * Retrieves a metadata value from the discovery document. This need only be used
     * for the retrieval of a non-standard metadata value. Convenience methods are defined on this
     * class for all standard metadata values.
     */
    private fun <T> get(field: Field<T>) = discoveryDoc[field]

    /**
     * Retrieves a metadata value from the discovery document. This need only be used
     * for the retrieval of a non-standard metadata value. Convenience methods are defined on this
     * class for all standard metadata values.
     */
    private fun <T> get(field: ListField<T>) = discoveryDoc[field]

    /**
     * The asserted issuer identifier.
     */
    val issuer: String
        get() = get(ISSUER)!!

    /**
     * The OAuth 2 authorization endpoint URI.
     */
    val authorizationEndpoint: Uri
        get() = get(AUTHORIZATION_ENDPOINT)!!

    /**
     * The OAuth 2 token endpoint URI. Not specified if only the implicit flow is used.
     */
    val tokenEndpoint: Uri?
        get() = get(TOKEN_ENDPOINT)

    /**
     * The OAuth 2 emd session endpoint URI. Not specified test OAuth implementation
     */
    val endSessionEndpoint: Uri?
        get() = get(END_SESSION_ENDPOINT)

    /**
     * The OpenID Connect UserInfo endpoint URI.
     */
    val userinfoEndpoint: Uri?
        get() = get(USERINFO_ENDPOINT)

    /**
     * The JSON web key set document URI.
     *
     * @see "JSON Web Key
     */
    val jwksUri: Uri
        get() = get(JWKS_URI)!!

    /**
     * The dynamic client registration endpoint URI.
     */
    val registrationEndpoint: Uri?
        get() = get(REGISTRATION_ENDPOINT)

    /**
     * The OAuth 2 `scope` values supported.
     *
     * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">
     *     OpenID Connect Dynamic Client Registration 1.0
     */
    val scopesSupported: List<String>?
        get() = get(SCOPES_SUPPORTED)

    /**
     * The OAuth 2 `response_type` values supported.
     */
    val responseTypesSupported: List<String>
        get() = get(RESPONSE_TYPES_SUPPORTED)!!

    /**
     * The OAuth 2 `response_mode` values supported.
     *
     * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html">
     *     OAuth 2.0 Multiple Response Type Encoding Practices</a>
     */
    val responseModesSupported: List<String>?
        get() = get(RESPONSE_MODES_SUPPORTED)

    /**
     * The OAuth 2 `grant_type` values supported. Defaults to `authorization_code` and `implicit`
     * if not specified in the discovery document, as suggested by the discovery specification.
     */
    val grantTypesSupported: List<String>
        get() = get(GRANT_TYPES_SUPPORTED)!!

    /**
     * The authentication context class references supported.
     */
    val acrValuesSupported: List<String>?
        get() = get(ACR_VALUES_SUPPORTED)

    /**
     * The subject identifier types supported.
     */
    val subjectTypesSupported: List<String>
        get() = get(SUBJECT_TYPES_SUPPORTED)!!

    /**
     * The JWS signing algorithms (alg values) supported for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    val idTokenSigningAlgorithmValuesSupported: List<String>
        get() = get(ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED)!!

    /**
     * The JWE encryption algorithms (alg values) supported for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    val idTokenEncryptionAlgorithmValuesSupported: List<String>?
        get() = get(ID_TOKEN_ENCRYPTION_ALG_VALUES_SUPPORTED)

    /**
     * The JWE encryption encodings (enc values) supported for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    val idTokenEncryptionEncodingValuesSupported: List<String>?
        get() = get(ID_TOKEN_ENCRYPTION_ENC_VALUES_SUPPORTED)

    /**
     * The JWS signing algorithms (alg values) supported by the UserInfo Endpoint
     * for encoding ID token claims.
     *
     * @see "JSON Web Signature
     * @see "JSON Web Algorithms
     * @see "JSON Web Token
     */
    val userinfoSigningAlgorithmValuesSupported: List<String>?
        get() = get(USERINFO_SIGNING_ALG_VALUES_SUPPORTED)

    /**
     * The JWE encryption algorithms (alg values) supported by the UserInfo Endpoint
     * for encoding ID token claims.
     *
     * @see "JSON Web Signature
     * @see "JSON Web Algorithms
     * @see "JSON Web Token
     */
    val userinfoEncryptionAlgorithmValuesSupported: List<String>?
        get() = get(USERINFO_ENCRYPTION_ALG_VALUES_SUPPORTED)

    /**
     * The JWE encryption encodings (enc values) supported by the UserInfo Endpoint
     * for encoding ID token claims.
     *
     * @see "JSON Web Token
     */
    val userinfoEncryptionEncodingValuesSupported: List<String>?
        get() = get(USERINFO_ENCRYPTION_ENC_VALUES_SUPPORTED)

    /**
     * The JWS signing algorithms (alg values) supported for Request Objects.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.1">
     *     OpenID Connect Core 1.0, Section 6.1</a>
     */
    val requestObjectSigningAlgorithmValuesSupported: List<String>?
        get() = get(REQUEST_OBJECT_SIGNING_ALG_VALUES_SUPPORTED)

    /**
     * The JWE encryption algorithms (alg values) supported for Request Objects.
     */
    val requestObjectEncryptionAlgorithmValuesSupported: List<String>?
        get() = get(REQUEST_OBJECT_ENCRYPTION_ALG_VALUES_SUPPORTED)

    /**
     * The JWE encryption encodings (enc values) supported for Request Objects.
     */
    val requestObjectEncryptionEncodingValuesSupported: List<String>?
        get() = get(REQUEST_OBJECT_ENCRYPTION_ENC_VALUES_SUPPORTED)

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
    val tokenEndpointAuthMethodsSupported: List<String>
        get() = get(TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED)!!

    /**
     * The JWS signing algorithms (alg values) supported by the token endpoint for the signature on
     * the JWT used to authenticate the client for the `private_key_jwt` and
     * `client_secret_jwt` authentication methods.
     *
     * @see "JSON Web Token"
     */
    val tokenEndpointAuthSigningAlgorithmValuesSupported: List<String>?
        get() = get(TOKEN_ENDPOINT_AUTH_SIGNING_ALG_VALUES_SUPPORTED)

    /**
     * The `display` parameter values supported.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1">
     *     OpenID Connect Core 1.0, Section 3.1.2.1</a>
     */
    val displayValuesSupported: List<String>?
        get() = get(DISPLAY_VALUES_SUPPORTED)

    /**
     * The claim types supported. Defaults to `normal` if not specified by the discovery
     * document JSON, as suggested by the discovery specification.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.6">
     *     OpenID Connect Core 1.0, Section 5.6</a>
     */
    val claimTypesSupported: List<String>?
        get() = get(CLAIM_TYPES_SUPPORTED)

    /**
     * The claim names of the claims that the provider _may_ be able to supply values for.
     */
    val claimsSupported: List<String>?
        get() = get(CLAIMS_SUPPORTED)

    /**
     * A page containing human-readable information that developers might want or need to know when
     * using this provider.
     */
    val serviceDocumentation: Uri?
        get() = get(SERVICE_DOCUMENTATION)

    /**
     * Languages and scripts supported for values in claims being returned.
     * Represented as a list of BCP47 language tag values.
     *
     * @see "Tags for Identifying Languages"
     */
    val claimsLocalesSupported: List<String>?
        get() = get(CLAIMS_LOCALES_SUPPORTED)

    /**
     * Languages and scripts supported for the user interface.
     * Represented as a list of BCP47 language tag values.
     *
     * @see "Tags for Identifying Languages"
     */
    val uiLocalesSupported: List<String>?
        get() = get(UI_LOCALES_SUPPORTED)

    /**
     * Specifies whether the `claims` parameter is supported for authorization requests.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.5.5">
     *     OpenID Connect Core 1.0, Section 5.5</a>
     */
    val isClaimsParameterSupported: Boolean?
        get() = get(CLAIMS_PARAMETER_SUPPORTED)

    /**
     * Specifies whether the `request` parameter is supported for authorization requests.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.1">
     *     OpenID Connect Core 1.0, Section 6.1</a>
     */
    val isRequestParameterSupported: Boolean?
        get() = get(REQUEST_PARAMETER_SUPPORTED)

    /**
     * Specifies whether the `request_uri` parameter is supported for authorization requests.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2">
     *     OpenID Connect Core 1.0, Section 6.2</a>
     */
    val isRequestUriParameterSupported: Boolean?
        get() = get(REQUEST_URI_PARAMETER_SUPPORTED)

    /**
     * Specifies whether `request_uri` values are required to be pre-registered before use.
     *
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#rfc.section.6.2">
     *     OpenID Connect Core 1.0, Section 6.2</a>
     */
    val requireRequestUriRegistration: Boolean?
        get() = get(REQUIRE_REQUEST_URI_REGISTRATION)

    /**
     * A page articulating the policy regarding the use of data provided by the provider.
     */
    val opPolicyUri: Uri?
        get() = get(OP_POLICY_URI)

    /**
     * A page articulating the terms of service for the provider.
     */
    val opTosUri: Uri?
        get() = get(OP_TOS_URI)

    companion object {
        @JvmField
        @VisibleForTesting
        val ISSUER: StringField = str("issuer")

        @JvmField
        @VisibleForTesting
        val AUTHORIZATION_ENDPOINT: UriField = uri("authorization_endpoint")

        @VisibleForTesting
        val TOKEN_ENDPOINT: UriField = uri("token_endpoint")

        @VisibleForTesting
        val END_SESSION_ENDPOINT: UriField = uri("end_session_endpoint")

        @VisibleForTesting
        val USERINFO_ENDPOINT: UriField = uri("userinfo_endpoint")

        @JvmField
        @VisibleForTesting
        val JWKS_URI: UriField = uri("jwks_uri")

        @VisibleForTesting
        val REGISTRATION_ENDPOINT: UriField = uri("registration_endpoint")

        @VisibleForTesting
        val SCOPES_SUPPORTED: StringListField = strList("scopes_supported")

        @JvmField
        @VisibleForTesting
        val RESPONSE_TYPES_SUPPORTED: StringListField = strList("response_types_supported")

        @VisibleForTesting
        val RESPONSE_MODES_SUPPORTED: StringListField = strList("response_modes_supported")

        @VisibleForTesting
        val GRANT_TYPES_SUPPORTED: StringListField = strList(
            "grant_types_supported",
            listOf("authorization_code", "implicit")
        )

        @VisibleForTesting
        val ACR_VALUES_SUPPORTED: StringListField = strList("acr_values_supported")

        @JvmField
        @VisibleForTesting
        val SUBJECT_TYPES_SUPPORTED: StringListField = strList("subject_types_supported")

        @JvmField
        @VisibleForTesting
        val ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED: StringListField =
            strList("id_token_signing_alg_values_supported")

        @VisibleForTesting
        val ID_TOKEN_ENCRYPTION_ALG_VALUES_SUPPORTED: StringListField =
            strList("id_token_encryption_enc_values_supported")

        @VisibleForTesting
        val ID_TOKEN_ENCRYPTION_ENC_VALUES_SUPPORTED: StringListField =
            strList("id_token_encryption_enc_values_supported")

        @VisibleForTesting
        val USERINFO_SIGNING_ALG_VALUES_SUPPORTED: StringListField =
            strList("userinfo_signing_alg_values_supported")

        @VisibleForTesting
        val USERINFO_ENCRYPTION_ALG_VALUES_SUPPORTED: StringListField =
            strList("userinfo_encryption_alg_values_supported")

        @VisibleForTesting
        val USERINFO_ENCRYPTION_ENC_VALUES_SUPPORTED: StringListField =
            strList("userinfo_encryption_enc_values_supported")

        @VisibleForTesting
        val REQUEST_OBJECT_SIGNING_ALG_VALUES_SUPPORTED: StringListField =
            strList("request_object_signing_alg_values_supported")

        @VisibleForTesting
        val REQUEST_OBJECT_ENCRYPTION_ALG_VALUES_SUPPORTED: StringListField =
            strList("request_object_encryption_alg_values_supported")

        @VisibleForTesting
        val REQUEST_OBJECT_ENCRYPTION_ENC_VALUES_SUPPORTED: StringListField =
            strList("request_object_encryption_enc_values_supported")

        @VisibleForTesting
        val TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED: StringListField = strList(
            "token_endpoint_auth_methods_supported",
            listOf("client_secret_basic")
        )

        @VisibleForTesting
        val TOKEN_ENDPOINT_AUTH_SIGNING_ALG_VALUES_SUPPORTED: StringListField =
            strList("token_endpoint_auth_signing_alg_values_supported")

        @VisibleForTesting
        val DISPLAY_VALUES_SUPPORTED: StringListField = strList("display_values_supported")

        @VisibleForTesting
        val CLAIM_TYPES_SUPPORTED: StringListField =
            strList("claim_types_supported", listOf("normal"))

        @VisibleForTesting
        val CLAIMS_SUPPORTED: StringListField = strList("claims_supported")

        @VisibleForTesting
        val SERVICE_DOCUMENTATION: UriField = uri("service_documentation")

        @VisibleForTesting
        val CLAIMS_LOCALES_SUPPORTED: StringListField = strList("claims_locales_supported")

        @VisibleForTesting
        val UI_LOCALES_SUPPORTED: StringListField = strList("ui_locales_supported")

        @JvmField
        @VisibleForTesting
        val CLAIMS_PARAMETER_SUPPORTED: BooleanField = bool("claims_parameter_supported", false)

        @JvmField
        @VisibleForTesting
        val REQUEST_PARAMETER_SUPPORTED: BooleanField = bool("request_parameter_supported", false)

        @JvmField
        @VisibleForTesting
        val REQUEST_URI_PARAMETER_SUPPORTED: BooleanField =
            bool("request_uri_parameter_supported", true)

        @JvmField
        @VisibleForTesting
        val REQUIRE_REQUEST_URI_REGISTRATION: BooleanField =
            bool("require_request_uri_registration", false)

        @VisibleForTesting
        val OP_POLICY_URI: UriField = uri("op_policy_uri")

        @VisibleForTesting
        val OP_TOS_URI: UriField = uri("op_tos_uri")

        /**
         * The fields which are marked as mandatory in the OpenID discovery spec.
         */
        private val MANDATORY_METADATA: List<String> = listOf(
            ISSUER.key,
            AUTHORIZATION_ENDPOINT.key,
            JWKS_URI.key,
            RESPONSE_TYPES_SUPPORTED.key,
            SUBJECT_TYPES_SUPPORTED.key,
            ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED.key
        )

        /**
         * Shorthand method for creating a string metadata extractor.
         */
        private fun str(@Suppress("SameParameterValue") key: String) = StringField(key)

        /**
         * Shorthand method for creating a URI metadata extractor.
         */
        private fun uri(key: String) = UriField(key)

        /**
         * Shorthand method for creating a string list metadata extractor.
         */
        private fun strList(key: String) = StringListField(key)

        /**
         * Shorthand method for creating a string list metadata extractor, with a default value.
         */
        private fun strList(key: String, defaults: List<String>) = StringListField(key, defaults)

        /**
         * Shorthand method for creating a boolean metadata extractor.
         */
        private fun bool(key: String, defaultValue: Boolean) = BooleanField(key, defaultValue)
    }
}
