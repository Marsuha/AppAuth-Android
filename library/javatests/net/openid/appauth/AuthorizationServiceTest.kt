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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE
import androidx.browser.customtabs.CustomTabsIntent.EXTRA_TOOLBAR_COLOR
import androidx.browser.customtabs.CustomTabsIntent.SHOW_PAGE_TITLE
import androidx.browser.customtabs.CustomTabsServiceConnection
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthorizationException.Companion.TYPE_OAUTH_TOKEN_ERROR
import net.openid.appauth.AuthorizationManagementActivity.Companion.KEY_AUTH_INTENT
import net.openid.appauth.AuthorizationManagementActivity.Companion.KEY_CANCEL_INTENT
import net.openid.appauth.AuthorizationManagementActivity.Companion.KEY_COMPLETE_INTENT
import net.openid.appauth.TestValues.TEST_CLIENT_ID
import net.openid.appauth.TestValues.TEST_CLIENT_SECRET
import net.openid.appauth.browser.BrowserDescriptor
import net.openid.appauth.browser.Browsers.Chrome.customTab
import net.openid.appauth.browser.CustomTabManager
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.internal.formUrlDecodeUnique
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_BAD_REQUEST

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class AuthorizationServiceTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private lateinit var service: AuthorizationService
    private lateinit var outputStream: OutputStream
    private lateinit var browserDescriptor: BrowserDescriptor

    @Mock
    lateinit var connectionProvider: ConnectionBuilder

    @Mock
    lateinit var httpConnection: HttpURLConnection

    @Mock
    lateinit var pendingIntent: PendingIntent

    @Mock
    lateinit var context: Context

    /*@Mock
    lateinit var client: CustomTabsClient*/

    @Mock
    lateinit var customTabManager: CustomTabManager

    @Before
    fun setUp() = runTest {
        browserDescriptor = customTab("46")

        service = AuthorizationService(
            context,
            appAuthConfiguration { connectionBuilder = connectionProvider },
            browserDescriptor,
            customTabManager
        )

        outputStream = ByteArrayOutputStream()

        whenever(connectionProvider.openConnection(any())) doReturn httpConnection
        whenever(httpConnection.getOutputStream()) doReturn outputStream

        whenever(
            context.bindService(
                serviceIntentEq(),
                any<CustomTabsServiceConnection>(),
                any<Int>()
            )
        ) doReturn true

        whenever(customTabManager.createTabBuilder()) doReturn CustomTabsIntent.Builder()
    }

    @Test
    fun testAuthorizationRequest_withSpecifiedState() = runTest {
        val request = TestValues.testAuthRequestBuilder
            .setState(TestValues.TEST_STATE)
            .build()

        service.performAuthorizationRequest(request, pendingIntent)
        val intent = captureAuthRequestIntent()
        assertRequestIntent(intent)
        assertEquals(request.toUri().toString(), intent.data.toString())
    }

    @Test
    fun testEndSessionRequest_withSpecifiedState() = runTest {
        val request = TestValues.testEndSessionRequestBuilder
            .setState(TestValues.TEST_STATE)
            .build()

        service.performEndSessionRequest(request, pendingIntent)
        val intent = captureAuthRequestIntent()
        assertRequestIntent(intent)
        assertEquals(request.toUri().toString(), intent.data.toString())
    }

    @Test
    fun testAuthorizationRequest_withSpecifiedNonce() = runTest {
        val request = TestValues.testAuthRequestBuilder
            .setNonce(TestValues.TEST_NONCE)
            .build()

        service.performAuthorizationRequest(request, pendingIntent)
        val intent = captureAuthRequestIntent()
        assertRequestIntent(intent)
        assertEquals(request.toUri().toString(), intent.data.toString())
    }

    @Test
    fun testAuthorizationRequest_withDefaultRandomStateAndNonce() = runTest {
        val request = TestValues.testAuthRequestBuilder.build()
        service.performAuthorizationRequest(request, pendingIntent)
        val intent = captureAuthRequestIntent()
        assertRequestIntent(intent)
    }

    @Test
    fun testAuthorizationRequest_customization() = runTest {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(Color.GREEN)
            .build()

        service.performAuthorizationRequest(
            request = TestValues.testAuthRequestBuilder.build(),
            completedIntent = pendingIntent,
            customTabsIntent = customTabsIntent
        )

        val intent = captureAuthRequestIntent()
        assertColorMatch(intent, Color.GREEN)
    }

    @Test
    fun testEndSessionRequest_customization() = runTest {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(Color.GREEN)
            .build()

        service.performEndSessionRequest(
            request = TestValues.testEndSessionRequest,
            completedIntent = pendingIntent,
            customTabsIntent = customTabsIntent
        )

        val intent = captureAuthRequestIntent()
        assertColorMatch(intent, Color.GREEN)
    }

    @Test(expected = IllegalStateException::class)
    fun testAuthorizationRequest_afterDispose() = runTest {
        service.dispose()

        service.performAuthorizationRequest(
            request = TestValues.testAuthRequestBuilder.build(),
            completedIntent = pendingIntent
        )
    }

    @Test(expected = IllegalStateException::class)
    fun testEndSessionRequest_afterDispose() = runTest {
        service.dispose()
        service.performEndSessionRequest(TestValues.testEndSessionRequest, pendingIntent)
    }

    @Test
    fun testGetAuthorizationRequestIntent_preservesRequest() = runTest {
        val request = TestValues.testAuthRequestBuilder.build()
        val intent = service.getAuthorizationRequestIntent(request)

        assertThat(intent.hasExtra(KEY_AUTH_INTENT)).isTrue()

        assertThat(intent.getStringExtra(AuthorizationManagementActivity.KEY_AUTH_REQUEST))
            .isEqualTo(request.asJsonString)
    }

    @Test
    fun testGetAuthorizationRequestIntent_doesNotInitPendingIntents() = runTest {
        val request = TestValues.testAuthRequestBuilder.build()
        val intent = service.getAuthorizationRequestIntent(request)
        val actualAuthIntent = intent.getParcelableExtra<Intent>(KEY_AUTH_INTENT)

        assertThat(actualAuthIntent?.getParcelableExtra<Intent>(KEY_COMPLETE_INTENT)).isNull()
        assertThat(actualAuthIntent?.getParcelableExtra<Intent>(KEY_CANCEL_INTENT)).isNull()
    }

    @Test
    fun testGetAuthorizationRequestIntent_withCustomTabs_preservesTabSettings() = runTest {
        val request = TestValues.testAuthRequestBuilder.build()
        @ColorInt val toolbarColor = Color.GREEN

        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(toolbarColor)
            .setShowTitle(true)
            .build()

        val intent = service.getAuthorizationRequestIntent(request, customTabsIntent)
        val actualAuthIntent = intent.getParcelableExtra<Intent>(KEY_AUTH_INTENT)

        assertThat(actualAuthIntent?.getIntExtra(EXTRA_TOOLBAR_COLOR, 0))
            .isEqualTo(toolbarColor)

        assertThat(actualAuthIntent?.getIntExtra(EXTRA_TITLE_VISIBILITY_STATE, 0))
            .isEqualTo(SHOW_PAGE_TITLE)
    }

    @Test
    fun testTokenRequest() = runTest {
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())
        whenever(httpConnection.inputStream) doReturn `is`
        whenever(httpConnection.getRequestProperty("Accept")) doReturn null
        whenever(httpConnection.responseCode) doReturn HttpURLConnection.HTTP_OK

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request)

        assertTokenResponse(result.getOrNull(), request)

        val postBody = outputStream.toString()

        // by default, we set application/json as an acceptable response type if a value was not
        // already set
        verify(httpConnection).setRequestProperty("Accept", "application/json")

        val params = postBody.formUrlDecodeUnique()

        request.requestParameters.forEach {
            assertThat(params).containsEntry(it.key, it.value)
        }

        assertThat(params).containsEntry(TokenRequest.PARAM_CLIENT_ID, request.clientId)
    }

    @Test
    fun testTokenRequest_withNonceValidation() = runTest {
        val idToken = TestValues.getTestIdTokenWithNonce(TestValues.TEST_NONCE)

        val `is`: InputStream = ByteArrayInputStream(
            getAuthCodeExchangeResponseJson(idToken).toByteArray()
        )

        Mockito.`when`(httpConnection.inputStream).thenReturn(`is`)
        Mockito.`when`(httpConnection.getRequestProperty("Accept")).thenReturn(null)

        Mockito.`when`(httpConnection.responseCode)
            .thenReturn(HttpURLConnection.HTTP_OK)

        val request = TestValues.testAuthCodeExchangeRequestBuilder
            .setNonce(TestValues.TEST_NONCE)
            .build()

        val result = performTokenRequest(request)

        assertTokenResponse(result.getOrNull(), request, idToken)
    }

    @Test
    fun testTokenRequest_clientSecretBasicAuth() = runTest {
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())

        whenever(httpConnection.inputStream) doReturn `is`
        whenever(httpConnection.getRequestProperty("Accept")) doReturn null
        whenever(httpConnection.responseCode) doReturn HttpURLConnection.HTTP_OK

        val request = TestValues.testAuthCodeExchangeRequest
        val clientAuth = ClientSecretBasic("SUPER_SECRET")
        val result = performTokenRequest(request, clientAuth)

        assertTokenResponse(result.getOrNull(), request)

        val postBody = outputStream.toString()

        // client secret basic does not send the client ID in the body - explicitly check for
        // this as a possible regression, as this can break integration with IDPs if present.
        val params = postBody.formUrlDecodeUnique()
        assertThat(params).doesNotContainKey(TokenRequest.PARAM_CLIENT_ID)
    }

    @Test
    fun testTokenRequest_leaveExistingAcceptUntouched() = runTest {
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())

        // emulate some content types having already been set as an Accept value
        whenever(httpConnection.getRequestProperty("Accept")) doReturn "text/plain"
        whenever(httpConnection.inputStream) doReturn `is`
        whenever(httpConnection.responseCode) doReturn HttpURLConnection.HTTP_OK

        val request = TestValues.testAuthCodeExchangeRequest
        performTokenRequest(request)

        // application/json should be added after the existing string
        verify(httpConnection, never())
            .setRequestProperty(eq("Accept"), any<String>())
    }

    @Test
    fun testTokenRequest_withBasicAuth() = runTest {
        val csb = ClientSecretBasic(TEST_CLIENT_SECRET)
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())

        whenever(httpConnection.responseCode).thenReturn(HttpURLConnection.HTTP_OK)
        whenever(httpConnection.inputStream).thenReturn(`is`)

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csb)

        assertTokenResponse(result.getOrNull(), request)

        val postBody = outputStream.toString()
        assertTokenRequestBody(postBody, request.requestParameters)

        verify(httpConnection).setRequestProperty(
            "Authorization",
            csb.getRequestHeaders(TEST_CLIENT_ID)["Authorization"]
        )
    }

    @Test
    fun testTokenRequest_withPostAuth() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())

        whenever(httpConnection.getInputStream()) doReturn `is`
        whenever(httpConnection.getResponseCode()) doReturn HttpURLConnection.HTTP_OK

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertTokenResponse(result.getOrNull(), request)

        val postBody = outputStream.toString()
        val expectedRequestBody =
            request.requestParameters + csp.getRequestParameters(TEST_CLIENT_ID)

        assertTokenRequestBody(postBody, expectedRequestBody)
    }

    @Test
    fun testTokenRequest_withInvalidGrant() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)
        val `is`: InputStream = ByteArrayInputStream(INVALID_GRANT_RESPONSE_JSON.toByteArray())

        whenever(httpConnection.errorStream) doReturn `is`
        whenever(httpConnection.responseCode) doReturn HTTP_BAD_REQUEST

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertInvalidGrant(result.exceptionOrNull() as? AuthorizationException)
    }

    @Test
    fun testTokenRequest_withInvalidGrant2() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)
        val `is`: InputStream = ByteArrayInputStream(INVALID_GRANT_RESPONSE_JSON.toByteArray())
        whenever(httpConnection.errorStream) doReturn `is`
        whenever(httpConnection.responseCode) doReturn 199

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertInvalidGrant(result.exceptionOrNull() as? AuthorizationException)
    }

    @Test
    fun testTokenRequest_withInvalidGrantWithNoDesc() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)

        val `is`: InputStream =
            ByteArrayInputStream(INVALID_GRANT_NO_DESC_RESPONSE_JSON.toByteArray())

        whenever(httpConnection.errorStream) doReturn `is`
        whenever(httpConnection.responseCode) doReturn HTTP_BAD_REQUEST

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertInvalidGrantWithNoDescription(result.exceptionOrNull() as? AuthorizationException)
    }

    @Test
    fun testTokenRequest_IoException() = runTest {
        val ex: Exception = IOException()
        whenever(httpConnection.getInputStream()) doThrow ex
        whenever(httpConnection.responseCode) doReturn HttpURLConnection.HTTP_OK

        val result = performTokenRequest(TestValues.testAuthCodeExchangeRequest)

        assertNotNull(result.exceptionOrNull())
        assertEquals(
            AuthorizationException.GeneralErrors.NETWORK_ERROR,
            result.exceptionOrNull()
        )
    }

    @Test
    fun testRegistrationRequest() = runTest {
        val `is`: InputStream = ByteArrayInputStream(REGISTRATION_RESPONSE_JSON.toByteArray())
        whenever(httpConnection.inputStream) doReturn `is`
        val request = TestValues.testRegistrationRequest
        val result = performRegistrationRequest(request)

        assertRegistrationResponse(result.getOrNull(), request)
        val postBody = outputStream.toString()
        assertThat(postBody).isEqualTo(request.asRequestJsonString)
    }

    @Test
    fun testRegistrationRequest_IoException() = runTest {
        val ex: Exception = IOException()
        whenever(httpConnection.inputStream) doThrow ex

        val result = performRegistrationRequest(TestValues.testRegistrationRequest)

        assertNotNull(result.exceptionOrNull())
        assertEquals(
            AuthorizationException.GeneralErrors.NETWORK_ERROR,
            result.exceptionOrNull()
        )
    }

    @Test(expected = IllegalStateException::class)
    fun testTokenRequest_afterDispose() = runTest {
        service.dispose()
        performTokenRequest(TestValues.testAuthCodeExchangeRequest)
    }

    @Test(expected = IllegalStateException::class)
    fun testCreateCustomTabsIntentBuilder_afterDispose() = runTest {
        service.dispose()
        service.createCustomTabsIntentBuilder()
    }

    @Test
    fun testGetBrowserDescriptor_browserAvailable() {
        assertEquals(service.browserDescriptor, browserDescriptor)
    }

    private fun captureAuthRequestIntent(): Intent {
        val intentCaptor = argumentCaptor<Intent>()
        verify(context).startActivity(intentCaptor.capture())

        // the real auth intent is wrapped in the intent by AuthorizationManagementActivity
        return intentCaptor.firstValue
            .getParcelableExtra(KEY_AUTH_INTENT)!!
    }

    private fun assertTokenResponse(
        response: TokenResponse?,
        expectedRequest: TokenRequest,
        idToken: String = TestValues.TEST_ID_TOKEN
    ) {
        assertNotNull(response)
        assertEquals(expectedRequest, response!!.request)
        assertEquals(TestValues.TEST_ACCESS_TOKEN, response.accessToken)
        assertEquals(TestValues.TEST_REFRESH_TOKEN, response.refreshToken)
        assertEquals(AuthorizationResponse.TOKEN_TYPE_BEARER, response.tokenType)
        assertEquals(idToken, response.idToken)
    }

    private fun assertInvalidGrant(error: AuthorizationException?) {
        assertNotNull(error)
        assertEquals(TYPE_OAUTH_TOKEN_ERROR.toLong(), error!!.type.toLong())
        assertEquals(TEST_INVALID_GRANT_CODE.toLong(), error.code.toLong())
        assertEquals("invalid_grant", error.error)
        assertEquals("invalid_grant description", error.errorDescription)
    }

    private fun assertInvalidGrantWithNoDescription(error: AuthorizationException?) {
        assertNotNull(error)
        assertEquals(TYPE_OAUTH_TOKEN_ERROR.toLong(), error!!.type.toLong())
        assertEquals(TEST_INVALID_GRANT_CODE.toLong(), error.code.toLong())
        assertEquals("invalid_grant", error.error)
        Assert.assertNull(error.errorDescription)
    }

    private fun assertRegistrationResponse(
        response: RegistrationResponse?,
        expectedRequest: RegistrationRequest
    ) {
        assertThat(response).isNotNull()
        assertThat(response!!.request).isEqualTo(expectedRequest)
        assertThat(response.clientId).isEqualTo(TEST_CLIENT_ID)
        assertThat(response.clientSecret).isEqualTo(TEST_CLIENT_SECRET)
        assertThat(response.clientSecretExpiresAt).isEqualTo(TestValues.TEST_CLIENT_SECRET_EXPIRES_AT)
    }

    private fun assertTokenRequestBody(
        requestBody: String,
        expectedParameters: Map<String, String>
    ) {
        val postBody = Uri.Builder().encodedQuery(requestBody).build()
        expectedParameters.forEach {
            assertThat(postBody.getQueryParameter(it.key)).isEqualTo(it.value)
        }
    }

    private suspend fun performTokenRequest(
        request: TokenRequest,
        clientAuthentication: ClientAuthentication = NoClientAuthentication
    ): Result<TokenResponse> {
        val result = try {
            Result.success(service.performTokenRequest(request, clientAuthentication))
        } catch (ex: AuthorizationException) {
            Result.failure(ex)
        }

        assertTrue((result.getOrNull() == null) xor (result.exceptionOrNull() == null))
        return result
    }

    private suspend fun performRegistrationRequest(
        request: RegistrationRequest
    ): Result<RegistrationResponse> {
        val result = try {
            Result.success(service.performRegistrationRequest(request))
        } catch (ex: AuthorizationException) {
            Result.failure(ex)
        }

        assertTrue((result.getOrNull() == null) xor (result.exceptionOrNull() == null))
        return result
    }

    private fun assertRequestIntent(intent: Intent, color: Int? = null) {
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertColorMatch(intent, color)
    }

    private fun assertColorMatch(intent: Intent, expected: Int?) {
        val color = intent.getIntExtra(EXTRA_TOOLBAR_COLOR, Color.TRANSPARENT)
        assertTrue((expected == null) || ((expected == color) && (color != Color.TRANSPARENT)))
    }

    val authCodeExchangeResponseJson: String
        get() = getAuthCodeExchangeResponseJson()

    fun getAuthCodeExchangeResponseJson(idToken: String = TestValues.TEST_ID_TOKEN) = """
            {
                "refresh_token": "${TestValues.TEST_REFRESH_TOKEN}",
                "access_token": "${TestValues.TEST_ACCESS_TOKEN}",
                "expires_in": $TEST_EXPIRES_IN,
                "id_token": "$idToken",
                "token_type": "${AuthorizationResponse.TOKEN_TYPE_BEARER}"
            }
        """.trimIndent()

    companion object {
        private const val TEST_EXPIRES_IN = 3600
        private const val TEST_BROWSER_PACKAGE = "com.browser.test"

        private val REGISTRATION_RESPONSE_JSON = """
            {
                "client_id": "$TEST_CLIENT_ID",
                "client_secret": "$TEST_CLIENT_SECRET",
                "client_secret_expires_at": ${TestValues.TEST_CLIENT_SECRET_EXPIRES_AT},
                "application_type": "${RegistrationRequest.APPLICATION_TYPE_NATIVE}"
            }
        """.trimIndent()

        private val INVALID_GRANT_RESPONSE_JSON = """
            {
                "error": "invalid_grant",
                "error_description": "invalid_grant description"
            }
        """.trimIndent()

        private val INVALID_GRANT_NO_DESC_RESPONSE_JSON = """
            {
                "error": "invalid_grant"
            }
        """.trimIndent()

        private const val TEST_INVALID_GRANT_CODE = 2002

        fun serviceIntentEq(): Intent = argThat<Intent> { TEST_BROWSER_PACKAGE == `package` }
    }
}
