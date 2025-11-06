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
import net.openid.appauth.internal.UriUtil.formUrlDecodeUnique
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
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
    @Throws(Exception::class)
    fun setUp() = runTest {
        browserDescriptor = customTab("46")

        service = AuthorizationService(
            context,
            appAuthConfiguration { connectionBuilder = connectionProvider },
            browserDescriptor,
            customTabManager
        )

        outputStream = ByteArrayOutputStream()

        whenever(connectionProvider.openConnection(any<Uri>()))
            .thenReturn(httpConnection)

        whenever(httpConnection.getOutputStream()).thenReturn(outputStream)

        whenever(
            context.bindService(
                serviceIntentEq(),
                any<CustomTabsServiceConnection>(),
                any<Int>()
            )
        ).thenReturn(true)

        whenever(customTabManager.createTabBuilder()).thenReturn(CustomTabsIntent.Builder())
    }

    @Test
    @Throws(Exception::class)
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
    @Throws(Exception::class)
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
    @Throws(Exception::class)
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
    @Throws(Exception::class)
    fun testAuthorizationRequest_withDefaultRandomStateAndNonce() = runTest {
        val request = TestValues.testAuthRequestBuilder.build()
        service.performAuthorizationRequest(request, pendingIntent)
        val intent = captureAuthRequestIntent()
        assertRequestIntent(intent)
    }

    @Test
    @Throws(Exception::class)
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
    @Throws(Exception::class)
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
    @Throws(Exception::class)
    fun testAuthorizationRequest_afterDispose() = runTest {
        service.dispose()

        service.performAuthorizationRequest(
            request = TestValues.testAuthRequestBuilder.build(),
            completedIntent = pendingIntent
        )
    }

    @Test(expected = IllegalStateException::class)
    @Throws(Exception::class)
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
            .isEqualTo(request.jsonSerializeString())
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
    @Throws(Exception::class)
    fun testTokenRequest() = runTest {
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())
        Mockito.`when`(httpConnection.inputStream).thenReturn(`is`)
        Mockito.`when`(httpConnection.getRequestProperty("Accept")).thenReturn(null)
        Mockito.`when`(httpConnection.responseCode).thenReturn(HttpURLConnection.HTTP_OK)

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request)

        assertTokenResponse(result.getOrNull(), request)

        val postBody = outputStream.toString()

        // by default, we set application/json as an acceptable response type if a value was not
        // already set
        Mockito.verify(httpConnection).setRequestProperty("Accept", "application/json")

        val params = formUrlDecodeUnique(postBody)

        request.requestParameters.forEach {
            assertThat(params).containsEntry(it.key, it.value)
        }

        assertThat(params).containsEntry(TokenRequest.PARAM_CLIENT_ID, request.clientId)
    }

    @Test
    @Throws(Exception::class)
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
    @Throws(Exception::class)
    fun testTokenRequest_clientSecretBasicAuth() = runTest {
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())

        Mockito.`when`(httpConnection.inputStream).thenReturn(`is`)
        Mockito.`when`(httpConnection.getRequestProperty("Accept")).thenReturn(null)

        Mockito.`when`(httpConnection.responseCode)
            .thenReturn(HttpURLConnection.HTTP_OK)

        val request = TestValues.testAuthCodeExchangeRequest
        val clientAuth = ClientSecretBasic("SUPER_SECRET")
        val result = performTokenRequest(request, clientAuth)

        assertTokenResponse(result.getOrNull(), request)

        val postBody = outputStream.toString()

        // client secret basic does not send the client ID in the body - explicitly check for
        // this as a possible regression, as this can break integration with IDPs if present.
        val params = formUrlDecodeUnique(postBody)
        assertThat(params).doesNotContainKey(TokenRequest.PARAM_CLIENT_ID)
    }

    @Test
    @Throws(Exception::class)
    fun testTokenRequest_leaveExistingAcceptUntouched() = runTest {
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())

        // emulate some content types having already been set as an Accept value
        Mockito.`when`(httpConnection.getRequestProperty("Accept"))
            .thenReturn("text/plain")

        Mockito.`when`(httpConnection.inputStream).thenReturn(`is`)
        Mockito.`when`(httpConnection.responseCode)
            .thenReturn(HttpURLConnection.HTTP_OK)

        val request = TestValues.testAuthCodeExchangeRequest
        performTokenRequest(request)

        // application/json should be added after the existing string
        Mockito.verify(httpConnection, Mockito.never()).setRequestProperty(
            ArgumentMatchers.eq("Accept"), ArgumentMatchers.any(String::class.java)
        )
    }

    @Test
    @Throws(Exception::class)
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
    @Throws(Exception::class)
    fun testTokenRequest_withPostAuth() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)
        val `is`: InputStream = ByteArrayInputStream(authCodeExchangeResponseJson.toByteArray())
        Mockito.`when`(httpConnection.getInputStream()).thenReturn(`is`)

        Mockito.`when`(httpConnection.getResponseCode())
            .thenReturn(HttpURLConnection.HTTP_OK)

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertTokenResponse(result.getOrNull(), request)

        val postBody = outputStream.toString()

        val expectedRequestBody =
            request.requestParameters + csp.getRequestParameters(TEST_CLIENT_ID)

        assertTokenRequestBody(postBody, expectedRequestBody)
    }

    @Test
    @Throws(Exception::class)
    fun testTokenRequest_withInvalidGrant() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)
        val `is`: InputStream = ByteArrayInputStream(INVALID_GRANT_RESPONSE_JSON.toByteArray())

        Mockito.`when`(httpConnection.errorStream).thenReturn(`is`)

        Mockito.`when`(httpConnection.responseCode)
            .thenReturn(HttpURLConnection.HTTP_BAD_REQUEST)

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertInvalidGrant(result.exceptionOrNull() as? AuthorizationException)
    }

    @Test
    @Throws(Exception::class)
    fun testTokenRequest_withInvalidGrant2() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)
        val `is`: InputStream = ByteArrayInputStream(INVALID_GRANT_RESPONSE_JSON.toByteArray())
        Mockito.`when`(httpConnection.errorStream).thenReturn(`is`)
        Mockito.`when`(httpConnection.responseCode).thenReturn(199)

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertInvalidGrant(result.exceptionOrNull() as? AuthorizationException)
    }

    @Test
    @Throws(Exception::class)
    fun testTokenRequest_withInvalidGrantWithNoDesc() = runTest {
        val csp = ClientSecretPost(TEST_CLIENT_SECRET)

        val `is`: InputStream =
            ByteArrayInputStream(INVALID_GRANT_NO_DESC_RESPONSE_JSON.toByteArray())

        Mockito.`when`(httpConnection.errorStream).thenReturn(`is`)

        Mockito.`when`(httpConnection.responseCode)
            .thenReturn(HttpURLConnection.HTTP_BAD_REQUEST)

        val request = TestValues.testAuthCodeExchangeRequest
        val result = performTokenRequest(request, csp)

        assertInvalidGrantWithNoDescription(result.exceptionOrNull() as? AuthorizationException)
    }

    @Test
    @Throws(Exception::class)
    fun testTokenRequest_IoException() = runTest {
        val ex: Exception = IOException()
        Mockito.`when`(httpConnection.getInputStream()).thenThrow(ex)

        Mockito.`when`(httpConnection.responseCode)
            .thenReturn(HttpURLConnection.HTTP_OK)

        val result = performTokenRequest(TestValues.testAuthCodeExchangeRequest)

        assertNotNull(result.exceptionOrNull())

        assertEquals(
            AuthorizationException.GeneralErrors.NETWORK_ERROR,
            result.exceptionOrNull()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testRegistrationRequest() = runTest {
        val `is`: InputStream = ByteArrayInputStream(REGISTRATION_RESPONSE_JSON.toByteArray())
        Mockito.`when`(httpConnection.inputStream).thenReturn(`is`)
        val request = TestValues.testRegistrationRequest
        val result = performRegistrationRequest(request)

        assertRegistrationResponse(result.getOrNull(), request)
        val postBody = outputStream.toString()
        assertThat(postBody).isEqualTo(request.toJsonString())
    }

    @Test
    @Throws(Exception::class)
    fun testRegistrationRequest_IoException() = runTest {
        val ex: Exception = IOException()
        Mockito.`when`(httpConnection.inputStream).thenThrow(ex)

        val result = performRegistrationRequest(TestValues.testRegistrationRequest)

        assertNotNull(result.exceptionOrNull())

        assertEquals(
            AuthorizationException.GeneralErrors.NETWORK_ERROR,
            result.exceptionOrNull()
        )
    }

    @Test(expected = IllegalStateException::class)
    @Throws(Exception::class)
    fun testTokenRequest_afterDispose() = runTest {
        service.dispose()
        performTokenRequest(TestValues.testAuthCodeExchangeRequest)
    }

    @Test(expected = IllegalStateException::class)
    @Throws(Exception::class)
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

    /**
     * Custom matcher for verifying the intent fired during token request.
     */
    private class CustomTabsServiceMatcher : ArgumentMatcher<Intent> {
        override fun matches(intent: Intent): Boolean {
            return TEST_BROWSER_PACKAGE == intent.`package`
        }

        override fun toString(): String {
            return "$TEST_BROWSER_PACKAGE == intent.`package`"
        }
    }

    val authCodeExchangeResponseJson: String
        get() = getAuthCodeExchangeResponseJson(null)

    fun getAuthCodeExchangeResponseJson(idToken: String?): String {
        var idToken = idToken
        if (idToken == null) {
            idToken = TestValues.TEST_ID_TOKEN
        }
        return ("{\n"
                + "  \"refresh_token\": \"" + TestValues.TEST_REFRESH_TOKEN + "\",\n"
                + "  \"access_token\": \"" + TestValues.TEST_ACCESS_TOKEN + "\",\n"
                + "  \"expires_in\": \"" + TEST_EXPIRES_IN + "\",\n"
                + "  \"id_token\": \"" + idToken + "\",\n"
                + "  \"token_type\": \"" + AuthorizationResponse.TOKEN_TYPE_BEARER + "\"\n"
                + "}")
    }

    companion object {
        private const val TEST_EXPIRES_IN = 3600
        private const val TEST_BROWSER_PACKAGE = "com.browser.test"

        private const val REGISTRATION_RESPONSE_JSON = ("{\n"
                + " \"client_id\": \"" + TEST_CLIENT_ID + "\",\n"
                + " \"client_secret\": \"" + TEST_CLIENT_SECRET + "\",\n"
                + " \"client_secret_expires_at\": \"" + TestValues.TEST_CLIENT_SECRET_EXPIRES_AT + "\",\n"
                + " \"application_type\": " + RegistrationRequest.APPLICATION_TYPE_NATIVE + "\n"
                + "}")

        private const val INVALID_GRANT_RESPONSE_JSON = ("{\n"
                + "  \"error\": \"invalid_grant\",\n"
                + "  \"error_description\": \"invalid_grant description\"\n"
                + "}")

        private const val INVALID_GRANT_NO_DESC_RESPONSE_JSON = ("{\n"
                + "  \"error\": \"invalid_grant\"\n"
                + "}")

        private const val TEST_INVALID_GRANT_CODE = 2002

        fun serviceIntentEq(): Intent = argThat<Intent>(CustomTabsServiceMatcher())
    }
}
