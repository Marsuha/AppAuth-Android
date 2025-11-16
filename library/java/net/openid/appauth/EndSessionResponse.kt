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

import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A response to end session request.
 *
 * @see EndSessionRequest
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">
 *     OpenID Connect RP-Initiated Logout 1.0 - draft 01</a>
 */
@Serializable
class EndSessionResponse private constructor(
    /**
     * The end session request associated with this response.
     */
    val request: EndSessionRequest,
    /**
     * The returned state parameter, which must match the value specified in the request.
     * AppAuth for Android ensures that this is the case.
     */
    override val state: String?
) : AuthorizationManagementResponse {

    override val asJsonString get() = Json.encodeToString(this)
    /**
     * Creates instances of [EndSessionResponse].
     */
    class Builder(private var request: EndSessionRequest) {

        private var state: String? = null

        @VisibleForTesting
        fun fromUri(uri: Uri): Builder {
            setState(uri.getQueryParameter(KEY_STATE))
            return this
        }

        @Suppress("unused")
        fun setRequest(request: EndSessionRequest): Builder {
            this@Builder.request = request
            return this
        }

        fun setState(state: String?): Builder {
            state?.let { require(it.isNotEmpty()) { "state must not be empty" } }
            this@Builder.state = state
            return this
        }

        /**
         * Builds the response object.
         */
        fun build(): EndSessionResponse {
            return EndSessionResponse(
                request,
                state
            )
        }
    }

    /**
     * Produces an intent containing this end session response. This is used to deliver the
     * end session response to the registered handler after a call to
     * [AuthorizationService.performEndSessionRequest].
     */
    override fun toIntent() = Intent().apply {
        putExtra(EXTRA_RESPONSE, asJsonString)
    }

    companion object {
        /**
         * The extra string used to store an [EndSessionResponse] in an intent by
         * [.toIntent].
         */
        const val EXTRA_RESPONSE: String = "net.openid.appauth.EndSessionResponse"

        @VisibleForTesting
        const val KEY_STATE: String = "state"

        fun fromJsonString(jsonStr: String) =
            Json.decodeFromString<EndSessionResponse>(jsonStr)

        /**
         * Extracts an end session response from an intent produced by [.toIntent]. This is
         * used to extract the response from the intent data passed to an activity registered as the
         * handler for [AuthorizationService.performEndSessionRequest].
         */
        @JvmStatic
        fun fromIntent(dataIntent: Intent): EndSessionResponse? {
            if (!containsEndSessionResponse(dataIntent)) return null
            return fromJsonString(dataIntent.getStringExtra(EXTRA_RESPONSE)!!)
        }

        @JvmStatic
        fun containsEndSessionResponse(intent: Intent) = intent.hasExtra(EXTRA_RESPONSE)
    }
}
