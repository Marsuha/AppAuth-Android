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

import net.openid.appauth.TestValues.TEST_APP_REDIRECT_URI
import net.openid.appauth.TestValues.TEST_CLIENT_ID
import net.openid.appauth.TestValues.TEST_CODE_VERIFIER
import net.openid.appauth.TestValues.testServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TokenRequestTest {
    private lateinit var minimalBuilder: TokenRequest.Builder
    private lateinit var authorizationCodeRequestBuilder: TokenRequest.Builder

    @Before
    fun setUp() {
        minimalBuilder = TokenRequest.Builder(
            testServiceConfig,
            TEST_CLIENT_ID
        )

        authorizationCodeRequestBuilder = TokenRequest.Builder(
            testServiceConfig,
            TEST_CLIENT_ID
        )
            .setAuthorizationCode(TEST_AUTHORIZATION_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuild_emptyClientId() {
        TokenRequest.Builder(testServiceConfig, "")
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuild_emptyAuthorizationCode() {
        minimalBuilder
            .setAuthorizationCode("")
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuild_emptyRefreshToken() {
        minimalBuilder
            .setRefreshToken("")
            .build()
    }

    @Test(expected = IllegalStateException::class)
    fun testBuild_noRedirectUriForAuthorizationCodeExchange() {
        minimalBuilder
            .setAuthorizationCode(TEST_AUTHORIZATION_CODE)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuilder_setAdditionalParams_withBuiltInParam() {
        minimalBuilder.setAdditionalParameters(
            mapOf(TokenRequest.PARAM_SCOPE to "scope")
        )
    }


    @Test(expected = IllegalArgumentException::class)
    fun testBuild_badScopeString() {
        minimalBuilder
            .setScopes("")
            .build()
    }

    @Test
    fun testGetRequestParameters_forCodeExchange() {
        val request = authorizationCodeRequestBuilder.build()

        val params = request.requestParameters
        assertThat(params).containsEntry(
            TokenRequest.PARAM_GRANT_TYPE,
            GrantTypeValues.AUTHORIZATION_CODE
        )

        assertThat(params).containsEntry(
            TokenRequest.PARAM_CODE,
            TEST_AUTHORIZATION_CODE
        )
        assertThat(params).containsEntry(
            TokenRequest.PARAM_REDIRECT_URI,
            TEST_APP_REDIRECT_URI.toString()
        )
    }

    @Test
    fun testGetRequestParameters_forRefreshToken() {
        val request = minimalBuilder
            .setRefreshToken(TEST_REFRESH_TOKEN)
            .build()

        val params = request.requestParameters
        assertThat(params).containsEntry(
            TokenRequest.PARAM_GRANT_TYPE,
            GrantTypeValues.REFRESH_TOKEN
        )

        assertThat(params).containsEntry(
            TokenRequest.PARAM_REFRESH_TOKEN,
            TEST_REFRESH_TOKEN
        )
    }

    @Test
    fun testGetRequestParameters_withCodeVerifier() {
        val request = authorizationCodeRequestBuilder
            .setCodeVerifier(TEST_CODE_VERIFIER)
            .build()

        assertThat(request.requestParameters)
            .containsEntry(TokenRequest.PARAM_CODE_VERIFIER, TEST_CODE_VERIFIER)
    }

    @Test
    fun testToUri_withScope() {
        val request = minimalBuilder
            .setRefreshToken(TEST_REFRESH_TOKEN)
            .setScope("email profile")
            .build()

        assertThat(request.requestParameters)
            .containsEntry(TokenRequest.PARAM_SCOPE, "email profile")
    }

    @Test
    fun testToUri_withAdditionalParameters() {
        val additionalParams = mapOf("p1" to "v1", "p2" to "v2")

        val request = authorizationCodeRequestBuilder
            .setAdditionalParameters(additionalParams)
            .build()

        val params = request.requestParameters
        assertThat(params).containsEntry("p1", "v1")
        assertThat(params).containsEntry("p2", "v2")
    }

    companion object {
        private const val TEST_AUTHORIZATION_CODE = "ABCDEFGH"
        private const val TEST_REFRESH_TOKEN = "IJKLMNOP"
    }
}
