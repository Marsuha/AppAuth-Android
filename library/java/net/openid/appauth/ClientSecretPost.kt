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
 * Implementation of the client authentication method 'client_secret_post'.
 *
 * @see "OpenID Connect Core 1.0, Section 9
 * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
 */
class ClientSecretPost(private val clientSecret: String) : ClientAuthentication {
    override fun getRequestParameters(clientId: String) = mapOf(
        PARAM_CLIENT_ID to clientId,
        PARAM_CLIENT_SECRET to clientSecret
    )

    override fun getRequestHeaders(clientId: String): Map<String, String>? = null

    companion object {
        /**
         * Name of this authentication method.
         *
         * @see "OpenID Connect Core 1.0, Section 9
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
         */
        const val NAME: String = "client_secret_post"
        const val PARAM_CLIENT_ID: String = "client_id"
        const val PARAM_CLIENT_SECRET: String = "client_secret"
    }
}
