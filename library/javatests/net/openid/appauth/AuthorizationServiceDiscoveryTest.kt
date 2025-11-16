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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import net.openid.appauth.TestValues.TEST_ISSUER
import net.openid.appauth.TestValues.getDiscoveryDocumentJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthorizationServiceDiscoveryTest {
    lateinit var discovery: AuthorizationServiceDiscovery

    @Before
    @Throws(Exception::class)
    fun setUp() {
        discovery = AuthorizationServiceDiscovery.fromJsonString(TEST_JSON)
    }

    @Test
    fun `test decode json string to AuthServiceDiscovery and back`() {
        val discoveryDoc = AuthorizationServiceDiscovery.fromJsonString(TEST_JSON)
        assertEquals(TestValues.testDiscoveryDocument, discoveryDoc)
        assertEquals(TEST_JSON_STRING, discoveryDoc.asJsonString)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    @Throws(Exception::class)
    fun `test missing mandatory fields`() {
        try {
            AuthorizationServiceDiscovery.fromJsonString(TEST_JSON_WITHOUT_MANDATORY_FIELD)
            fail("Expected MissingFieldException not thrown.")
        } catch (ex: MissingFieldException) {
            assertEquals(MANDATORY_FIELDS, ex.missingFields)
        }
    }

    @Test
    fun `test default value parameters supported`() {
        val discoveryDoc = AuthorizationServiceDiscovery
            .fromJsonString(TEST_JSON_WITHOUT_DEFAULT_FIELDS)

        assertFalse(discoveryDoc.isClaimsParameterSupported)
        assertFalse(discoveryDoc.isRequestParameterSupported)
        assertTrue(discoveryDoc.isRequestUriParameterSupported)
        assertFalse(discoveryDoc.requireRequestUriRegistration)
    }

    @Test
    fun testGetIssuer() {
        assertEquals(TEST_ISSUER, discovery.issuer)
    }

    @Test
    fun testGetAuthorizationEndpoint() {
        assertEquals(
            TEST_AUTHORIZATION_ENDPOINT,
            discovery.authorizationEndpoint.toString()
        )
    }

    @Test
    fun testGetTokenEndpoint() {
        assertEquals(TEST_TOKEN_ENDPOINT, discovery.tokenEndpoint.toString())
    }

    @Test
    fun testGetUserinfoEndpoint() {
        assertEquals(TEST_USERINFO_ENDPOINT, discovery.userinfoEndpoint.toString())
    }

    @Test
    fun testGetJwksUri() {
        assertEquals(TEST_JWKS_URI, discovery.jwksUri.toString())
    }

    @Test
    fun testGetResponseTypeSupported() {
        assertEquals(TEST_RESPONSE_TYPES_SUPPORTED, discovery.responseTypesSupported)
    }

    @Test
    fun testGetSubjectTypesSupported() {
        assertEquals(TEST_SUBJECT_TYPES_SUPPORTED, discovery.subjectTypesSupported)
    }

    @Test
    fun testGetIdTokenSigningAlgorithmValuesSupported() {
        assertEquals(
            TEST_ID_TOKEN_SIGNING_ALG_VALUES,
            discovery.idTokenSigningAlgorithmValuesSupported
        )
    }

    @Test
    fun testGetScopesSupported() {
        assertEquals(TEST_SCOPES_SUPPORTED, discovery.scopesSupported)
    }

    @Test
    fun testGetTokenEndpointAuthMethodsSupported() {
        assertEquals(
            TEST_TOKEN_ENDPOINT_AUTH_METHODS,
            discovery.tokenEndpointAuthMethodsSupported
        )
    }

    @Test
    fun testGetClaimsSupported() {
        assertEquals(TEST_CLAIMS_SUPPORTED, discovery.claimsSupported)
    }

    companion object {
        // ToDo: add more tests for remaining getters
        const val TEST_AUTHORIZATION_ENDPOINT: String = "http://test.openid.com/o/oauth/auth"
        const val TEST_TOKEN_ENDPOINT: String = "http://test.openid.com/o/oauth/token"
        const val TEST_USERINFO_ENDPOINT: String = "http://test.openid.com/o/oauth/userinfo"
        const val TEST_REGISTRATION_ENDPOINT: String = "http://test.openid.com/o/oauth/register"
        const val TEST_END_SESSION_ENDPOINT: String = "http://test.openid.com/o/oauth/logout"
        const val TEST_JWKS_URI: String = "http://test.openid.com/o/oauth/jwks"
        val TEST_RESPONSE_TYPES_SUPPORTED = listOf("code", "token")
        val TEST_SUBJECT_TYPES_SUPPORTED = listOf("public")
        val TEST_ID_TOKEN_SIGNING_ALG_VALUES = listOf("RS256")
        val TEST_SCOPES_SUPPORTED = listOf("openid", "profile")
        val TEST_TOKEN_ENDPOINT_AUTH_METHODS = listOf("client_secret_post", "client_secret_basic")
        val TEST_CLAIMS_SUPPORTED = listOf("aud", "exp")

        private fun List<String>.removeAll(list: List<String>): List<String> {
            return filterNot { string ->
                var result = false
                list.forEach {
                    if (string.contains(it)) {
                        result = true
                        return@forEach
                    }
                }

                result
            }
        }

        val TEST_JSON: String = getDiscoveryDocumentJson(
            issuer = TEST_ISSUER,
            authorizationEndpoint = TEST_AUTHORIZATION_ENDPOINT,
            tokenEndpoint = TEST_TOKEN_ENDPOINT,
            userInfoEndpoint = TEST_USERINFO_ENDPOINT,
            registrationEndpoint = TEST_REGISTRATION_ENDPOINT,
            endSessionEndpoint = TEST_END_SESSION_ENDPOINT,
            jwksUri = TEST_JWKS_URI,
            responseTypesSupported = TEST_RESPONSE_TYPES_SUPPORTED,
            subjectTypesSupported = TEST_SUBJECT_TYPES_SUPPORTED,
            idTokenSigningAlgValues = TEST_ID_TOKEN_SIGNING_ALG_VALUES,
            scopesSupported = TEST_SCOPES_SUPPORTED,
            tokenEndpointAuthMethods = TEST_TOKEN_ENDPOINT_AUTH_METHODS,
            claimsSupported = TEST_CLAIMS_SUPPORTED
        )

        val MANDATORY_FIELDS = listOf(
            "issuer",
            "authorization_endpoint",
            "jwks_uri",
            "response_types_supported",
            "subject_types_supported",
            "id_token_signing_alg_values_supported"
        )

        val DEFAULT_FIELDS = listOf(
            "claims_parameter_supported",
            "request_parameter_supported",
            "request_uri_parameter_supported",
            "require_request_uri_registration"
        )

        val TEST_JSON_WITHOUT_MANDATORY_FIELD = TEST_JSON
            .split("\n")
            .removeAll(MANDATORY_FIELDS)
            .joinToString("")

        val TEST_JSON_WITHOUT_DEFAULT_FIELDS = TEST_JSON
            .split("\n")
            .removeAll(DEFAULT_FIELDS)
            .joinToString("")

        val TEST_JSON_STRING: String = TestValues.testDiscoveryDocument.asJsonString
    }
}
