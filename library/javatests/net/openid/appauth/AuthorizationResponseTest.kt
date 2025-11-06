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
import net.openid.appauth.AuthorizationResponse.Companion.jsonDeserialize
import net.openid.appauth.TestValues.TEST_ACCESS_TOKEN
import net.openid.appauth.TestValues.TEST_AUTH_CODE
import net.openid.appauth.TestValues.TEST_CODE_VERIFIER
import net.openid.appauth.TestValues.TEST_ID_TOKEN
import net.openid.appauth.TestValues.TEST_STATE
import net.openid.appauth.TestValues.getMinimalAuthRequestBuilder
import net.openid.appauth.TestValues.testAuthRequest
import net.openid.appauth.TestValues.testAuthRequestBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthorizationResponseTest {
    private lateinit var authorizationResponseBuilder: AuthorizationResponse.Builder
    private lateinit var authorizationResponse: AuthorizationResponse
    private lateinit var clock: TestClock

    @Before
    fun setUp() {
        clock = TestClock(TEST_START_TIME)
        authorizationResponseBuilder = AuthorizationResponse.Builder(testAuthRequest)
            .setState(TEST_STATE)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setTokenType(AuthorizationResponse.TOKEN_TYPE_BEARER)
            .setIdToken(TEST_ID_TOKEN)
            .setAccessTokenExpirationTime(TEST_TOKEN_EXPIRE_TIME)

        authorizationResponse = authorizationResponseBuilder.build()
    }

    @Test
    fun testBuilder() {
        checkExpectedFields(authorizationResponseBuilder.build())
    }

    @Test
    fun testBuildFromUri() {
        val authRequest = testAuthRequestBuilder
            .setState(TEST_STATE)
            .build()

        val authResponse = AuthorizationResponse.Builder(authRequest)
            .fromUri(TEST_URI, clock)
            .build()

        checkExpectedFields(authResponse)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuild_setAdditionalParams_withBuiltInParam() {
        authorizationResponseBuilder.setAdditionalParameters(
            mapOf(AuthorizationResponse.KEY_SCOPE to "scope")
        )
    }

    @Test
    fun testExpiresIn() {
        val authResponse = authorizationResponseBuilder
            .setAccessTokenExpiresIn(TEST_EXPIRES_IN, clock)
            .build()

        assertEquals(TEST_TOKEN_EXPIRE_TIME, authResponse.accessTokenExpirationTime)
    }

    @Test
    fun testHasExpired() {
        clock.currentTime.set(TEST_START_TIME + 1)
        assertFalse(authorizationResponse.hasAccessTokenExpired(clock))
        clock.currentTime.set(TEST_TOKEN_EXPIRE_TIME - 1)
        assertFalse(authorizationResponse.hasAccessTokenExpired(clock))
        clock.currentTime.set(TEST_TOKEN_EXPIRE_TIME + 1)
        assertTrue(authorizationResponse.hasAccessTokenExpired(clock))
    }

    @Test
    @Throws(Exception::class)
    fun testSerialization() {
        val json = authorizationResponse.jsonSerializeString()
        val authResponse = jsonDeserialize(json)
        checkExpectedFields(authResponse)
    }

    @Test
    fun testCreateTokenExchangeRequest() {
        val tokenExchangeRequest = authorizationResponse.createTokenExchangeRequest()
        assertThat(tokenExchangeRequest.grantType).isEqualTo(GrantTypeValues.AUTHORIZATION_CODE)
        assertThat(tokenExchangeRequest.codeVerifier).isEqualTo(TEST_CODE_VERIFIER)
        assertThat(tokenExchangeRequest.authorizationCode).isEqualTo(TEST_AUTH_CODE)
    }

    @Test
    fun testCreateTokenExchangeRequest_failsForImplicitFlowResponse() {
        // simulate an implicit flow request and response
        val request = getMinimalAuthRequestBuilder(ResponseTypeValues.TOKEN).build()
        val response = AuthorizationResponse.Builder(request)
            .setAccessToken("token")
            .setTokenType(AuthorizationResponse.TOKEN_TYPE_BEARER)
            .setAccessTokenExpiresIn(TimeUnit.DAYS.toSeconds(30))
            .setState(request.state)
            .build()

        // as there is no authorization code in the response, this will fail
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { response.createTokenExchangeRequest() }
            .withMessage("authorizationCode not available for exchange request")
    }

    @Test
    fun testCreateTokenExchangeRequest_failsForImplicitResponse() {
    }

    @Test
    fun testCreateTokenExchangeRequest_authResponseScopesAreIgnored() {
        val response = authorizationResponseBuilder
            .setScopes(AuthorizationRequest.Scope.EMAIL)
            .build()

        val tokenExchangeRequest = response.createTokenExchangeRequest()
        assertThat(tokenExchangeRequest.scope).isNull()
    }

    private fun checkExpectedFields(authResponse: AuthorizationResponse) {
        assertEquals("state does not match", TEST_STATE, authResponse.state)

        assertEquals(
            "authorization code does not match",
            TEST_AUTH_CODE,
            authResponse.authorizationCode
        )

        assertEquals(
            "access token does not match",
            TEST_ACCESS_TOKEN, authResponse.accessToken
        )

        assertEquals(
            "token type does not match",
            AuthorizationResponse.TOKEN_TYPE_BEARER, authResponse.tokenType
        )

        assertEquals(
            "id token does not match",
            TEST_ID_TOKEN, authResponse.idToken
        )

        assertEquals(
            "access token expiration time does not match",
            TEST_TOKEN_EXPIRE_TIME, authResponse.accessTokenExpirationTime
        )
    }

    companion object {
        // the test is asserted to be running at time 23
        private const val TEST_START_TIME = 23L

        // expiration time, in seconds
        private const val TEST_EXPIRES_IN = 78L
        private const val TEST_TOKEN_EXPIRE_TIME = 78023L

        private val TEST_URI: Uri = Uri.Builder()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, TEST_STATE)
            .appendQueryParameter(AuthorizationResponse.KEY_AUTHORIZATION_CODE, TEST_AUTH_CODE)
            .appendQueryParameter(AuthorizationResponse.KEY_ACCESS_TOKEN, TEST_ACCESS_TOKEN)
            .appendQueryParameter(
                AuthorizationResponse.KEY_TOKEN_TYPE,
                AuthorizationResponse.TOKEN_TYPE_BEARER
            )
            .appendQueryParameter(AuthorizationResponse.KEY_ID_TOKEN, TEST_ID_TOKEN)
            .appendQueryParameter(
                AuthorizationResponse.KEY_EXPIRES_IN,
                TEST_EXPIRES_IN.toString()
            )
            .build()
    }
}
