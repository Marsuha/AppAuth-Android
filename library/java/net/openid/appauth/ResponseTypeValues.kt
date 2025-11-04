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
 * The response type values defined by the
 * [OAuth 2.0](https://tools.ietf.org/html/rfc6749) and
 * [OpenID Connect Core 1.0](http://openid.net/specs/openid-connect-core-1_0.html)
 * specifications, used in [authorization][AuthorizationRequest] and
 * [dynamic client registration][RegistrationRequest] requests.
 */
object ResponseTypeValues {
    /**
     * For requesting an authorization code.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    const val CODE: String = "code"

    /**
     * For requesting an access token via an implicit grant.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    const val TOKEN: String = "token"

    /**
     * For requesting an OpenID Conenct ID Token.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    const val ID_TOKEN: String = "id_token"
}
