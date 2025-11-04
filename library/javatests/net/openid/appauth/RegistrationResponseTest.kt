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

import android.net.Uri
import net.openid.appauth.TestValues.TEST_APP_REDIRECT_URI
import net.openid.appauth.TestValues.TEST_CLIENT_ID
import net.openid.appauth.TestValues.TEST_CLIENT_SECRET
import net.openid.appauth.TestValues.TEST_CLIENT_SECRET_EXPIRES_AT
import net.openid.appauth.TestValues.testRegistrationRequest
import net.openid.appauth.TestValues.testServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(Enclosed::class)
@Config(sdk = [16])
object RegistrationResponseTest {
    private const val TEST_CLIENT_ID_ISSUED_AT = 34L
    private const val TEST_REGISTRATION_ACCESS_TOKEN = "test_access_token"
    private const val TEST_REGISTRATION_CLIENT_URI =
        "https://test.openid.com/register?client_id=$TEST_CLIENT_ID"
    private const val TEST_TOKEN_ENDPOINT_AUTH_METHOD = "client_secret_basic"

    private const val TEST_JSON = ("{\n"
            + " \"client_id\": \"" + TEST_CLIENT_ID + "\",\n"
            + " \"client_id_issued_at\": \"" + TEST_CLIENT_ID_ISSUED_AT + "\",\n"
            + " \"client_secret\": \"" + TEST_CLIENT_SECRET + "\",\n"
            + " \"client_secret_expires_at\": \"" + TEST_CLIENT_SECRET_EXPIRES_AT + "\",\n"
            + " \"registration_access_token\": \"" + TEST_REGISTRATION_ACCESS_TOKEN + "\",\n"
            + " \"registration_client_uri\": \"" + TEST_REGISTRATION_CLIENT_URI + "\",\n"
            + " \"application_type\": \"" + RegistrationRequest.APPLICATION_TYPE_NATIVE + "\",\n"
            + " \"token_endpoint_auth_method\": \"" + TEST_TOKEN_ENDPOINT_AUTH_METHOD + "\"\n"
            + "}")

    @RunWith(RobolectricTestRunner::class)
    @Config(sdk = [16])
    class RegistrationResponseSingleTest {
        private lateinit var minimalBuilder: RegistrationResponse.Builder
        private lateinit var testJson: JSONObject

        @Before
        @Throws(Exception::class)
        fun setUp() {
            testJson = JSONObject(TEST_JSON)
            minimalBuilder = RegistrationResponse.Builder(testRegistrationRequest)
        }

        @Test(expected = IllegalArgumentException::class)
        fun testBuilder_setAdditionalParams_withBuiltInParam() {
            minimalBuilder.setAdditionalParameters(
                mapOf(RegistrationResponse.PARAM_CLIENT_ID to "client1")
            )
        }

        @Test
        @Throws(Exception::class)
        fun testFromJson() {
            val response = RegistrationResponse.fromJson(testRegistrationRequest, testJson)
            assertValues(response)
        }

        @Test
        @Throws(Exception::class)
        fun testSerialize() {
            val json = RegistrationResponse.fromJson(testRegistrationRequest, testJson)
                .jsonSerialize()

            assertThat(json.get(RegistrationResponse.KEY_REQUEST).toString())
                .isEqualTo(testRegistrationRequest.jsonSerialize().toString())

            assertThat(json.getLong(RegistrationResponse.PARAM_CLIENT_ID_ISSUED_AT))
                .isEqualTo(TEST_CLIENT_ID_ISSUED_AT)

            assertThat(json.getString(RegistrationResponse.PARAM_CLIENT_SECRET))
                .isEqualTo(TEST_CLIENT_SECRET)

            assertThat(json.getLong(RegistrationResponse.PARAM_CLIENT_SECRET_EXPIRES_AT))
                .isEqualTo(TEST_CLIENT_SECRET_EXPIRES_AT)

            assertThat(json.getString(RegistrationResponse.PARAM_REGISTRATION_ACCESS_TOKEN))
                .isEqualTo(TEST_REGISTRATION_ACCESS_TOKEN)

            assertThat(json.getUri(RegistrationResponse.PARAM_REGISTRATION_CLIENT_URI))
                .isEqualTo(Uri.parse(TEST_REGISTRATION_CLIENT_URI))

            assertThat(json.getString(RegistrationResponse.PARAM_TOKEN_ENDPOINT_AUTH_METHOD))
                .isEqualTo(TEST_TOKEN_ENDPOINT_AUTH_METHOD)
        }

