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
import androidx.core.net.toUri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationServiceDiscoveryTest.Companion.TEST_JSON

/**
 * Contains common test values which are useful across all tests.
 */
@Suppress("unused")
internal object TestValues {
    const val TEST_CLIENT_ID: String = "test_client_id"
    const val TEST_STATE: String = $$"$TAT3"
    const val TEST_NONCE: String = "NONC3"
    const val TEST_APP_SCHEME: String = "com.test.app"

    @JvmField
    val TEST_APP_REDIRECT_URI: Uri = "$TEST_APP_SCHEME:/oidc_callback".toUri()
    const val TEST_SCOPE: String = "openid email"
    val TEST_IDP_AUTH_ENDPOINT: Uri = "https://testidp.example.com/authorize".toUri()
    val TEST_IDP_TOKEN_ENDPOINT: Uri = "https://testidp.example.com/token".toUri()
    val TEST_IDP_REGISTRATION_ENDPOINT: Uri = "https://testidp.example.com/token".toUri()

    const val TEST_CODE_VERIFIER: String = "0123456789_0123456789_0123456789_0123456789"
    const val TEST_AUTH_CODE: String = "zxcvbnmjk"
    const val TEST_ACCESS_TOKEN: String = "aaabbbccc"
    const val TEST_ACCESS_TOKEN_EXPIRATION_TIME: Long = 120000L // two minutes
    const val TEST_ISSUER: String = "https://test.issuer"

    @JvmField
    val TEST_ID_TOKEN: String = IdTokenTest.getUnsignedIdToken(
        TEST_ISSUER,
        IdTokenTest.TEST_SUBJECT,
        TEST_CLIENT_ID,
        null
    )
    const val TEST_REFRESH_TOKEN: String = "asdfghjkl"

    const val TEST_CLIENT_SECRET_EXPIRES_AT: Long = 78L
    const val TEST_CLIENT_SECRET: String = "test_client_secret"

    const val TEST_EMAIL_ADDRESS: String = "test@example.com"

    private fun List<String>.toJson() = Json.encodeToString(this)

    fun getDiscoveryDocument(
        issuer: String,
        authorizationEndpoint: String,
        tokenEndpoint: String,
        userInfoEndpoint: String,
        registrationEndpoint: String,
        endSessionEndpoint: String,
        jwksUri: String,
        responseTypesSupported: List<String>,
        subjectTypesSupported: List<String>,
        idTokenSigningAlgValues: List<String>,
        scopesSupported: List<String>,
        tokenEndpointAuthMethods: List<String>,
        claimsSupported: List<String>
    ) = AuthorizationServiceDiscovery(
        issuer = issuer,
        authorizationEndpoint = authorizationEndpoint.toUri(),
        tokenEndpoint = tokenEndpoint.toUri(),
        userinfoEndpoint = userInfoEndpoint.toUri(),
        registrationEndpoint = registrationEndpoint.toUri(),
        endSessionEndpoint = endSessionEndpoint.toUri(),
        jwksUri = jwksUri.toUri(),
        responseTypesSupported = responseTypesSupported,
        subjectTypesSupported = subjectTypesSupported,
        idTokenSigningAlgorithmValuesSupported = idTokenSigningAlgValues,
        scopesSupported = scopesSupported,
        tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethods,
        claimsSupported = claimsSupported
    )

    fun getDiscoveryDocumentJson(
        issuer: String,
        authorizationEndpoint: String,
        tokenEndpoint: String,
        userInfoEndpoint: String,
        registrationEndpoint: String,
        endSessionEndpoint: String,
        jwksUri: String,
        responseTypesSupported: List<String>,
        subjectTypesSupported: List<String>,
        idTokenSigningAlgValues: List<String>,
        scopesSupported: List<String>,
        tokenEndpointAuthMethods: List<String>,
        claimsSupported: List<String>
    ) = """
        {
            "issuer": "$issuer",
            "authorization_endpoint": "$authorizationEndpoint",
            "token_endpoint": "$tokenEndpoint",
            "userinfo_endpoint": "$userInfoEndpoint",
            "end_session_endpoint": "$endSessionEndpoint",
            "registration_endpoint": "$registrationEndpoint",
            "jwks_uri": "$jwksUri",
            "response_types_supported": ${responseTypesSupported.toJson()},
            "subject_types_supported": ${subjectTypesSupported.toJson()},
            "id_token_signing_alg_values_supported": ${idTokenSigningAlgValues.toJson()},
            "scopes_supported": ${scopesSupported.toJson()},
            "token_endpoint_auth_methods_supported": ${tokenEndpointAuthMethods.toJson()},
            "claims_supported": ${claimsSupported.toJson()}
        }
        """.trimIndent()

