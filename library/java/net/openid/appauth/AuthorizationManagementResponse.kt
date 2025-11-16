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

/**
 * A base response for session management models
 * [AuthorizationResponse] and [EndSessionResponse]
 */
sealed interface AuthorizationManagementResponse {
    /**
     * The `state` parameter that was sent in the `AuthorizationRequest`.
     *
     * This is used to protect against Cross-Site Request Forgery (CSRF) attacks. The client
     * should verify that this value matches the one sent in the original request.
     *
     * @see "The OAuth 2.0 Authorization Framework, Section 10.12"
     * @see "https://tools.ietf.org/html/rfc6749#section-10.12"
     */
    val state: String?

    /**
     * A convenience property that returns the JSON serialization of this response as a string.
     */
    val asJsonString: String

    /**
     * Creates an intent containing the serializable form of the response.
     */
    fun toIntent(): Intent
}