        @Test
        @Throws(Exception::class)
        fun testSerialize_withAdditionalParameters() {
            val additionalParameters = mapOf("test1" to "value1")
            val json = minimalBuilder.setClientId(TEST_CLIENT_ID)
                .setAdditionalParameters(additionalParameters)
                .build()
                .jsonSerialize()

            assertThat(json.getStringMap(RegistrationResponse.KEY_ADDITIONAL_PARAMETERS))
                .isEqualTo(additionalParameters)
        }

        @Test(expected = IllegalArgumentException::class)
        @Throws(Exception::class)
        fun testDeserialize_withoutRequest() {
            RegistrationResponse.jsonDeserialize(testJson)
        }

        @Test
        @Throws(Exception::class)
        fun testDeserialize() {
            testJson.put(
                RegistrationResponse.KEY_REQUEST,
                testRegistrationRequest.jsonSerialize()
            )

            val response = RegistrationResponse.jsonDeserialize(testJson)
            assertValues(response)
        }

        @Test
        @Throws(Exception::class)
        fun testSerialization_doesNotChange() {
            testJson.put(
                RegistrationResponse.KEY_REQUEST,
                testRegistrationRequest.jsonSerialize()
            )

            val response = RegistrationResponse.jsonDeserialize(testJson)

            val firstOutput = response.jsonSerializeString()
            val secondOutput = RegistrationResponse.jsonDeserialize(testJson).jsonSerializeString()

            assertThat(secondOutput).isEqualTo(firstOutput)
        }

        @Test
        @Throws(Exception::class)
        fun testHasExpired_withValidClientSecret() {
            val response = RegistrationResponse.fromJson(testRegistrationRequest, testJson)
            val now = TimeUnit.SECONDS.toMillis(TEST_CLIENT_SECRET_EXPIRES_AT - 1L)
            assertThat(response.hasClientSecretExpired(TestClock(now))).isFalse()
        }

        @Test
        @Throws(Exception::class)
        fun testHasExpired_withExpiredClientSecret() {
            val response = RegistrationResponse.fromJson(testRegistrationRequest, testJson)
            val now = TimeUnit.SECONDS.toMillis(TEST_CLIENT_SECRET_EXPIRES_AT + 1L)
            assertThat(response.hasClientSecretExpired(TestClock(now))).isTrue()
        }

        private fun assertValues(response: RegistrationResponse) {
            assertThat(response.clientId).isEqualTo(TEST_CLIENT_ID)
            assertThat(response.clientIdIssuedAt).isEqualTo(TEST_CLIENT_ID_ISSUED_AT)
            assertThat(response.clientSecret).isEqualTo(TEST_CLIENT_SECRET)
            assertThat(response.clientSecretExpiresAt).isEqualTo(TEST_CLIENT_SECRET_EXPIRES_AT)
            assertThat(response.registrationAccessToken).isEqualTo(TEST_REGISTRATION_ACCESS_TOKEN)
            assertThat(response.registrationClientUri)
                .isEqualTo(Uri.parse(TEST_REGISTRATION_CLIENT_URI))

            assertThat(response.tokenEndpointAuthMethod).isEqualTo(TEST_TOKEN_ENDPOINT_AUTH_METHOD)
        }
    }

    @RunWith(ParameterizedRobolectricTestRunner::class)
    @Config(sdk = [16])
    class RegistrationResponseParameterTest(private val missingParameter: String?) {
        private lateinit var responseJson: JSONObject
        private lateinit var minimalRegistrationRequest: RegistrationRequest

        @Before
        @Throws(Exception::class)
        fun setUp() {
            responseJson = JSONObject(TEST_JSON)

            minimalRegistrationRequest = RegistrationRequest.Builder(
                testServiceConfig,
                listOf(TEST_APP_REDIRECT_URI)
            ).build()
        }

        @Test
        @Throws(Exception::class)
        fun testBuilder_fromJsonNWithMissingRequiredParameter() {
            responseJson.remove(missingParameter)

            try {
                RegistrationResponse.fromJson(minimalRegistrationRequest, responseJson)
                Assert.fail("Expected MissingArgumentException not thrown.")
            } catch (e: RegistrationResponse.MissingArgumentException) {
                assertThat(missingParameter).isEqualTo(e.missingField)
            }
        }

        companion object {
            @Suppress("unused")
            @ParameterizedRobolectricTestRunner.Parameters(name = "Missing parameter = {0}")
            fun data() = listOf(
                listOf(
                    listOf(RegistrationResponse.PARAM_CLIENT_SECRET_EXPIRES_AT),
                    listOf(RegistrationResponse.PARAM_REGISTRATION_ACCESS_TOKEN),
                    listOf(RegistrationResponse.PARAM_REGISTRATION_CLIENT_URI)
                )
            )
        }
    }
}


