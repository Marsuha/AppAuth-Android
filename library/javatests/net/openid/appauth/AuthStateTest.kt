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

import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState.Companion.jsonDeserialize
import net.openid.appauth.AuthState.FreshTokenResult
import net.openid.appauth.ClientAuthentication.UnsupportedAuthenticationMethod
import net.openid.appauth.TestValues.TEST_ACCESS_TOKEN
import net.openid.appauth.TestValues.TEST_AUTH_CODE
import net.openid.appauth.TestValues.TEST_CLIENT_SECRET
import net.openid.appauth.TestValues.TEST_ID_TOKEN
import net.openid.appauth.TestValues.TEST_REFRESH_TOKEN
import net.openid.appauth.TestValues.getMinimalAuthRequestBuilder
import net.openid.appauth.TestValues.testAuthCodeExchangeResponse
import net.openid.appauth.TestValues.testAuthCodeExchangeResponseBuilder
import net.openid.appauth.TestValues.testAuthRequest
import net.openid.appauth.TestValues.testAuthResponse
import net.openid.appauth.TestValues.testAuthResponseBuilder
import net.openid.appauth.TestValues.testRegistrationResponse
import net.openid.appauth.TestValues.testRegistrationResponseBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthStateTest {
    private lateinit var clock: TestClock

    @Before
    fun setUp() {
        clock = TestClock(0L)
    }

    @Test
    fun testInitialState() {
        val state = AuthState()
        assertThat(state.isAuthorized).isFalse()
        assertThat(state.accessToken).isNull()
        assertThat(state.accessTokenExpirationTime).isNull()
        assertThat(state.idToken).isNull()
        assertThat(state.refreshToken).isNull()
        assertThat(state.lastAuthorizationResponse).isNull()
        assertThat(state.lastTokenResponse).isNull()
        assertThat(state.lastRegistrationResponse).isNull()
        assertThat(state.scope).isNull()
        assertThat(state.scopeValues).isNull()
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()
    }

    @Test
    fun testInitialState_fromAuthorizationResponse() {
        val authCodeRequest = testAuthRequest
        val resp = testAuthResponse
        val state = AuthState(resp, null)

        assertThat(state.isAuthorized).isFalse()
        assertThat(state.accessToken).isNull()
        assertThat(state.accessTokenExpirationTime).isNull()
        assertThat(state.idToken).isNull()
        assertThat(state.refreshToken).isNull()

        assertThat<AuthorizationException?>(state.authorizationException).isNull()
        assertThat<AuthorizationResponse?>(state.lastAuthorizationResponse)
            .isSameAs(resp)
        assertThat<TokenResponse?>(state.lastTokenResponse).isNull()

        assertThat(state.scope).isEqualTo(authCodeRequest.scope)
        assertThat(state.scopeValues).isEqualTo(authCodeRequest.scopeValues)

        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()
    }

    @Test
    fun testInitialState_fromAuthorizationException() {
        val state = AuthState(
            null,
            AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
        )

        assertThat(state.isAuthorized).isFalse()
        assertThat(state.accessToken).isNull()
        assertThat(state.accessTokenExpirationTime).isNull()
        assertThat(state.idToken).isNull()
        assertThat(state.refreshToken).isNull()

        assertThat<AuthorizationException?>(state.authorizationException).isEqualTo(
            AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
        )
        assertThat<AuthorizationResponse?>(state.lastAuthorizationResponse).isNull()
        assertThat<TokenResponse?>(state.lastTokenResponse).isNull()

        assertThat(state.scope).isNull()
        assertThat(state.scopeValues).isNull()
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()
    }

    @Test
    fun testInitialState_fromAuthorizationResponse_withModifiedScope() {
        // simulate a situation in which the response grants a subset of the requested scopes,
        // perhaps due to policy or user preference
        val resp = testAuthResponseBuilder
            .setScopes(AuthorizationRequest.Scope.OPENID)
            .build()
        val state = AuthState(resp, null)

        assertThat(state.scope).isEqualTo(resp.scope)
        assertThat(state.scopeValues).isEqualTo(resp.scopeSet)
    }

    @Test
    fun testInitialState_fromAuthorizationResponseAndTokenResponse() {
        val authResp = testAuthResponse
        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        assertThat(state.accessToken).isNull()
        assertThat(state.accessTokenExpirationTime).isNull()
        assertThat(state.idToken).isNull()
        assertThat(state.refreshToken).isEqualTo(TEST_REFRESH_TOKEN)

        assertThat<AuthorizationException?>(state.authorizationException).isNull()
        assertThat<AuthorizationResponse?>(state.lastAuthorizationResponse)
            .isSameAs(authResp)
        assertThat<TokenResponse?>(state.lastTokenResponse).isSameAs(tokenResp)

        assertThat(state.scope).isEqualTo(authResp.request.scope)
        assertThat(state.scopeValues).isEqualTo(authResp.request.scopeValues)

        // no access token or ID token have yet been retrieved
        assertThat(state.isAuthorized).isFalse()

        // the refresh token has been acquired, but has not yet been used to fetch an access token
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()
    }

    @Test
    fun testInitialState_fromRegistrationResponse() {
        val regResp = testRegistrationResponse
        val state = AuthState(regResp)

        assertThat(state.isAuthorized).isFalse()
        assertThat(state.accessToken).isNull()
        assertThat(state.accessTokenExpirationTime).isNull()
        assertThat(state.idToken).isNull()
        assertThat(state.refreshToken).isNull()

        assertThat<AuthorizationException?>(state.authorizationException).isNull()
        assertThat<AuthorizationResponse?>(state.lastAuthorizationResponse).isNull()
        assertThat<TokenResponse?>(state.lastTokenResponse).isNull()
        assertThat<RegistrationResponse?>(state.lastRegistrationResponse)
            .isSameAs(regResp)

        assertThat(state.scope).isNull()
        assertThat(state.scopeValues).isNull()
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testConstructor_withAuthResponseAndException() {
        AuthState(
            testAuthResponse,
            AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
        )
    }

    @Test
    fun testUpdate_authResponseWithException_authErrorType() {
        val state = AuthState()
        state.update(
            null as AuthorizationResponse?,
            AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
        )

        assertThat<AuthorizationException?>(state.authorizationException)
            .isSameAs(AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED)
    }

    @Test
    fun testUpdate_authResponseWithException_ignoredErrorType() {
        val state = AuthState()
        state.update(
            null as AuthorizationResponse?,
            AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW
        )

        assertThat<AuthorizationException?>(state.authorizationException).isNull()
    }

    @Test
    fun testUpdate_tokenResponseWithException_tokenErrorType() {
        val state = AuthState()
        state.update(
            null as TokenResponse?,
            AuthorizationException.TokenRequestErrors.UNAUTHORIZED_CLIENT
        )

        assertThat<AuthorizationException?>(state.authorizationException)
            .isSameAs(AuthorizationException.TokenRequestErrors.UNAUTHORIZED_CLIENT)
    }

    @Test
    fun testUpdate_tokenResponseWithException_ignoredErrorType() {
        val state = AuthState()
        state.update(
            null as TokenResponse?,
            AuthorizationException.GeneralErrors.NETWORK_ERROR
        )

        assertThat<AuthorizationException?>(state.authorizationException).isNull()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUpdate_withAuthResponseAndException() {
        val state = AuthState()
        state.update(
            testAuthResponse,
            AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUpdate_withTokenResponseAndException() {
        val state = AuthState(testAuthResponse, null)
        state.update(
            testAuthCodeExchangeResponse,
            AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED
        )
    }

    @Test
    fun testGetAccessToken_fromAuthResponse() {
        val authReq = getMinimalAuthRequestBuilder("code token")
            .setScope(AuthorizationRequest.Scope.EMAIL)
            .build()
        val authResp = AuthorizationResponse.Builder(authReq)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setState(authReq.state)
            .build()

        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        // in this scenario, we have an access token in the authorization response but not
        // in the token response. We expect the access token from the authorization response
        // to be returned.
        assertThat(state.accessToken).isEqualTo(authResp.accessToken)
    }

    @Test
    fun testGetAccessToken_fromTokenResponse() {
        val authReq = getMinimalAuthRequestBuilder("code token")
            .setScope(AuthorizationRequest.Scope.EMAIL)
            .build()
        val authResp = AuthorizationResponse.Builder(authReq)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setAccessToken("older_token")
            .setState(authReq.state)
            .build()
        val tokenResp = testAuthCodeExchangeResponseBuilder
            .setAccessToken("newer_token")
            .build()
        val state = AuthState(authResp, tokenResp, null)

        // in this scenario, we have an access token on both the authorization response and the
        // token response. The value on the token response takes precedence.
        assertThat(state.accessToken).isEqualTo(tokenResp.accessToken)
    }

    @Test
    fun testGetIdToken_fromAuthResponse() {
        val authReq = getMinimalAuthRequestBuilder("code id_token")
            .setScope(AuthorizationRequest.Scope.EMAIL)
            .build()
        val authResp = AuthorizationResponse.Builder(authReq)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setIdToken(TEST_ID_TOKEN)
            .setState(authReq.state)
            .build()

        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        // in this scenario, we have an ID token in the authorization response but not
        // in the token response. We expect the ID token from the authorization response
        // to be returned.
        assertThat(state.idToken).isEqualTo(authResp.idToken)
    }

    @Test
    fun testGetIdToken_fromTokenResponse() {
        val authReq = getMinimalAuthRequestBuilder("code id_token")
            .setScope(AuthorizationRequest.Scope.EMAIL)
            .build()
        val authResp = AuthorizationResponse.Builder(authReq)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setIdToken("older.token.value")
            .setState(authReq.state)
            .build()
        val tokenResp = testAuthCodeExchangeResponseBuilder
            .setIdToken("newer.token.value")
            .build()
        val state = AuthState(authResp, tokenResp, null)

        // in this scenario, we have an ID token on both the authorization response and the
        // token response. The value on the token response takes precedence.
        assertThat(state.idToken).isEqualTo(tokenResp.idToken)
    }

    @Test
    fun testCreateTokenRefreshRequest() {
        val authResp = testAuthResponse
        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        val request = state.createTokenRefreshRequest()

        assertThat(request.configuration.tokenEndpoint)
            .isEqualTo(state.authorizationServiceConfiguration?.tokenEndpoint)

        assertThat(request.clientId).isEqualTo(authResp.request.clientId)
        assertThat(request.grantType).isEqualTo(GrantTypeValues.REFRESH_TOKEN)
        assertThat(request.refreshToken).isEqualTo(state.refreshToken)
    }

    @Test
    fun testGetNeedsTokenRefresh() {
        val authReq = getMinimalAuthRequestBuilder("token code")
            .setScope("my_scope")
            .build()

        val authResp = AuthorizationResponse.Builder(authReq)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setAccessTokenExpirationTime(TWO_MINUTES)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setState(authReq.state)
            .build()

        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        // before the expiration time
        clock.currentTime.set(ONE_SECOND)
        assertThat(state.getNeedsTokenRefresh(clock)).isFalse()

        // 1ms before the tolerance threshold
        clock.currentTime.set(TWO_MINUTES - AuthState.EXPIRY_TIME_TOLERANCE_MS - 1)
        assertThat(state.getNeedsTokenRefresh(clock)).isFalse()

        // on the tolerance threshold
        clock.currentTime.set(TWO_MINUTES - AuthState.EXPIRY_TIME_TOLERANCE_MS)
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()

        // past tolerance threshold
        clock.currentTime.set(TWO_MINUTES - AuthState.EXPIRY_TIME_TOLERANCE_MS + ONE_SECOND)
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()

        // on token's actual expiration
        clock.currentTime.set(TWO_MINUTES)
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()

        // past token's actual expiration
        clock.currentTime.set(TWO_MINUTES + ONE_SECOND)
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()
    }

    @Test
    fun testSetNeedsTokenRefresh() {
        val authReq = getMinimalAuthRequestBuilder("token code")
            .setScope("my_scope")
            .build()

        val authResp = AuthorizationResponse.Builder(authReq)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setAccessTokenExpirationTime(TWO_MINUTES)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setState(authReq.state)
            .build()
        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        // before the expiration time...
        clock.currentTime.set(ONE_SECOND)
        assertThat(state.getNeedsTokenRefresh(clock)).isFalse()

        // ... force a refresh
        state.needsTokenRefresh = true
        assertThat(state.getNeedsTokenRefresh(clock)).isTrue()
    }

    @Test
    fun testPerformActionWithFreshTokens() = runTest {
        val authReq = getMinimalAuthRequestBuilder("id_token token code")
            .setScope("my_scope")
            .build()

        val authResp = AuthorizationResponse.Builder(authReq)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setAccessTokenExpirationTime(TWO_MINUTES)
            .setIdToken(TEST_ID_TOKEN)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setState(authReq.state)
            .build()

        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        val service = mock<AuthorizationService>()

        // at this point in time, the access token will not be considered to be expired
        clock.currentTime.set(ONE_SECOND)

        var capturedAccessToken: String? = null
        var capturedIdToken: String? = null
        var capturedException: AuthorizationException? = null

        state.performActionWithFreshTokens(
            service,
            NoClientAuthentication,
            emptyMap(),
            clock,
        ) { result ->
            when (result) {
                is FreshTokenResult.Failure -> capturedException = result.exception
                is FreshTokenResult.Success -> {
                    capturedAccessToken = result.accessToken
                    capturedIdToken = result.idToken
                }
            }
        }

        // as the token has not expired, the service will not be used to refresh it
        verifyNoInteractions(service)

        assertThat(capturedAccessToken).isEqualTo(TEST_ACCESS_TOKEN)
        assertThat(capturedIdToken).isEqualTo(TEST_ID_TOKEN)
        assertThat(capturedException).isNull()
    }

    @Test
    fun testPerformActionWithFreshTokens_afterTokenExpiration() = runTest {
        val authRequest = getMinimalAuthRequestBuilder("id_token token code")
            .setScope("my_scope")
            .build()

        val authResponse = AuthorizationResponse.Builder(authRequest)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setAccessTokenExpirationTime(TWO_MINUTES)
            .setIdToken(TEST_ID_TOKEN)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setState(authRequest.state)
            .build()

        val tokenResponse = testAuthCodeExchangeResponse

        val state = AuthState(
            authResponse = authResponse,
            tokenResponse = tokenResponse,
            authException = null
        )

        // at this point in time, the access token will be considered to be expired
        clock.currentTime.set(TWO_MINUTES - AuthState.EXPIRY_TIME_TOLERANCE_MS + ONE_SECOND)

        val service = mock<AuthorizationService>()
        val freshAccessToken = "fresh_access_token"
        val freshIdToken = "fresh.id.token"
        val freshExpirationTime: Long = clock.currentTime.get() + TWO_MINUTES

        // simulate the success of a token request with fresh tokens as a result
        val freshResponse = TokenResponse.Builder(state.createTokenRefreshRequest())
            .setTokenType(TokenResponse.TOKEN_TYPE_BEARER)
            .setAccessToken(freshAccessToken)
            .setAccessTokenExpirationTime(freshExpirationTime)
            .setIdToken(freshIdToken)
            .build()

        whenever(
            service.performTokenRequest(
                any(),
                any<ClientAuthentication>()
            )
        ).thenReturn(freshResponse)

        var capturedAccessToken: String? = null
        var capturedIdToken: String? = null
        var capturedException: AuthorizationException? = null

        state.performActionWithFreshTokens(
            service,
            NoClientAuthentication,
            emptyMap(),
            clock,
        ) { result ->
            when (result) {
                is FreshTokenResult.Success -> {
                    capturedAccessToken = result.accessToken
                    capturedIdToken = result.idToken
                }

                is FreshTokenResult.Failure -> capturedException = result.exception
            }
        }

        // as the access token has expired, we expect a token refresh request
        val requestCaptor = argumentCaptor<TokenRequest>()
        verify(service, times(1)).performTokenRequest(
            requestCaptor.capture(),
            any<ClientAuthentication>(),
        )

        assertThat(requestCaptor.firstValue.refreshToken).isEqualTo(tokenResponse.refreshToken)

        assertThat(capturedAccessToken).isEqualTo(freshAccessToken)
        assertThat(capturedIdToken).isEqualTo(freshIdToken)
        assertThat(capturedException).isNull()

        // additionally, the auth state should be updated with the new token values
        assertThat(state.accessToken).isEqualTo(freshAccessToken)
        assertThat(state.accessTokenExpirationTime).isEqualTo(freshExpirationTime)
        assertThat(state.idToken).isEqualTo(freshIdToken)
    }

    @Test
    fun testPerformActionWithFreshToken_afterTokenExpiration_multipleActions() = runTest {
        val authReq = getMinimalAuthRequestBuilder("id_token token code")
            .setScope("my_scope")
            .build()

        val authResp = AuthorizationResponse.Builder(authReq)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setAccessTokenExpirationTime(TWO_MINUTES)
            .setIdToken(TEST_ID_TOKEN)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setState(authReq.state)
            .build()

        val tokenResp = testAuthCodeExchangeResponse
        val state = AuthState(authResp, tokenResp, null)

        // at this point in time, the access token will be considered to be expired
        clock.currentTime.set(TWO_MINUTES - AuthState.EXPIRY_TIME_TOLERANCE_MS + ONE_SECOND)

        val freshRefreshToken = "fresh_refresh_token"
        val freshAccessToken = "fresh_access_token"
        val freshIdToken = "fresh.id.token"
        val freshExpirationTime: Long = clock.currentTime.get() + TWO_MINUTES

        // simulate success on the token request, with fresh tokens in the result
        val freshResponse = TokenResponse.Builder(state.createTokenRefreshRequest())
            .setTokenType(TokenResponse.TOKEN_TYPE_BEARER)
            .setAccessToken(freshAccessToken)
            .setAccessTokenExpirationTime(freshExpirationTime)
            .setIdToken(freshIdToken)
            .setRefreshToken(freshRefreshToken)
            .build()

        val service = mock<AuthorizationService>()

        whenever(
            service.performTokenRequest(
                any(),
                any<ClientAuthentication>()
            )
        ).thenReturn(freshResponse)

        var capturedAccessToken: String? = null
        var capturedIdToken: String? = null
        var capturedException: AuthorizationException? = null

        state.performActionWithFreshTokens(
            service,
            NoClientAuthentication,
            emptyMap(),
            clock,
        ) { result ->
            when (result) {
                is FreshTokenResult.Success -> {
                    capturedAccessToken = result.accessToken
                    capturedIdToken = result.idToken
                }

                is FreshTokenResult.Failure -> capturedException = result.exception
            }
        }

        state.performActionWithFreshTokens(
            service,
            NoClientAuthentication,
            emptyMap(),
            clock,
        ) { result ->
            when (result) {
                is FreshTokenResult.Success -> {
                    capturedAccessToken = result.accessToken
                    capturedIdToken = result.idToken
                }

                is FreshTokenResult.Failure -> capturedException = result.exception
            }
        }

        // as the access token has expired, we expect a token refresh request
        val requestCaptor = argumentCaptor<TokenRequest>()
        verify(service, times(1)).performTokenRequest(
            requestCaptor.capture(),
            any<ClientAuthentication>(),
        )

        assertThat(requestCaptor.firstValue.refreshToken).isEqualTo(tokenResp.refreshToken)
        assertThat(capturedAccessToken).isEqualTo(freshAccessToken)
        assertThat(capturedIdToken).isEqualTo(freshIdToken)
        assertThat(capturedException).isNull()

        // additionally, the auth state should be updated with the new token values
        assertThat(state.refreshToken).isEqualTo(freshRefreshToken)
        assertThat(state.accessToken).isEqualTo(freshAccessToken)
        assertThat(state.accessTokenExpirationTime).isEqualTo(freshExpirationTime)
        assertThat(state.idToken).isEqualTo(freshIdToken)
    }

    @Test
    @Throws(Exception::class)
    fun testJsonSerialization() {
        val authReq = getMinimalAuthRequestBuilder("id_token token code")
            .setScopes(
                AuthorizationRequest.Scope.OPENID,
                AuthorizationRequest.Scope.EMAIL,
                AuthorizationRequest.Scope.PROFILE
            )
            .build()

        val authResp = AuthorizationResponse.Builder(authReq)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setIdToken(TEST_ID_TOKEN)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setState(authReq.state)
            .build()

        val tokenResp = testAuthCodeExchangeResponse
        val regResp = testRegistrationResponse
        val state = AuthState(authResp, tokenResp, null)
        state.update(regResp)

        val json = state.jsonSerializeString()
        val restoredState = jsonDeserialize(json)

        assertThat(restoredState.isAuthorized).isEqualTo(state.isAuthorized)

        assertThat(restoredState.accessToken).isEqualTo(state.accessToken)
        assertThat(restoredState.accessTokenExpirationTime)
            .isEqualTo(state.accessTokenExpirationTime)

        assertThat(restoredState.idToken).isEqualTo(state.idToken)
        assertThat(restoredState.refreshToken).isEqualTo(state.refreshToken)
        assertThat(restoredState.scope).isEqualTo(state.scope)
        assertThat(restoredState.getNeedsTokenRefresh(clock))
            .isEqualTo(state.getNeedsTokenRefresh(clock))

        assertThat(restoredState.clientSecret).isEqualTo(state.clientSecret)
        assertThat(restoredState.hasClientSecretExpired(clock))
            .isEqualTo(state.hasClientSecretExpired(clock))
    }

    @Test
    @Throws(Exception::class)
    fun testJsonSerialization_doesNotChange() {
        val authReq = getMinimalAuthRequestBuilder("id_token token code")
            .setScopes(
                AuthorizationRequest.Scope.OPENID,
                AuthorizationRequest.Scope.EMAIL,
                AuthorizationRequest.Scope.PROFILE
            )
            .build()

        val authResp = AuthorizationResponse.Builder(authReq)
            .setAccessToken(TEST_ACCESS_TOKEN)
            .setIdToken(TEST_ID_TOKEN)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setState(authReq.state)
            .build()

        val tokenResp = testAuthCodeExchangeResponse
        val regResp = testRegistrationResponse
        val state = AuthState(authResp, tokenResp, null)
        state.update(regResp)

        val firstOutput = state.jsonSerializeString()
        val secondOutput = jsonDeserialize(firstOutput).jsonSerializeString()

        assertThat(secondOutput).isEqualTo(firstOutput)
    }

    @Test
    @Throws(Exception::class)
    fun testJsonSerialization_withException() {
        val state = AuthState(
            null,
            AuthorizationException.AuthorizationRequestErrors.INVALID_REQUEST
        )

        val restored = jsonDeserialize(state.jsonSerializeString())
        assertThat<AuthorizationException?>(restored.authorizationException)
            .isEqualTo(state.authorizationException)
    }

    @Test
    fun testHasClientSecretExpired() {
        val regResp = testRegistrationResponseBuilder
            .setClientSecret(TEST_CLIENT_SECRET)
            .setClientSecretExpiresAt(TWO_MINUTES)
            .build()

        val state = AuthState(regResp)

        // before the expiration time
        clock.currentTime.set(ONE_SECOND)
        assertThat(state.hasClientSecretExpired(clock)).isFalse()

        // on client_secret's actual expiration
        clock.currentTime.set(TWO_MINUTES)
        assertThat(state.hasClientSecretExpired(clock)).isTrue()

        // past client_secrets's actual expiration
        clock.currentTime.set(TWO_MINUTES + ONE_SECOND)
        assertThat(state.hasClientSecretExpired(clock)).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun testCreateRequiredClientAuthentication_withoutClientCredentials() {
        val regResp = testRegistrationResponseBuilder.build()
        val state = AuthState(regResp)
        assertThat(state.clientAuthentication)
            .isInstanceOf(NoClientAuthentication::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun testCreateRequiredClientAuthentication_withoutTokenEndpointAuthMethod() {
        val regResp = testRegistrationResponseBuilder
            .setClientSecret(TEST_CLIENT_SECRET)
            .build()
        val state = AuthState(regResp)
        assertThat(state.clientAuthentication)
            .isInstanceOf(ClientSecretBasic::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun testCreateRequiredClientAuthentication_withTokenEndpointAuthMethodNone() {
        val regResp = testRegistrationResponseBuilder
            .setClientSecret(TEST_CLIENT_SECRET)
            .setTokenEndpointAuthMethod("none")
            .build()
        val state = AuthState(regResp)
        assertThat(state.clientAuthentication)
            .isInstanceOf(NoClientAuthentication::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun testCreateRequiredClientAuthentication_withTokenEndpointAuthMethodBasic() {
        val regResp = testRegistrationResponseBuilder
            .setClientSecret(TEST_CLIENT_SECRET)
            .setTokenEndpointAuthMethod(ClientSecretBasic.NAME)
            .build()
        val state = AuthState(regResp)
        assertThat(state.clientAuthentication)
            .isInstanceOf(ClientSecretBasic::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun testCreateRequiredClientAuthentication_withTokenEndpointAuthMethodPost() {
        val regResp = testRegistrationResponseBuilder
            .setClientSecret(TEST_CLIENT_SECRET)
            .setTokenEndpointAuthMethod(ClientSecretPost.NAME)
            .build()
        val state = AuthState(regResp)
        assertThat(state.clientAuthentication)
            .isInstanceOf(ClientSecretPost::class.java)
    }

    @Test(expected = UnsupportedAuthenticationMethod::class)
    @Throws(Exception::class)
    fun testCreateRequiredClientAuthentication_withUnknownTokenEndpointAuthMethod() {
        val regResp = testRegistrationResponseBuilder
            .setClientSecret(TEST_CLIENT_SECRET)
            .setTokenEndpointAuthMethod("unknown")
            .build()
        val state = AuthState(regResp)
        state.clientAuthentication
    }

    companion object {
        private const val ONE_SECOND = 1000L
        private const val TWO_MINUTES = 120000L
    }
}
