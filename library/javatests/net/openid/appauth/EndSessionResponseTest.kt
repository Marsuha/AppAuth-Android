package net.openid.appauth

import android.content.Intent
import android.net.Uri
import net.openid.appauth.EndSessionResponse.Companion.containsEndSessionResponse
import net.openid.appauth.EndSessionResponse.Companion.fromIntent
import net.openid.appauth.EndSessionResponse.Companion.jsonDeserialize
import net.openid.appauth.TestValues.testEndSessionRequest
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EndSessionResponseTest {
    fun testBuilder_nullState() {
        val res = EndSessionResponse.Builder(TEST_REQUEST)
            .setState(null).build()

        assertThat(res.state).isNull()
    }

    @Test
    fun testIntentSerializeDeserialize() {
        val endSessionResponse = EndSessionResponse.Builder(TEST_REQUEST)
                .setState(TEST_REQUEST.state)
                .build()

        val endSessionIntent = endSessionResponse.toIntent()
        val deserializeResponse = fromIntent(endSessionIntent)

        assertThat(deserializeResponse).isNotNull()
        assertThat(deserializeResponse!!.state).isEqualTo(endSessionResponse.request.state)

        assertThat(deserializeResponse.request.postLogoutRedirectUri)
            .isEqualTo(endSessionResponse.request.postLogoutRedirectUri)

        assertThat(deserializeResponse.request.configuration.endSessionEndpoint)
            .isEqualTo(endSessionResponse.request.configuration.endSessionEndpoint)

        assertThat(deserializeResponse.request.state)
            .isEqualTo(endSessionResponse.request.state)

        assertThat(deserializeResponse.request.idTokenHint)
            .isEqualTo(endSessionResponse.request.idTokenHint)
    }

    @Test
    fun testIntentSerializeNull() {
        val deserializeResponse = fromIntent(Intent())
        assertThat(deserializeResponse).isNull()
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIntentDeserializeError() {
        val intent = Intent()
        intent.putExtra(EndSessionResponse.EXTRA_RESPONSE, "")
        fromIntent(intent)
    }

    @Test
    @Throws(JSONException::class)
    fun testJsonSerializeDeserialize() {
        val endSessionResponse = EndSessionResponse.Builder(TEST_REQUEST)
                .setState(TEST_REQUEST.state)
                .build()

        val endSessionResponseJson = endSessionResponse.jsonSerialize()
        val deserializeResponse = jsonDeserialize(endSessionResponseJson)

        assertThat(deserializeResponse).isNotNull()
        assertThat(deserializeResponse.state).isEqualTo(endSessionResponse.state)

        assertThat(deserializeResponse.request.postLogoutRedirectUri)
            .isEqualTo(endSessionResponse.request.postLogoutRedirectUri)

        assertThat(deserializeResponse.request.configuration.endSessionEndpoint)
            .isEqualTo(endSessionResponse.request.configuration.endSessionEndpoint)

        assertThat(deserializeResponse.request.state)
            .isEqualTo(endSessionResponse.request.state)

        assertThat(deserializeResponse.request.idTokenHint)
            .isEqualTo(endSessionResponse.request.idTokenHint)
    }

    @Test
    fun testFromRequestAndUri_Success() {
        val endSessionResponse = EndSessionResponse.Builder(TEST_REQUEST)
                .setState(TEST_REQUEST.state)
                .build()

        val endSessionUri = Uri.Builder()
            .appendQueryParameter(EndSessionResponse.KEY_STATE, TEST_REQUEST.state)
            .build()

        val endSessionResponseDeserialized = EndSessionResponse.Builder(TEST_REQUEST)
                .fromUri(endSessionUri)
                .build()

        assertThat(endSessionResponseDeserialized).isNotNull()
        assertThat(endSessionResponseDeserialized.state).isEqualTo(endSessionResponse.state)

        assertThat(endSessionResponseDeserialized.request.postLogoutRedirectUri)
            .isEqualTo(endSessionResponse.request.postLogoutRedirectUri)

        assertThat(endSessionResponseDeserialized.request.configuration.endSessionEndpoint)
            .isEqualTo(endSessionResponse.request.configuration.endSessionEndpoint)

        assertThat(endSessionResponseDeserialized.request.state)
            .isEqualTo(endSessionResponse.request.state)

        assertThat(endSessionResponseDeserialized.request.idTokenHint)
            .isEqualTo(endSessionResponse.request.idTokenHint)
    }

    @Test
    fun testIntent_containsEndSessionResponse_True() {
        val endSessionResponse = EndSessionResponse.Builder(TEST_REQUEST)
                .setState(TEST_REQUEST.state)
                .build()

        val endSessionIntent = endSessionResponse.toIntent()

        assertThat(containsEndSessionResponse(endSessionIntent)).isTrue()
    }

    @Test
    fun testIntent_containsEndSessionResponse_False() {
        val intent = Intent()
        assertThat(containsEndSessionResponse(intent)).isFalse()
    }

    companion object {
        private val TEST_REQUEST = testEndSessionRequest
    }
}
