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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthorizationServiceConfiguration.Companion.buildConfigurationUriFromIssuer
import net.openid.appauth.AuthorizationServiceConfiguration.Companion.fromJson
import net.openid.appauth.connectivity.ConnectionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class AuthorizationServiceConfigurationTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private lateinit var configuration: AuthorizationServiceConfiguration

    @Mock
    lateinit var httpConnection: HttpURLConnection

    @Mock
    lateinit var connectionBuilder: ConnectionBuilder

    @Before
    @Throws(Exception::class)
    fun setUp() {
        configuration = AuthorizationServiceConfiguration(
            TEST_AUTH_ENDPOINT.toUri(),
            TEST_TOKEN_ENDPOINT.toUri(),
            TEST_REGISTRATION_ENDPOINT.toUri(),
            TEST_END_SESSION_ENDPOINT.toUri()
        )

        whenever(connectionBuilder.openConnection(any<Uri>()))
            .thenReturn(httpConnection)
    }

    @Test
    fun testDefaultConstructor() {
        assertMembers(configuration)
    }

    @Test
    @Throws(Exception::class)
    fun testSerialization() {
        val config = fromJson(configuration.toJson())
        assertMembers(config)
    }

    @Test
    @Throws(Exception::class)
    fun testSerializationWithoutRegistrationEndpoint() {
        val config = AuthorizationServiceConfiguration(
            authorizationEndpoint = Uri.parse(TEST_AUTH_ENDPOINT),
            tokenEndpoint = Uri.parse(TEST_TOKEN_ENDPOINT),
            endSessionEndpoint = Uri.parse(TEST_END_SESSION_ENDPOINT)
        )

        val deserialized = fromJson(config.toJson())

        assertThat(deserialized.authorizationEndpoint)
            .isEqualTo(config.authorizationEndpoint)

        assertThat(deserialized.tokenEndpoint).isEqualTo(config.tokenEndpoint)
        assertThat(deserialized.registrationEndpoint).isNull()

        assertThat(deserialized.endSessionEndpoint)
            .isEqualTo(config.endSessionEndpoint)
    }

    @Test
    @Throws(Exception::class)
    fun testSerializationWithoutRegistrationEndpointAndEndSessionEndpoint() {
        val config = AuthorizationServiceConfiguration(
            Uri.parse(TEST_AUTH_ENDPOINT),
            Uri.parse(TEST_TOKEN_ENDPOINT)
        )

        val deserialized = fromJson(config.toJson())

        assertThat(deserialized.authorizationEndpoint)
            .isEqualTo(config.authorizationEndpoint)

        assertThat(deserialized.tokenEndpoint).isEqualTo(config.tokenEndpoint)
        assertThat(deserialized.endSessionEndpoint).isNull()
        assertThat(deserialized.registrationEndpoint).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun testDiscoveryConstructorWithName() {
        val json = JSONObject(TEST_JSON)
        val discovery = AuthorizationServiceDiscovery(json)
        val config = AuthorizationServiceConfiguration(discovery)
        assertMembers(config)
    }

    @Test
    @Throws(Exception::class)
    fun testDiscoveryConstructorWithoutName() {
        val json = JSONObject(TEST_JSON)
        val discovery = AuthorizationServiceDiscovery(json)
        val config = AuthorizationServiceConfiguration(discovery)
        assertMembers(config)
    }

    private fun assertMembers(config: AuthorizationServiceConfiguration) {
        assertEquals(TEST_AUTH_ENDPOINT, config.authorizationEndpoint.toString())
        assertEquals(TEST_TOKEN_ENDPOINT, config.tokenEndpoint.toString())
        assertEquals(TEST_REGISTRATION_ENDPOINT, config.registrationEndpoint.toString())
        assertEquals(TEST_END_SESSION_ENDPOINT, config.endSessionEndpoint.toString())
    }

    @Test
    fun testBuildConfigurationUriFromIssuer() {
        val issuerUri = Uri.parse("https://test.openid.com")
        assertThat(buildConfigurationUriFromIssuer(issuerUri))
            .isEqualTo(TEST_DISCOVERY_URI)
    }

    @Test
    fun testBuildConfigurationUriFromIssuer_withRootPath() {
        val issuerUri = Uri.parse("https://test.openid.com/")
        assertThat(buildConfigurationUriFromIssuer(issuerUri))
            .isEqualTo(TEST_DISCOVERY_URI)
    }

    @Test
    fun testBuildConfigurationUriFromIssuer_withExtendedPath() {
        val issuerUri = Uri.parse("https://test.openid.com/tenant1")
        assertThat(buildConfigurationUriFromIssuer(issuerUri))
            .isEqualTo(
                Uri.parse("https://test.openid.com/tenant1/.well-known/openid-configuration")
            )
    }

    @Test
    @Throws(Exception::class)
    fun testFetchFromUrl_success() = runTest {
        val `is`: InputStream = ByteArrayInputStream(TEST_JSON.toByteArray())
        whenever(httpConnection.inputStream).thenReturn(`is`)
        val resultConfig = doFetch()
        val config = resultConfig.getOrNull()
        assertNotNull(config)
        assertEquals(TEST_AUTH_ENDPOINT, config!!.authorizationEndpoint.toString())
        assertEquals(TEST_TOKEN_ENDPOINT, config.tokenEndpoint.toString())
        verify(httpConnection).connect()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Throws(Exception::class)
    fun testFetchFromUrlWithoutName() = runTest {
        val `is`: InputStream = ByteArrayInputStream(TEST_JSON.toByteArray())
        whenever(httpConnection.inputStream).thenReturn(`is`)
        val resultConfig = doFetch()
        val config = resultConfig.getOrNull()
        assertNotNull(config)
        assertEquals(TEST_AUTH_ENDPOINT, config!!.authorizationEndpoint.toString())
        assertEquals(TEST_TOKEN_ENDPOINT, config.tokenEndpoint.toString())
        verify(httpConnection).connect()
    }

    @Test
    @Throws(Exception::class)
    fun testFetchFromUrl_missingArgument() = runTest {
        val `is`: InputStream = ByteArrayInputStream(TEST_JSON_MISSING_ARGUMENT.toByteArray())
        whenever(httpConnection.inputStream).thenReturn(`is`)
        val resultConfig = doFetch()
        assertNotNull(resultConfig.exceptionOrNull())

        assertEquals(
            AuthorizationException.GeneralErrors.INVALID_DISCOVERY_DOCUMENT,
            resultConfig.exceptionOrNull()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFetchFromUrl_malformedJson() = runTest {
        val `is`: InputStream = ByteArrayInputStream(TEST_JSON_MALFORMED.toByteArray())
        whenever(httpConnection.inputStream).thenReturn(`is`)
        val resultConfig = doFetch()
        assertNotNull(resultConfig.exceptionOrNull())

        assertEquals(
            AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR,
            resultConfig.exceptionOrNull()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFetchFromUrl_IoException() = runTest {
        val ex = IOException()
        whenever(httpConnection.inputStream).thenThrow(ex)
        val resultConfig = doFetch()
        assertNotNull(resultConfig.exceptionOrNull())

        assertEquals(
            AuthorizationException.GeneralErrors.NETWORK_ERROR,
            resultConfig.exceptionOrNull()
        )
    }

    private suspend fun doFetch(): Result<AuthorizationServiceConfiguration> {
        val result = try {
            Result.success(
                AuthorizationServiceConfiguration.fetchFromUrl(
                    TEST_DISCOVERY_URI,
                    connectionBuilder
                )
            )
        } catch (ex: AuthorizationException) {
            Result.failure(ex)
        }

        assertTrue((result.isSuccess) xor (result.isFailure))
        return result
    }

    @Suppress("unused")
    companion object {
        private const val CALLBACK_TIMEOUT_MILLIS = 1000
        private const val TEST_NAME = "test_name"
        private const val TEST_ISSUER = "test_issuer"
        private const val TEST_AUTH_ENDPOINT = "https://test.openid.com/o/oauth/auth"
        private const val TEST_TOKEN_ENDPOINT = "https://test.openid.com/o/oauth/token"
        private const val TEST_END_SESSION_ENDPOINT = "https://test.openid.com/o/oauth/logout"
        private const val TEST_REGISTRATION_ENDPOINT =
            "https://test.openid.com/o/oauth/registration"
        private const val TEST_USERINFO_ENDPOINT = "https://test.openid.com/o/oauth/userinfo"
        private const val TEST_JWKS_URI = "https://test.openid.com/o/oauth/jwks"
        private val TEST_RESPONSE_TYPE_SUPPORTED = listOf("code", "token")
        private val TEST_SUBJECT_TYPES_SUPPORTED = listOf("public")
        private val TEST_ID_TOKEN_SIGNING_ALG_VALUES = listOf("RS256")
        private val TEST_SCOPES_SUPPORTED = listOf("openid", "profile")
        private val TEST_TOKEN_ENDPOINT_AUTH_METHODS =
            listOf("client_secret_post", "client_secret_basic")
        private val TEST_CLAIMS_SUPPORTED = listOf("aud", "exp")
        private val TEST_DISCOVERY_URI: Uri =
            Uri.parse("https://test.openid.com/.well-known/openid-configuration")
        val TEST_JSON: String = """
            {
             "issuer": "$TEST_ISSUER",
             "authorization_endpoint": "$TEST_AUTH_ENDPOINT",
             "token_endpoint": "$TEST_TOKEN_ENDPOINT",
             "registration_endpoint": "$TEST_REGISTRATION_ENDPOINT",
             "end_session_endpoint": "$TEST_END_SESSION_ENDPOINT",
             "userinfo_endpoint": "$TEST_USERINFO_ENDPOINT",
             "jwks_uri": "$TEST_JWKS_URI",
             "response_types_supported": ${TEST_RESPONSE_TYPE_SUPPORTED.toJson()},
             "subject_types_supported": ${TEST_SUBJECT_TYPES_SUPPORTED.toJson()},
             "id_token_signing_alg_values_supported": ${TEST_ID_TOKEN_SIGNING_ALG_VALUES.toJson()},
             "scopes_supported": ${TEST_SCOPES_SUPPORTED.toJson()},
             "token_endpoint_auth_methods_supported": ${TEST_TOKEN_ENDPOINT_AUTH_METHODS.toJson()},
             "claims_supported": ${TEST_CLAIMS_SUPPORTED.toJson()}
            }
        """.trimIndent()

        private val TEST_JSON_MALFORMED = """
            {
             "issuer": "$TEST_ISSUER",
             "authorization_endpoint": "$TEST_AUTH_ENDPOINT",
             "token_endpoint": "$TEST_TOKEN_ENDPOINT",
             "userinfo_endpoint": "$TEST_USERINFO_ENDPOINT",
             "jwks_uri": "$TEST_JWKS_URI",
             "response_types_supported": ${TEST_RESPONSE_TYPE_SUPPORTED.toJson()},
             "subject_types_supported": ${TEST_SUBJECT_TYPES_SUPPORTED.toJson()},
             "id_token_signing_alg_values_supported": ${TEST_ID_TOKEN_SIGNING_ALG_VALUES.toJson()},
             "scopes_supported": ${TEST_SCOPES_SUPPORTED.toJson()},
             "token_endpoint_auth_methods_supported": ${TEST_TOKEN_ENDPOINT_AUTH_METHODS.toJson()},
             "claims_supported": ${TEST_CLAIMS_SUPPORTED.toJson()},
            }
        """.trimIndent()

        private val TEST_JSON_MISSING_ARGUMENT = """
            {
             "issuer": "$TEST_ISSUER",
             "authorization_endpoint": "$TEST_AUTH_ENDPOINT",
             "token_endpoint": "$TEST_TOKEN_ENDPOINT",
             "userinfo_endpoint": "$TEST_USERINFO_ENDPOINT"
            }
        """.trimIndent()

        private fun List<String>.toJson(): String {
            return JSONArray(this).toString()
        }
    }
}
