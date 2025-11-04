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
import net.openid.appauth.TestValues.TEST_AUTH_CODE
import net.openid.appauth.TestValues.TEST_CLIENT_ID
import net.openid.appauth.TestValues.TEST_ID_TOKEN
import net.openid.appauth.TestValues.TEST_REFRESH_TOKEN
import net.openid.appauth.TestValues.TEST_SCOPE
import net.openid.appauth.TestValues.testServiceConfig
import net.openid.appauth.TokenResponse.Companion.jsonDeserialize
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [16])
class TokenResponseTest {
    private lateinit var minimalBuilder: TokenResponse.Builder

    @Before
    fun setUp() {
        val request = TokenRequest.Builder(testServiceConfig, TEST_CLIENT_ID)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)
            .build()

        minimalBuilder = TokenResponse.Builder(request)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuilder_setAdditionalParams_withBuiltInParam() {
        minimalBuilder.setAdditionalParameters(mapOf(TokenRequest.PARAM_SCOPE to "scope"))
    }

    @Test
    @Throws(JSONException::class)
    fun testBuilder_fromResponseJsonStringWithScope() {
        val tokenResponse = minimalBuilder.fromResponseJsonString(TEST_JSON_WITH_SCOPE).build()

        assertNotNull(tokenResponse)
        assertEquals(TEST_KEY_ACCESS_TOKEN, tokenResponse.accessToken)
        assertEquals(TEST_KEY_TOKEN_TYPE, tokenResponse.tokenType)
        assertEquals(TEST_KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
        assertEquals(TEST_KEY_ID_TOKEN, tokenResponse.idToken)
        assertEquals(TEST_KEY_KEY_EXPIRES_AT, tokenResponse.accessTokenExpirationTime)

        assertEquals(TEST_KEY_SCOPES, tokenResponse.scope)
        assertThat(tokenResponse.scopeSet).isEqualTo(
            setOf(TEST_KEY_SCOPE_1, TEST_KEY_SCOPE_2, TEST_KEY_SCOPE_3)
        )
    }

    @Test(expected = JSONException::class)
    @Throws(JSONException::class)
    fun testBuilder_fromResponseJsonString_emptyJson() {
        val tokenResponse = minimalBuilder.fromResponseJsonString("{}").build()

        assertNotNull(tokenResponse)

        assertEquals(TEST_KEY_ACCESS_TOKEN, tokenResponse.accessToken)
        assertEquals(TEST_KEY_TOKEN_TYPE, tokenResponse.tokenType)
        assertEquals(TEST_KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
        assertEquals(TEST_KEY_ID_TOKEN, tokenResponse.idToken)
        assertEquals(TEST_KEY_KEY_EXPIRES_AT, tokenResponse.accessTokenExpirationTime)

        assertThat(tokenResponse.scope).isNullOrEmpty()
        assertNull(tokenResponse.scopeSet)
    }

    @Test
    @Throws(JSONException::class)
    fun testBuilder_fromResponseJsonStringWithoutScopeField() {
        val tokenResponse =
            minimalBuilder.fromResponseJsonString(TEST_JSON_WITHOUT_SCOPE_FIELD).build()

        assertNotNull(tokenResponse)

        assertEquals(TEST_KEY_ACCESS_TOKEN, tokenResponse.accessToken)
        assertEquals(TEST_KEY_TOKEN_TYPE, tokenResponse.tokenType)
        assertEquals(TEST_KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
        assertEquals(TEST_KEY_ID_TOKEN, tokenResponse.idToken)
        assertEquals(TEST_KEY_KEY_EXPIRES_AT, tokenResponse.accessTokenExpirationTime)

        assertThat(tokenResponse.scope).isNullOrEmpty()
        assertNull(tokenResponse.scopeSet)
    }

    @Test
    @Throws(JSONException::class)
    fun testJsonSerialization() {
        val response = minimalBuilder
            .setAccessToken(TEST_KEY_ACCESS_TOKEN)
            .setAccessTokenExpirationTime(TEST_KEY_KEY_EXPIRES_AT)
            .setIdToken(TEST_ID_TOKEN)
            .setRefreshToken(TEST_REFRESH_TOKEN)
            .setScope(TEST_SCOPE)
            .setTokenType(TEST_KEY_TOKEN_TYPE)
            .build()

        val output = response.jsonSerializeString()
        val input = jsonDeserialize(output)

        assertThat(input.accessToken).isEqualTo(response.accessToken)
        assertThat(input.accessTokenExpirationTime)
            .isEqualTo(response.accessTokenExpirationTime)

        assertThat(input.idToken).isEqualTo(response.idToken)
        assertThat(input.refreshToken).isEqualTo(response.refreshToken)
        assertThat(input.scope).isEqualTo(response.scope)
        assertThat(input.tokenType).isEqualTo(response.tokenType)
    }

    @Test
    @Throws(Exception::class)
    fun testJsonSerialization_doesNotChange() {
        val tokenResponse = minimalBuilder.fromResponseJsonString(TEST_JSON_WITH_SCOPE).build()

        val firstOutput = tokenResponse.jsonSerializeString()
        val secondOutput = jsonDeserialize(firstOutput).jsonSerializeString()

        assertThat(secondOutput).isEqualTo(firstOutput)
    }

    @Suppress("unused")
    companion object {
        private const val TEST_KEY_TOKEN_TYPE = "Bearer"
        private const val TEST_KEY_ACCESS_TOKEN = "pAstudrU6axaw#Da355eseTu6ugufrev"
        private const val TEST_KEY_KEY_EXPIRES_AT = 1481304561609L
        private const val TEST_KEY_REFRESH_TOKEN = "T#xapeva#Rux3steh3fazuvak4seN#S?"
        private const val TEST_KEY_ID_TOKEN = $$"5-=5eW5eGe3wE7A$WA+waph7S#FRedat"
        private const val TEST_KEY_SCOPE_1 = "Scope01"
        private const val TEST_KEY_SCOPE_2 = "Scope02"
        private const val TEST_KEY_SCOPE_3 = "Scope03"
        private const val TEST_KEY_SCOPES: String =
            "$TEST_KEY_SCOPE_1 $TEST_KEY_SCOPE_2 $TEST_KEY_SCOPE_3"

        private const val TEST_JSON_WITH_SCOPE = "{\n" +
                "    \"" + TokenResponse.KEY_ACCESS_TOKEN + "\": \"" + TEST_KEY_ACCESS_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_TOKEN_TYPE + "\": \"" + TEST_KEY_TOKEN_TYPE + "\",\n" +
                "    \"" + TokenResponse.KEY_REFRESH_TOKEN + "\": \"" + TEST_KEY_REFRESH_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_ID_TOKEN + "\": \"" + TEST_KEY_ID_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_EXPIRES_AT + "\": " + TEST_KEY_KEY_EXPIRES_AT + ",\n" +
                "    \"" + TokenResponse.KEY_SCOPE + "\": \"" + TEST_KEY_SCOPES + "\"\n" +
                "}"

        private const val TEST_JSON_WITHOUT_SCOPE = "{\n" +
                "    \"" + TokenResponse.KEY_ACCESS_TOKEN + "\": \"" + TEST_KEY_ACCESS_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_TOKEN_TYPE + "\": \"" + TEST_KEY_TOKEN_TYPE + "\",\n" +
                "    \"" + TokenResponse.KEY_REFRESH_TOKEN + "\": \"" + TEST_KEY_REFRESH_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_ID_TOKEN + "\": \"" + TEST_KEY_ID_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_EXPIRES_AT + "\": " + TEST_KEY_KEY_EXPIRES_AT + ",\n" +
                "    \"" + TokenResponse.KEY_SCOPE + "\":\"\"\n" +
                "}"
        private const val TEST_JSON_WITHOUT_SCOPE_FIELD = "{\n" +
                "    \"" + TokenResponse.KEY_ACCESS_TOKEN + "\": \"" + TEST_KEY_ACCESS_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_TOKEN_TYPE + "\": \"" + TEST_KEY_TOKEN_TYPE + "\",\n" +
                "    \"" + TokenResponse.KEY_REFRESH_TOKEN + "\": \"" + TEST_KEY_REFRESH_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_ID_TOKEN + "\": \"" + TEST_KEY_ID_TOKEN + "\",\n" +
                "    \"" + TokenResponse.KEY_EXPIRES_AT + "\": " + TEST_KEY_KEY_EXPIRES_AT + "\n" +
                "}"
    }
}
