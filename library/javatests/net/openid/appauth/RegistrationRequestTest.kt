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
import androidx.core.net.toUri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.openid.appauth.GrantTypeValues.IMPLICIT
import net.openid.appauth.RegistrationRequest.Companion.APPLICATION_TYPE_NATIVE
import net.openid.appauth.RegistrationRequest.Companion.KEY_ADDITIONAL_PARAMETERS
import net.openid.appauth.RegistrationRequest.Companion.KEY_CONFIGURATION
import net.openid.appauth.RegistrationRequest.Companion.PARAM_APPLICATION_TYPE
import net.openid.appauth.RegistrationRequest.Companion.PARAM_GRANT_TYPES
import net.openid.appauth.RegistrationRequest.Companion.PARAM_JWKS
import net.openid.appauth.RegistrationRequest.Companion.PARAM_JWKS_URI
import net.openid.appauth.RegistrationRequest.Companion.PARAM_REDIRECT_URIS
import net.openid.appauth.RegistrationRequest.Companion.PARAM_RESPONSE_TYPES
import net.openid.appauth.RegistrationRequest.Companion.PARAM_SUBJECT_TYPE
import net.openid.appauth.RegistrationRequest.Companion.SUBJECT_TYPE_PAIRWISE
import net.openid.appauth.ResponseTypeValues.ID_TOKEN
import net.openid.appauth.TestValues.TEST_APP_REDIRECT_URI
import net.openid.appauth.TestValues.TEST_APP_SCHEME
import net.openid.appauth.TestValues.testServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RegistrationRequestTest {
    private lateinit var minimalRequestBuilder: RegistrationRequest.Builder
    private lateinit var maximalRequestBuilder: RegistrationRequest.Builder
    private lateinit var testJson: JsonObject
    private lateinit var redirectUris: List<Uri>

    @Before
    fun setUp() {
        redirectUris = listOf(TEST_APP_REDIRECT_URI)
        minimalRequestBuilder = RegistrationRequest.Builder(testServiceConfig, redirectUris)

        maximalRequestBuilder = RegistrationRequest.Builder(testServiceConfig, redirectUris)
            .setResponseTypeValues(ID_TOKEN)
            .setGrantTypeValues(IMPLICIT)
            .setSubjectType(SUBJECT_TYPE_PAIRWISE)

        testJson = Json.parseToJsonElement(TEST_JSON).jsonObject
    }

    @Test
    fun testBuilder() {
        assertValues(minimalRequestBuilder.build())
    }

    @Test
    fun testBuilder_setRedirectUriValues() {
        val redirect1 = "$TEST_APP_SCHEME:/callback1".toUri()
        val redirect2 = "$TEST_APP_SCHEME:/callback2".toUri()
        minimalRequestBuilder.setRedirectUriValues(redirect1, redirect2)
        val request = minimalRequestBuilder.build()
        assertThat(request.redirectUris.containsAll(listOf(redirect1, redirect2))).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuilder_setAdditionalParams_withBuiltInParam() {
        val additionalParams = buildJsonObject { put(PARAM_APPLICATION_TYPE, "web") }
        minimalRequestBuilder.setAdditionalParameters(additionalParams)
    }

    @Test
    fun testApplicationTypeIsNativeByDefault() {
        val request = minimalRequestBuilder.build()
        assertThat(request.applicationType).isEqualTo(APPLICATION_TYPE_NATIVE)
    }

    @Test
    fun testToRequestJsonString_withAdditionalParameters() {
        val request = minimalRequestBuilder
            .setAdditionalParameters(TEST_ADDITIONAL_PARAMS)
            .build()

        val json = request.toRequestJsonObject()

        TEST_ADDITIONAL_PARAMS.forEach { assertThat(json[it.key]).isEqualTo(it.value) }
        assertThat(request.applicationType).isEqualTo(APPLICATION_TYPE_NATIVE)
    }

    @Test
    fun testToRequestJsonString() {
        val request = maximalRequestBuilder.build()
        assertMaximalValuesInJson(request, request.toRequestJsonObject())
    }

    @Test
    fun testToRequestJsonString_withJwksUri() {
        val request = minimalRequestBuilder
            .setJwksUri(TEST_JWKS_URI)
            .build()

        val json = request.toRequestJsonObject()
        assertThat(Uri.parse(json[PARAM_JWKS_URI]?.jsonPrimitive?.content))
            .isEqualTo(TEST_JWKS_URI)
    }


    @Test
    fun testToRequestJsonString_withJwks() {
        val request = minimalRequestBuilder
            .setJwks(Json.parseToJsonElement(TEST_JWKS).jsonObject)
            .build()

        assertThat(request.jwks).isNotNull()

        val json = request.toRequestJsonObject()
        assertThat(json[PARAM_JWKS]).isEqualTo(request.jwks)
    }

    @Test
    fun testSerialize() {
        val request = maximalRequestBuilder.build()
        val jsonObject = request.asJsonObject
        assertMaximalValuesInJson(request, jsonObject)
        assertThat(jsonObject[KEY_CONFIGURATION]?.toString())
            .isEqualTo(request.configuration.asJsonString)
    }

    @Test
    fun testSerialize_withAdditionalParameters() {
        val additionalParameters = buildJsonObject { put("test1", "value1") }
        val request = maximalRequestBuilder.setAdditionalParameters(additionalParameters).build()
        val jsonObject = request.asJsonObject
        assertMaximalValuesInJson(request, jsonObject)
        assertThat(jsonObject[KEY_ADDITIONAL_PARAMETERS]).isEqualTo(additionalParameters)
    }

    @Test
    fun testDeserialize() {
        val json = JsonObject(testJson + mapOf(KEY_CONFIGURATION to testServiceConfig.asJsonObject))
        val request = RegistrationRequest.fromJsonString(json.toString())
        assertThat(request.configuration.asJsonString).isEqualTo(testServiceConfig.asJsonString)
        assertMaximalValuesInJson(request, json)
    }

    @Test
    fun testDeserialize_withAdditionalParameters() {
        val additionalParameters = buildJsonObject {
            put("key1", "value1")
            put("key2", "value2")
        }

        val json = buildJsonObject {
            put(KEY_CONFIGURATION, testServiceConfig.asJsonObject)
            put(KEY_ADDITIONAL_PARAMETERS, additionalParameters)
        }

        val request = RegistrationRequest.fromJsonString(JsonObject( json + testJson).toString())
        assertThat(request.additionalParameters).isEqualTo(additionalParameters)
    }

    private fun assertValues(request: RegistrationRequest) {
        assertEquals(
            "unexpected redirect URI", TEST_APP_REDIRECT_URI,
            request.redirectUris.iterator().next()
        )
        assertEquals(
            "unexpected application type", APPLICATION_TYPE_NATIVE,
            request.applicationType
        )
    }

    private fun assertMaximalValuesInJson(request: RegistrationRequest, json: JsonObject) {
        assertThat(json[PARAM_REDIRECT_URIS]).isEqualTo(request.redirectUris.toJsonArray())
        assertThat(json[PARAM_APPLICATION_TYPE]?.jsonPrimitive?.content).isEqualTo(APPLICATION_TYPE_NATIVE)
        assertThat(json[PARAM_RESPONSE_TYPES]).isEqualTo(request.responseTypes?.toJsonArray())
        assertThat(json[PARAM_GRANT_TYPES]).isEqualTo(request.grantTypes?.toJsonArray())
        assertThat(json[PARAM_SUBJECT_TYPE]?.jsonPrimitive?.content).isEqualTo(request.subjectType)
    }

    companion object {
        private val TEST_ADDITIONAL_PARAMS = buildJsonObject {
            put("test_key1", "test_value1")
            put("test_key2", "test_value2")
        }

        private val TEST_JSON = """
            {
                "application_type": "$APPLICATION_TYPE_NATIVE",
                "redirect_uris": ["$TEST_APP_REDIRECT_URI"],
                "subject_type": "$SUBJECT_TYPE_PAIRWISE",
                "response_types": ["$ID_TOKEN"],
                "grant_types": ["$IMPLICIT"]
            }
        """.trimIndent()


        val TEST_JWKS_URI: Uri = "https://mydomain/path/keys".toUri()
        private val TEST_JWKS = """
            {
                "keys": [
                    {
                        "kty": "RSA",
                        "kid": "key1",
                        "n": "AJnc...L0HU=",
                        "e": "AQAB"
                    }
                ]
            }
        """.trimIndent()
    }
}
