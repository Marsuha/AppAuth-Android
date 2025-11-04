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

/**
 * Implementation of the client authentication method 'none'. This is the default,
 * if no other authentication method is specified when calling
 * [AuthorizationService.performTokenRequest].
 *
 * @see "OpenID Connect Core 1.0, Section 9
 * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
 */
object NoClientAuthentication : ClientAuthentication {
    /**
     * Name of this authentication method.
     */
    @Suppress("unused")
    const val NAME: String = "none"

    /**
     * {@inheritDoc}
     *
     * @return always `null`.
     */
    override fun getRequestHeaders(clientId: String): Map<String, String>? = null

    /**
     * {@inheritDoc}
     *
     * Where no alternative form of client authentication is used, the client_id is simply
     * sent as a client identity assertion.
     */
    override fun getRequestParameters(clientId: String) =
        mapOf(TokenRequest.PARAM_CLIENT_ID to clientId)
}
