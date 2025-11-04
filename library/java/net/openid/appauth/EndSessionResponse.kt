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

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import org.json.JSONException
import org.json.JSONObject

/**
 * A response to end session request.
 *
 * @see EndSessionRequest
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">
 *     OpenID Connect RP-Initiated Logout 1.0 - draft 01</a>
 */
class EndSessionResponse private constructor(
    /**
     * The end session request associated with this response.
     */
    @JvmField val request: EndSessionRequest,
    /**
     * The returned state parameter, which must match the value specified in the request.
     * AppAuth for Android ensures that this is the case.
     */
    override val state: String?
) : AuthorizationManagementResponse {
    /**
     * Creates instances of [EndSessionResponse].
     */
    class Builder(request: EndSessionRequest) {
        private var mRequest: EndSessionRequest = request

        private var mState: String? = null

        @VisibleForTesting
        fun fromUri(uri: Uri): Builder {
            val state = uri.getQueryParameter(KEY_STATE)
            checkNotNull(state) { "state must be provided" }
            setState(state)
            return this
        }

        @Suppress("unused")
        fun setRequest(request: EndSessionRequest): Builder {
            mRequest = request
            return this
        }

        fun setState(state: String?): Builder {
            state?.let { require(it.isNotEmpty()) { "state must not be empty" } }
            mState = state
            return this
        }

        /**
         * Builds the response object.
         */
        fun build(): EndSessionResponse {
            return EndSessionResponse(
                mRequest,
                mState
            )
        }
    }

    /**
     * Produces a JSON representation of the end session response for persistent storage or local
     * transmission (e.g. between activities).
     */
    @SuppressLint("VisibleForTests")
    override fun jsonSerialize() = JSONObject().apply {
        put(KEY_REQUEST, request.jsonSerialize())
        state?.let { put(KEY_STATE, it) }
    }

    /**
     * Produces an intent containing this end session response. This is used to deliver the
     * end session response to the registered handler after a call to
     * [AuthorizationService.performEndSessionRequest].
     */
    override fun toIntent() = Intent().apply {
        putExtra(EXTRA_RESPONSE, jsonSerializeString())
    }

    companion object {
        /**
         * The extra string used to store an [EndSessionResponse] in an intent by
         * [.toIntent].
         */
        const val EXTRA_RESPONSE: String = "net.openid.appauth.EndSessionResponse"

        @VisibleForTesting
        const val KEY_REQUEST: String = "request"

        @VisibleForTesting
        const val KEY_STATE: String = "state"

        /**
         * Reads an end session response from a JSON string representation produced by
         * [.jsonSerialize].
         *
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject): EndSessionResponse {
            require(json.has(KEY_REQUEST)) { "authorization request not provided and not found in JSON" }

            return EndSessionResponse(
                EndSessionRequest.jsonDeserialize(json.getJSONObject(KEY_REQUEST)),
                json.getStringIfDefined(KEY_STATE)
            )
        }

        /**
         * Reads an end session response from a JSON string representation produced by
         * [.jsonSerializeString]. This method is just a convenience wrapper for
         * [.jsonDeserialize], converting the JSON string to its JSON object form.
         *
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @Throws(JSONException::class)
        fun jsonDeserialize(jsonStr: String) = jsonDeserialize(JSONObject(jsonStr))

        /**
         * Extracts an end session response from an intent produced by [.toIntent]. This is
         * used to extract the response from the intent data passed to an activity registered as the
         * handler for [AuthorizationService.performEndSessionRequest].
         */
        @JvmStatic
        fun fromIntent(dataIntent: Intent): EndSessionResponse? {
            if (!containsEndSessionResponse(dataIntent)) return null

            try {
                return jsonDeserialize(dataIntent.getStringExtra(EXTRA_RESPONSE)!!)
            } catch (ex: JSONException) {
                throw IllegalArgumentException("Intent contains malformed auth response", ex)
            }
        }

        @JvmStatic
        fun containsEndSessionResponse(intent: Intent) = intent.hasExtra(EXTRA_RESPONSE)
    }
}
