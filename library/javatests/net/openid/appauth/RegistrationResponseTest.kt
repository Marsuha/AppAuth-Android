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

import androidx.core.net.toUri
import com.google.testing.junit.testparameterinjector.TestParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import net.openid.appauth.RegistrationResponse.Companion.KEY_ADDITIONAL_PARAMETERS
import net.openid.appauth.RegistrationResponse.Companion.KEY_REQUEST
import net.openid.appauth.RegistrationResponse.Companion.PARAM_CLIENT_ID
import net.openid.appauth.RegistrationResponse.Companion.PARAM_CLIENT_ID_ISSUED_AT
import net.openid.appauth.RegistrationResponse.Companion.PARAM_CLIENT_SECRET
import net.openid.appauth.RegistrationResponse.Companion.PARAM_CLIENT_SECRET_EXPIRES_AT
import net.openid.appauth.RegistrationResponse.Companion.PARAM_REGISTRATION_ACCESS_TOKEN
import net.openid.appauth.RegistrationResponse.Companion.PARAM_REGISTRATION_CLIENT_URI
import net.openid.appauth.RegistrationResponse.Companion.PARAM_TOKEN_ENDPOINT_AUTH_METHOD
import net.openid.appauth.TestValues.TEST_APP_REDIRECT_URI
import net.openid.appauth.TestValues.TEST_CLIENT_ID
import net.openid.appauth.TestValues.TEST_CLIENT_SECRET
import net.openid.appauth.TestValues.TEST_CLIENT_SECRET_EXPIRES_AT
import net.openid.appauth.TestValues.testRegistrationRequest
import net.openid.appauth.TestValues.testServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(Enclosed::class)
@Config(sdk = [28])
object RegistrationResponseTest {
    private const val TEST_CLIENT_ID_ISSUED_AT = 34L
    private const val TEST_REGISTRATION_ACCESS_TOKEN = "test_access_token"
    private const val TEST_REGISTRATION_CLIENT_URI =
        "https://test.openid.com/register?client_id=$TEST_CLIENT_ID"
    private const val TEST_TOKEN_ENDPOINT_AUTH_METHOD = "client_secret_basic"

    private val TEST_JSON = """
        {
            "client_id": "$TEST_CLIENT_ID",
            "client_id_issued_at": $TEST_CLIENT_ID_ISSUED_AT,
            "client_secret": "$TEST_CLIENT_SECRET",
            "client_secret_expires_at": $TEST_CLIENT_SECRET_EXPIRES_AT,
            "registration_access_token": "$TEST_REGISTRATION_ACCESS_TOKEN",
            "registration_client_uri": "$TEST_REGISTRATION_CLIENT_URI",
            "application_type": "${RegistrationRequest.APPLICATION_TYPE_NATIVE}",
            "token_endpoint_auth_method": "$TEST_TOKEN_ENDPOINT_AUTH_METHOD"
        }
    """.trimIndent()

    private val testRegistrationRequestJson = testRegistrationRequest.asJsonObject

    @RunWith(RobolectricTestRunner::class)
    @Config(sdk = [28])
    class RegistrationResponseSingleTest {
        private lateinit var minimalBuilder: RegistrationResponse.Builder

        private lateinit var response: RegistrationResponse

        private lateinit var testJson: JsonObject

        @Before
        fun setUp() {
            testJson = Json.parseToJsonElement(TEST_JSON).jsonObject
            minimalBuilder = RegistrationResponse.Builder(testRegistrationRequest)
            response = RegistrationResponse.buildFromJson(testRegistrationRequest, testJson)
            assertValues(response)
        }

        @Test(expected = IllegalArgumentException::class)
        fun testBuilder_setAdditionalParams_withBuiltInParam() {
            minimalBuilder.setAdditionalParameters(
                buildJsonObject { put(PARAM_CLIENT_ID, "client1") }
            )
        }

        @Test
        fun testSerialize() {
            val json = response.asJsonObject

            assertThat(json[KEY_REQUEST]?.toString())
                .isEqualTo(testRegistrationRequest.asJsonString)

            assertThat(json[PARAM_CLIENT_ID_ISSUED_AT]?.jsonPrimitive?.long)
                .isEqualTo(TEST_CLIENT_ID_ISSUED_AT)

            assertThat(json[PARAM_CLIENT_SECRET]?.jsonPrimitive?.content)
                .isEqualTo(TEST_CLIENT_SECRET)

            assertThat(json[PARAM_CLIENT_SECRET_EXPIRES_AT]?.jsonPrimitive?.long)
                .isEqualTo(TEST_CLIENT_SECRET_EXPIRES_AT)

            assertThat(json[PARAM_REGISTRATION_ACCESS_TOKEN]?.jsonPrimitive?.content)
                .isEqualTo(TEST_REGISTRATION_ACCESS_TOKEN)

            assertThat(json[PARAM_REGISTRATION_CLIENT_URI]?.jsonPrimitive?.content)
                .isEqualTo(TEST_REGISTRATION_CLIENT_URI)

            assertThat(json[PARAM_TOKEN_ENDPOINT_AUTH_METHOD]?.jsonPrimitive?.content)
                .isEqualTo(TEST_TOKEN_ENDPOINT_AUTH_METHOD)
        }

