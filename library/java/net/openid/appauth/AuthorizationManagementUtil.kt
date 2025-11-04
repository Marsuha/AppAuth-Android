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

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Base64
import net.openid.appauth.AuthorizationManagementUtil.RequestType.AUTHORIZATION
import net.openid.appauth.AuthorizationManagementUtil.RequestType.END_SESSION
import net.openid.appauth.AuthorizationResponse.Companion.containsAuthorizationResponse
import net.openid.appauth.EndSessionResponse.Companion.containsEndSessionResponse
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom

/**
 * An internal utility class for storing and retrieving authorization and end session requests
 * and responses.
 */
internal object AuthorizationManagementUtil {
    private const val STATE_LENGTH = 16

    enum class RequestType(val type: String) {
        AUTHORIZATION("authorization"),
        END_SESSION("end_session")
    }

    /**
     * Generates a random, base64-encoded string for use as a state parameter in an
     * authorization or end session request. This is used to associate a client session with the
     * request and to mitigate CSRF attacks.
     */
    val randomState: String
        get() {
            val sr = SecureRandom()
            val random = ByteArray(STATE_LENGTH)
            sr.nextBytes(random)
            return Base64.encodeToString(
                random,
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
            )
        }

    /**
     * Returns the request type for a given [AuthorizationManagementRequest].
     */
    fun requestTypeFor(request: AuthorizationManagementRequest) = when (request) {
        is AuthorizationRequest -> AUTHORIZATION
        is EndSessionRequest -> END_SESSION
    }

    /**
     * Reads an authorization request from a JSON string representation produced by either
     * [AuthorizationRequest.jsonSerialize] or [EndSessionRequest.jsonSerialize].
     * @throws JSONException if the provided JSON does not match the expected structure.
     */
    @Throws(JSONException::class)
    fun requestFrom(jsonStr: String, type: RequestType): AuthorizationManagementRequest {
        val json = JSONObject(jsonStr)
        return when (type) {
            AUTHORIZATION -> AuthorizationRequest.jsonDeserialize(json)
            END_SESSION -> EndSessionRequest.jsonDeserialize(json)
        }
    }

    /**
     * Builds an AuthorizationManagementResponse from
     * [AuthorizationManagementRequest] and [Uri]
     */
    @SuppressLint("VisibleForTests")
    fun responseWith(request: AuthorizationManagementRequest, uri: Uri) = when (request) {
        is AuthorizationRequest -> {
            AuthorizationResponse.Builder(request)
                .fromUri(uri)
                .build()
        }

        is EndSessionRequest -> {
            EndSessionResponse.Builder(request)
                .fromUri(uri)
                .build()
        }
    }

    /**
     * Extracts response from an intent produced by [.toIntent]. This is
     * used to extract the response from the intent data passed to an activity registered as the
     * handler for [AuthorizationService.performEndSessionRequest]
     * or [AuthorizationService.performAuthorizationRequest].
     */
    fun responseFrom(dataIntent: Intent): AuthorizationManagementResponse {
        if (containsEndSessionResponse(dataIntent)) {
            return EndSessionResponse.fromIntent(dataIntent)!!
        }

        if (containsAuthorizationResponse(dataIntent)) {
            return AuthorizationResponse.fromIntent(dataIntent)!!
        }

        throw IllegalArgumentException("Malformed intent")
    }
}