    @OptIn(ExperimentalSerializationApi::class)
    val testDiscoveryDocument: AuthorizationServiceDiscovery
        get() {
            try {
                return AuthorizationServiceDiscovery.fromJsonString(TEST_JSON)
            } catch (ex: SerializationException) {
                throw RuntimeException(
                    "Unable to create test authorization service discover document",
                    ex
                )
            }
        }

    @JvmStatic
    val testServiceConfig: AuthorizationServiceConfiguration
        get() = AuthorizationServiceConfiguration(testDiscoveryDocument)

    @JvmStatic
    fun getMinimalAuthRequestBuilder(responseType: String): AuthorizationRequest.Builder {
        return AuthorizationRequest.Builder(
            testServiceConfig,
            TEST_CLIENT_ID,
            responseType,
            TEST_APP_REDIRECT_URI
        )
    }

    @JvmStatic
    val testAuthRequestBuilder: AuthorizationRequest.Builder
        get() = getMinimalAuthRequestBuilder(ResponseTypeValues.CODE)
            .setScopes(AuthorizationRequest.Scope.OPENID, AuthorizationRequest.Scope.EMAIL)
            .setCodeVerifier(TEST_CODE_VERIFIER)

    @JvmStatic
    val testEndSessionRequestBuilder: EndSessionRequest.Builder
        get() = EndSessionRequest.Builder(testServiceConfig)
            .setIdTokenHint(TEST_ID_TOKEN)
            .setPostLogoutRedirectUri(TEST_APP_REDIRECT_URI)

    @JvmStatic
    val testAuthRequest: AuthorizationRequest
        get() = testAuthRequestBuilder
            .setNonce(null)
            .build()

    @JvmStatic
    val testEndSessionRequest: EndSessionRequest
        get() = testEndSessionRequestBuilder
            .build()

    @JvmStatic
    val testAuthResponseBuilder: AuthorizationResponse.Builder
        get() {
            val req: AuthorizationRequest = testAuthRequest
            return AuthorizationResponse.Builder(req)
                .setState(req.state)
                .setAuthorizationCode(TEST_AUTH_CODE)
        }

    @JvmStatic
    val testAuthResponse: AuthorizationResponse
        get() = testAuthResponseBuilder.build()

    val minimalTokenRequestBuilder: TokenRequest.Builder
        get() = TokenRequest.Builder(
            testServiceConfig,
            TEST_CLIENT_ID
        )

    @JvmStatic
    val testAuthCodeExchangeRequestBuilder: TokenRequest.Builder
        get() = minimalTokenRequestBuilder
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setCodeVerifier(TEST_CODE_VERIFIER)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)

    @JvmStatic
    val testAuthCodeExchangeRequest: TokenRequest
        get() = testAuthCodeExchangeRequestBuilder.build()

    @JvmStatic
    val testAuthCodeExchangeResponseBuilder: TokenResponse.Builder
        get() = TokenResponse.Builder(testAuthCodeExchangeRequest)
            .setTokenType(TokenResponse.TOKEN_TYPE_BEARER)
            .setRefreshToken(TEST_REFRESH_TOKEN)

    @JvmStatic
    val testAuthCodeExchangeResponse: TokenResponse
        get() = testAuthCodeExchangeResponseBuilder.build()

    val testRegistrationRequestBuilder: RegistrationRequest.Builder
        get() = RegistrationRequest.Builder(
            testServiceConfig,
            listOf(TEST_APP_REDIRECT_URI)
        )

    @JvmStatic
    val testRegistrationRequest: RegistrationRequest
        get() = testRegistrationRequestBuilder.build()

    @JvmStatic
    val testRegistrationResponseBuilder: RegistrationResponse.Builder
        get() = RegistrationResponse.Builder(testRegistrationRequest)
            .setClientId(TEST_CLIENT_ID)

    @JvmStatic
    val testRegistrationResponse: RegistrationResponse
        get() = testRegistrationResponseBuilder
            .setClientSecret(TEST_CLIENT_SECRET)
            .setClientSecretExpiresAt(TEST_CLIENT_SECRET_EXPIRES_AT)
            .build()

    fun getTestIdTokenWithNonce(nonce: String?): String {
        return IdTokenTest.getUnsignedIdToken(
            TEST_ISSUER,
            IdTokenTest.TEST_SUBJECT,
            TEST_CLIENT_ID,
            nonce
        )
    }
}
