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
import net.openid.appauth.TestValues.TEST_APP_SCHEME
import net.openid.appauth.TestValues.testServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [16])
class RegistrationRequestTest {
    private lateinit var minimalRequestBuilder: RegistrationRequest.Builder
    private lateinit var maximalRequestBuilder: RegistrationRequest.Builder
    private lateinit var json: JSONObject
    private lateinit var redirectUris: List<Uri>

    @Before
    @Throws(JSONException::class)
    fun setUp() {
        redirectUris = listOf(TEST_APP_REDIRECT_URI)
        minimalRequestBuilder = RegistrationRequest.Builder(testServiceConfig, redirectUris)

        maximalRequestBuilder = RegistrationRequest.Builder(testServiceConfig, redirectUris)
            .setResponseTypeValues(ResponseTypeValues.ID_TOKEN)
            .setGrantTypeValues(GrantTypeValues.IMPLICIT)
            .setSubjectType(RegistrationRequest.SUBJECT_TYPE_PAIRWISE)

        json = JSONObject(TEST_JSON)
    }

    @Test
    fun testBuilder() {
        assertValues(minimalRequestBuilder.build())
    }

    @Test
    fun testBuilder_setRedirectUriValues() {
        val redirect1 = Uri.parse("$TEST_APP_SCHEME:/callback1")
        val redirect2 = Uri.parse("$TEST_APP_SCHEME:/callback2")
        minimalRequestBuilder.setRedirectUriValues(redirect1, redirect2)
        val request = minimalRequestBuilder.build()
        assertThat(request.redirectUris.containsAll(listOf(redirect1, redirect2))).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBuilder_setAdditionalParams_withBuiltInParam() {
        val additionalParams = mapOf(RegistrationRequest.PARAM_APPLICATION_TYPE to "web")
        minimalRequestBuilder.setAdditionalParameters(additionalParams)
    }

    @Test
    fun testApplicationTypeIsNativeByDefault() {
        val request = minimalRequestBuilder.build()
        assertThat(request.applicationType).isEqualTo(RegistrationRequest.APPLICATION_TYPE_NATIVE)
    }

    @Test
    @Throws(JSONException::class)
    fun testToJsonString_withAdditionalParameters() {
        val request = minimalRequestBuilder
            .setAdditionalParameters(TEST_ADDITIONAL_PARAMS)
            .build()

        val jsonStr = request.toJsonString()

        val json = JSONObject(jsonStr)
        TEST_ADDITIONAL_PARAMS.forEach { assertThat(json.get(it.key)).isEqualTo(it.value) }

        assertThat(request.applicationType)
            .isEqualTo(RegistrationRequest.APPLICATION_TYPE_NATIVE)
    }

    @Test
    @Throws(JSONException::class)
    fun testToJsonString() {
        val request = maximalRequestBuilder.build()
        val jsonStr = request.toJsonString()
        assertMaximalValuesInJson(request, JSONObject(jsonStr))
    }

    @Test
    @Throws(JSONException::class)
    fun testToJsonString_withJwksUri() {
        val request = minimalRequestBuilder
            .setJwksUri(TEST_JWKS_URI)
            .build()

        val jsonStr = request.toJsonString()
        val json = JSONObject(jsonStr)

        assertThat(Uri.parse(json.getString(RegistrationRequest.PARAM_JWKS_URI)))
            .isEqualTo(TEST_JWKS_URI)
    }


    @Test
    @Throws(JSONException::class)
    fun testToJsonString_withJwks() {
        val request = minimalRequestBuilder
            .setJwks(JSONObject(TEST_JWKS))
            .build()

        assertThat(request.jwks).isNotNull()

        val jsonStr = request.toJsonString()
        val json = JSONObject(jsonStr)

        assertThat(json.getJSONObject(RegistrationRequest.PARAM_JWKS).toString())
            .isEqualTo(request.jwks.toString())
    }

    @Test
    @Throws(JSONException::class)
    fun testSerialize() {
        val request = maximalRequestBuilder.build()
        val json = request.jsonSerialize()
        assertMaximalValuesInJson(request, json)
        assertThat(json.getJSONObject(RegistrationRequest.KEY_CONFIGURATION).toString())
            .isEqualTo(request.configuration.toJson().toString())
    }

    @Test
    @Throws(JSONException::class)
    fun testSerialize_withAdditionalParameters() {
        val additionalParameters = mapOf("test1" to "value1")
        val request = maximalRequestBuilder.setAdditionalParameters(additionalParameters).build()
        val json = request.jsonSerialize()
        assertMaximalValuesInJson(request, json)
        assertThat(json.getStringMap(RegistrationRequest.KEY_ADDITIONAL_PARAMETERS))
            .isEqualTo(additionalParameters)
    }

    @Test
    @Throws(JSONException::class)
    fun testDeserialize() {
        json.put(RegistrationRequest.KEY_CONFIGURATION, testServiceConfig.toJson())
        val request = RegistrationRequest.jsonDeserialize(json)
        assertThat(request.configuration.toJsonString()).isEqualTo(testServiceConfig.toJsonString())
        assertMaximalValuesInJson(request, json)
    }

    @Test
    @Throws(JSONException::class)
    fun testDeserialize_withAdditionalParameters() {
        json.put(RegistrationRequest.KEY_CONFIGURATION, testServiceConfig.toJson())
        val additionalParameters = mapOf("key1" to "value1", "key2" to "value2")

        json.put(
            RegistrationRequest.KEY_ADDITIONAL_PARAMETERS,
            additionalParameters.toJsonObject()
        )

        val request = RegistrationRequest.jsonDeserialize(json)
        assertThat(request.additionalParameters).isEqualTo(additionalParameters)
    }

    private fun assertValues(request: RegistrationRequest) {
        Assert.assertEquals(
            "unexpected redirect URI", TEST_APP_REDIRECT_URI,
            request.redirectUris.iterator().next()
        )
        Assert.assertEquals(
            "unexpected application type", RegistrationRequest.APPLICATION_TYPE_NATIVE,
            request.applicationType
        )
    }

    @Throws(JSONException::class)
    private fun assertMaximalValuesInJson(request: RegistrationRequest, json: JSONObject) {
        assertThat(json.get(RegistrationRequest.PARAM_REDIRECT_URIS))
            .isEqualTo(request.redirectUris.toJsonArray())

        assertThat(json.get(RegistrationRequest.PARAM_APPLICATION_TYPE))
            .isEqualTo(RegistrationRequest.APPLICATION_TYPE_NATIVE)

        assertThat(json.get(RegistrationRequest.PARAM_RESPONSE_TYPES))
            .isEqualTo(request.responseTypes?.toJsonArray())

        assertThat(json.get(RegistrationRequest.PARAM_GRANT_TYPES))
            .isEqualTo(request.grantTypes?.toJsonArray())

        assertThat(json.get(RegistrationRequest.PARAM_SUBJECT_TYPE))
            .isEqualTo(request.subjectType)
    }

    companion object {
        private val TEST_ADDITIONAL_PARAMS = mapOf(
            "test_key1" to "test_value1",
            "test_key2" to "test_value2"
        )

        private val TEST_JSON = ("{\n"
                + " \"application_type\": \"" + RegistrationRequest.APPLICATION_TYPE_NATIVE + "\",\n"
                + " \"redirect_uris\": [\"" + TEST_APP_REDIRECT_URI + "\"],\n"
                + " \"subject_type\": \"" + RegistrationRequest.SUBJECT_TYPE_PAIRWISE + "\",\n"
                + " \"response_types\": [\"" + ResponseTypeValues.ID_TOKEN + "\"],\n"
                + " \"grant_types\": [\"" + GrantTypeValues.IMPLICIT + "\"]\n"
                + "}")

        val TEST_JWKS_URI: Uri = Uri.parse("https://mydomain/path/keys")
        private const val TEST_JWKS = ("{\n"
                + " \"keys\": [\n"
                + "  {\n"
                + "   \"kty\": \"RSA\",\n"
                + "   \"kid\": \"key1\",\n"
                + "   \"n\": \"AJnc...L0HU=\",\n"
                + "   \"e\": \"AQAB\"\n"
                + "  }\n"
                + " ]\n"
                + "}")
    }
}
