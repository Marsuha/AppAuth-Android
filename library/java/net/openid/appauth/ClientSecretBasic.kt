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

import android.util.Base64
import net.openid.appauth.internal.UriUtil.formUrlEncodeValue

/**
 * Implementation of the client authentication method 'client_secret_basic'.
 *
 * @see "OpenID Connect Core 1.0, Section 9
 * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
 */
class ClientSecretBasic(private val clientSecret: String) : ClientAuthentication {
    override fun getRequestHeaders(clientId: String): Map<String, String> {
        // From the OAuth2 RFC, client ID and secret should be encoded prior to concatenation and
        // conversion to Base64: https://tools.ietf.org/html/rfc6749#section-2.3.1
        val encodedClientId = formUrlEncodeValue(clientId)
        val encodedClientSecret = formUrlEncodeValue(clientSecret)
        val credentials = "$encodedClientId:$encodedClientSecret"
        val basicAuth = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return mapOf("Authorization" to "Basic $basicAuth")
    }

    override fun getRequestParameters(clientId: String): Map<String, String>? = null

    companion object {
        /**
         * Name of this authentication method.
         *
         * @see "OpenID Connect Core 1.0, Section 9
         * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
         */
        const val NAME: String = "client_secret_basic"
    }
}