        @Test
        fun testSerialize_withAdditionalParameters() {
            val additionalParameters = buildJsonObject { put("test1", "value1") }
            val json = minimalBuilder
                .setClientId(TEST_CLIENT_ID)
                .setAdditionalParameters(additionalParameters)
                .build()
                .asJsonObject

            assertThat(json[KEY_ADDITIONAL_PARAMETERS]).isEqualTo(additionalParameters)
        }

        @Test(expected = IllegalArgumentException::class)
        fun testDeserialize_withoutRequest() {
            RegistrationResponse.fromJsonString(TEST_JSON)
        }

        @Test
        fun testDeserialize() {
            val regRequest = buildJsonObject { put(KEY_REQUEST, testRegistrationRequestJson) }
            val json = JsonObject(testJson + regRequest)
            val response = RegistrationResponse.fromJsonString(json.toString())
            assertValues(response)
        }

        @Test
        fun testSerialization_doesNotChange() {
            val regRequest = buildJsonObject { put(KEY_REQUEST, testRegistrationRequestJson) }
            val json = JsonObject(testJson + regRequest)
            val response = RegistrationResponse.fromJsonString(json.toString())

            val firstOutput = response.asJsonString
            val secondOutput = RegistrationResponse
                .fromJsonString(json.toString())
                .asJsonString

            assertThat(secondOutput).isEqualTo(firstOutput)
        }

        @Test
        fun testHasExpired_withValidClientSecret() {
            val response = RegistrationResponse.buildFromJson(testRegistrationRequest, testJson)
            val now = TimeUnit.SECONDS.toMillis(TEST_CLIENT_SECRET_EXPIRES_AT - 1L)
            assertThat(response.hasClientSecretExpired(TestClock(now))).isFalse()
        }

        @Test
        @Throws(Exception::class)
        fun testHasExpired_withExpiredClientSecret() {
            val response = RegistrationResponse.buildFromJson(testRegistrationRequest, testJson)
            val now = TimeUnit.SECONDS.toMillis(TEST_CLIENT_SECRET_EXPIRES_AT + 1L)
            assertThat(response.hasClientSecretExpired(TestClock(now))).isTrue()
        }

        private fun assertValues(response: RegistrationResponse) {
            assertThat(response.clientId).isEqualTo(TEST_CLIENT_ID)
            assertThat(response.clientIdIssuedAt).isEqualTo(TEST_CLIENT_ID_ISSUED_AT)
            assertThat(response.clientSecret).isEqualTo(TEST_CLIENT_SECRET)
            assertThat(response.clientSecretExpiresAt).isEqualTo(TEST_CLIENT_SECRET_EXPIRES_AT)
            assertThat(response.registrationAccessToken).isEqualTo(TEST_REGISTRATION_ACCESS_TOKEN)
            assertThat(response.registrationClientUri).isEqualTo(TEST_REGISTRATION_CLIENT_URI.toUri())
            assertThat(response.tokenEndpointAuthMethod).isEqualTo(TEST_TOKEN_ENDPOINT_AUTH_METHOD)
        }
    }

    @RunWith(RobolectricTestParameterInjector::class)
    @Config(sdk = [28])
    class RegistrationResponseParameterTest(
        @param:TestParameter(
            PARAM_CLIENT_SECRET_EXPIRES_AT,
            PARAM_REGISTRATION_ACCESS_TOKEN,
            PARAM_REGISTRATION_CLIENT_URI
        )
        var missingParameter: String
    ) {
        private lateinit var responseJson: JsonObject
        private lateinit var minimalRegistrationRequest: RegistrationRequest

        @Before
        fun setUp() {
            responseJson = Json.parseToJsonElement(TEST_JSON).jsonObject

            minimalRegistrationRequest = RegistrationRequest.Builder(
                testServiceConfig,
                listOf(TEST_APP_REDIRECT_URI)
            ).build()
        }

        @Test
        fun testBuilder_fromJsonNWithMissingRequiredParameter() {
            val json = JsonObject(responseJson.filterKeys { it != missingParameter })

            try {
                RegistrationResponse.buildFromJson(
                    minimalRegistrationRequest,
                    json
                )

                Assert.fail("Expected MissingArgumentException not thrown.")
            } catch (e: RegistrationResponse.MissingArgumentException) {
                assertThat(missingParameter).isEqualTo(e.missingField)
            }
        }
    }
}


