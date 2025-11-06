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

import net.openid.appauth.AuthorizationServiceDiscovery.Companion.AUTHORIZATION_ENDPOINT
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.CLAIMS_PARAMETER_SUPPORTED
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.ISSUER
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.JWKS_URI
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.REQUEST_PARAMETER_SUPPORTED
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.REQUEST_URI_PARAMETER_SUPPORTED
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.REQUIRE_REQUEST_URI_REGISTRATION
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.RESPONSE_TYPES_SUPPORTED
import net.openid.appauth.AuthorizationServiceDiscovery.Companion.SUBJECT_TYPES_SUPPORTED
import net.openid.appauth.TestValues.TEST_ISSUER
import net.openid.appauth.TestValues.getDiscoveryDocumentJson
import org.json.JSONObject
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthorizationServiceDiscoveryTest {
    lateinit var json: JSONObject
    lateinit var discovery: AuthorizationServiceDiscovery

    @Before
    @Throws(Exception::class)
    fun setUp() {
        json = JSONObject(TEST_JSON)
        discovery = AuthorizationServiceDiscovery(json)
    }

    @Test
    @Throws(Exception::class)
    fun testMissingAuthorizationEndpoint() {
        json.remove(AUTHORIZATION_ENDPOINT.key)

        try {
            AuthorizationServiceDiscovery(json)
            fail("Expected MissingArgumentException not thrown.")
        } catch (e: AuthorizationServiceDiscovery.MissingArgumentException) {
            assertEquals(
                AUTHORIZATION_ENDPOINT.key,
                e.missingField
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMissingIssuer() {
        json.remove(ISSUER.key)

        try {
            AuthorizationServiceDiscovery(json)
            fail("Expected MissingArgumentException not thrown.")
        } catch (e: AuthorizationServiceDiscovery.MissingArgumentException) {
            assertEquals(
                ISSUER.key,
                e.missingField
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMissingJwksUri() {
        json.remove(JWKS_URI.key)
        try {
            AuthorizationServiceDiscovery(json)
            fail("Expected MissingArgumentException not thrown.")
        } catch (e: AuthorizationServiceDiscovery.MissingArgumentException) {
            assertEquals(
                JWKS_URI.key,
                e.missingField
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMissingSubjectTypesSupported() {
        json.remove(SUBJECT_TYPES_SUPPORTED.key)

        try {
            AuthorizationServiceDiscovery(json)
            fail("Expected MissingArgumentException not thrown.")
        } catch (e: AuthorizationServiceDiscovery.MissingArgumentException) {
            assertEquals(
                SUBJECT_TYPES_SUPPORTED.key,
                e.missingField
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMissingResponseTypesSupported() {
        json.remove(RESPONSE_TYPES_SUPPORTED.key)

        try {
            AuthorizationServiceDiscovery(json)
            fail("Expected MissingArgumentException not thrown.")
        } catch (e: AuthorizationServiceDiscovery.MissingArgumentException) {
            assertEquals(
                RESPONSE_TYPES_SUPPORTED.key,
                e.missingField
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMissingIdTokenSigningAlgValuesSupported() {
        json.remove(ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED.key)

        try {
            AuthorizationServiceDiscovery(json)
            fail("Expected MissingArgumentException not thrown.")
        } catch (e: AuthorizationServiceDiscovery.MissingArgumentException) {
            assertEquals(
                ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED.key,
                e.missingField
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDefaultValueClaimsParametersSupported() {
        json.remove(CLAIMS_PARAMETER_SUPPORTED.key)
        assertFalse(AuthorizationServiceDiscovery(json).isClaimsParameterSupported == true)
    }

    @Test
    @Throws(Exception::class)
    fun testDefaultValueRequestParameterSupported() {
        json.remove(REQUEST_PARAMETER_SUPPORTED.key)
        assertFalse(AuthorizationServiceDiscovery(json).isRequestParameterSupported == true)
    }

    @Test
    @Throws(Exception::class)
    fun testDefaultValueRequestUriParameterSupported() {
        json.remove(REQUEST_URI_PARAMETER_SUPPORTED.key)
        Assert.assertTrue(AuthorizationServiceDiscovery(json).isRequestUriParameterSupported == true)
    }

    @Test
    @Throws(Exception::class)
    fun testDefaultValueRequireRequestUriRegistration() {
        json.remove(REQUIRE_REQUEST_URI_REGISTRATION.key)
        assertFalse(AuthorizationServiceDiscovery(json).requireRequestUriRegistration == true)
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

        val TEST_JSON: String = getDiscoveryDocumentJson(
            TEST_ISSUER,
            TEST_AUTHORIZATION_ENDPOINT,
            TEST_TOKEN_ENDPOINT,
            TEST_USERINFO_ENDPOINT,
            TEST_REGISTRATION_ENDPOINT,
            TEST_END_SESSION_ENDPOINT,
            TEST_JWKS_URI,
            TEST_RESPONSE_TYPES_SUPPORTED,
            TEST_SUBJECT_TYPES_SUPPORTED,
            TEST_ID_TOKEN_SIGNING_ALG_VALUES,
            TEST_SCOPES_SUPPORTED,
            TEST_TOKEN_ENDPOINT_AUTH_METHODS,
            TEST_CLAIMS_SUPPORTED
        )
    }
}
