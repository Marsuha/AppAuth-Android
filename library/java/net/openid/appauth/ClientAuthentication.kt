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
 * An interface for client authentication methods.
 *
 * This interface defines the contract for implementing different client authentication mechanisms
 * as specified in [Section 2.3 of RFC6749](https://tools.ietf.org/html/rfc6749#section-2.3)
 * and [Section 2 of OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication).
 *
 * Implementations of this interface are responsible for providing the necessary headers or
 * parameters to authenticate the client when making requests to the token endpoint.
 *
 * @see ClientSecretBasic
 * @see ClientSecretPost
 * @see NoClientAuthentication
 */
sealed interface ClientAuthentication {
    /**
     * An exception thrown when a client authentication method is not supported by the
     * authorization server. This typically occurs when the server's discovery document
     * does not list the method in its `token_endpoint_auth_methods_supported` metadata.
     *
     * @param unsupportedAuthenticationMethod The name of the unsupported authentication method.
     */
    class UnsupportedAuthenticationMethod(
        unsupportedAuthenticationMethod: String
    ) : Exception("Unsupported client authentication method: $unsupportedAuthenticationMethod")


    /**
     * Constructs any extra parameters necessary to include in the request headers for the client
     * authentication.
     */
    fun getRequestHeaders(clientId: String): Map<String, String>?

    /**
     * Constructs any extra parameters necessary to include in the request body for the client
     * authentication.
     */
    fun getRequestParameters(clientId: String): Map<String, String>?
}
