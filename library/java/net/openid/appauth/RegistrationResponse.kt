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

import android.net.Uri
import androidx.annotation.VisibleForTesting
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Suppress("unused")
class RegistrationResponse private constructor(
    /**
     * The registration request associated with this response.
     */
    @JvmField val request: RegistrationRequest,
    /**
     * The registered client identifier.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    @JvmField val clientId: String,
    /**
     * Timestamp of when the client identifier was issued, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @JvmField val clientIdIssuedAt: Long?,
    /**
     * The client secret, which is part of the client credentials, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @JvmField val clientSecret: String?,
    /**
     * Timestamp of when the client credentials expires, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @JvmField val clientSecretExpiresAt: Long?,
    /**
     * Client registration access token that can be used for subsequent operations upon the client
     * registration.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @JvmField val registrationAccessToken: String?,
    /**
     * Location of the client configuration endpoint, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @JvmField val registrationClientUri: Uri?,
    /**
     * Client authentication method to use at the token endpoint, if provided.
     *
     * @see "OpenID Connect Core 1.0, Section 9
     * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
     */
    @JvmField val tokenEndpointAuthMethod: String?,
    /**
     * Additional, non-standard parameters in the response.
     */
    val additionalParameters: Map<String, String>
) {
    /**
     * Thrown when a mandatory property is missing from the registration response.
     */
    class MissingArgumentException(
        /**
         * Indicates that the specified mandatory field is missing from the registration response.
         */
        val missingField: String
    ) : Exception("Missing mandatory registration field: $missingField")

    class Builder(request: RegistrationRequest) {
        private var mRequest: RegistrationRequest = request
        private var mClientId: String? = null

        private var mClientIdIssuedAt: Long? = null
        private var mClientSecret: String? = null
        private var mClientSecretExpiresAt: Long? = null
        private var mRegistrationAccessToken: String? = null
        private var mRegistrationClientUri: Uri? = null
        private var mTokenEndpointAuthMethod: String? = null

        private var mAdditionalParameters: Map<String, String> = emptyMap()

        init {
            require(!mClientId.isNullOrEmpty()) { "client ID cannot be null or empty" }
        }

        /**
         * Specifies the request associated with this response. Must not be null.
         */
        fun setRequest(request: RegistrationRequest): Builder {
            mRequest = request
            return this
        }

        /**
         * Specifies the client identifier.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework
         */
        fun setClientId(clientId: String): Builder {
            require(clientId.isNotEmpty()) { "client ID cannot be empty" }
            mClientId = clientId
            return this
        }

        /**
         * Specifies the timestamp for when the client identifier was issued.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setClientIdIssuedAt(clientIdIssuedAt: Long?): Builder {
            mClientIdIssuedAt = clientIdIssuedAt
            return this
        }

        /**
         * Specifies the client secret.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setClientSecret(clientSecret: String?): Builder {
            mClientSecret = clientSecret
            return this
        }

        /**
         * Specifies the expiration time of the client secret.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setClientSecretExpiresAt(clientSecretExpiresAt: Long?): Builder {
            mClientSecretExpiresAt = clientSecretExpiresAt
            return this
        }

        /**
         * Specifies the registration access token.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setRegistrationAccessToken(registrationAccessToken: String?): Builder {
            mRegistrationAccessToken = registrationAccessToken
            return this
        }

        /**
         * Specifies the client authentication method to use at the token endpoint.
         */
        fun setTokenEndpointAuthMethod(tokenEndpointAuthMethod: String?): Builder {
            mTokenEndpointAuthMethod = tokenEndpointAuthMethod
            return this
        }

        /**
         * Specifies the client configuration endpoint.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setRegistrationClientUri(registrationClientUri: Uri?): Builder {
            mRegistrationClientUri = registrationClientUri
            return this
        }

        /**
         * Specifies the additional, non-standard parameters received as part of the response.
         */
        fun setAdditionalParameters(additionalParameters: Map<String, String>?): Builder {
            mAdditionalParameters = additionalParameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Creates the token response instance.
         */
        fun build() = RegistrationResponse(
            request = mRequest,
            clientId = mClientId!!,
            clientIdIssuedAt = mClientIdIssuedAt,
            clientSecret = mClientSecret,
            clientSecretExpiresAt = mClientSecretExpiresAt,
            registrationAccessToken = mRegistrationAccessToken,
            registrationClientUri = mRegistrationClientUri,
            tokenEndpointAuthMethod = mTokenEndpointAuthMethod,
            additionalParameters = mAdditionalParameters
        )

        /**
         * Extracts registration response fields from a JSON string.
         *
         * @throws JSONException if the JSON is malformed or has incorrect value types for fields.
         * @throws MissingArgumentException if the JSON is missing fields required by the
         * specification.
         */
        @Throws(JSONException::class, MissingArgumentException::class)
        fun fromResponseJsonString(jsonStr: String): Builder {
            require(jsonStr.isNotEmpty()) { "json cannot be empty" }
            return fromResponseJson(JSONObject(jsonStr))
        }

        /**
         * Extracts token response fields from a JSON object.
         *
         * @throws JSONException if the JSON is malformed or has incorrect value types for fields.
         * @throws MissingArgumentException if the JSON is missing fields required by the
         * specification.
         */
        @Throws(JSONException::class, MissingArgumentException::class)
        fun fromResponseJson(json: JSONObject): Builder {
            setClientId(json.getString(PARAM_CLIENT_ID))
            setClientIdIssuedAt(json.getLongIfDefined(PARAM_CLIENT_ID_ISSUED_AT))

            if (json.has(PARAM_CLIENT_SECRET)) {
                if (!json.has(PARAM_CLIENT_SECRET_EXPIRES_AT)) {
                    /*
                     * From OpenID Connect Dynamic Client Registration, Section 3.2:
                     * client_secret_expires_at: "REQUIRED if client_secret is issued"
                     */
                    throw MissingArgumentException(PARAM_CLIENT_SECRET_EXPIRES_AT)
                }

                setClientSecret(json.getString(PARAM_CLIENT_SECRET))
                setClientSecretExpiresAt(json.getLong(PARAM_CLIENT_SECRET_EXPIRES_AT))
            }

            if (json.has(PARAM_REGISTRATION_ACCESS_TOKEN)
                != json.has(PARAM_REGISTRATION_CLIENT_URI)
            ) {
                /*
                 * From OpenID Connect Dynamic Client Registration, Section 3.2:
                 * "Implementations MUST either return both a Client Configuration Endpoint and a
                 * Registration Access Token or neither of them."
                 */
                val missingParameter: String = if (json.has(PARAM_REGISTRATION_ACCESS_TOKEN)) {
                    PARAM_REGISTRATION_CLIENT_URI
                } else {
                    PARAM_REGISTRATION_ACCESS_TOKEN
                }

                throw MissingArgumentException(missingParameter)
            }

            setRegistrationAccessToken(json.getStringIfDefined(PARAM_REGISTRATION_ACCESS_TOKEN))
            setRegistrationClientUri(json.getUriIfDefined(PARAM_REGISTRATION_CLIENT_URI))
            setTokenEndpointAuthMethod(json.getStringIfDefined(PARAM_TOKEN_ENDPOINT_AUTH_METHOD))
            setAdditionalParameters(json.extractAdditionalParams(BUILT_IN_PARAMS))
            return this
        }
    }

    /**
     * Produces a JSON representation of the registration response for persistent storage or
     * local transmission (e.g. between activities).
     */
    fun jsonSerialize() = JSONObject().apply {
        put(KEY_REQUEST, request.jsonSerialize())
        put(PARAM_CLIENT_ID, clientId)
        clientIdIssuedAt?.let { put(PARAM_CLIENT_ID_ISSUED_AT, it) }
        clientSecret?.let { put(PARAM_CLIENT_SECRET, it) }
        clientSecretExpiresAt?.let { put(PARAM_CLIENT_SECRET_EXPIRES_AT, it) }
        registrationAccessToken?.let { put(PARAM_REGISTRATION_ACCESS_TOKEN, it) }
        registrationClientUri?.let { put(PARAM_REGISTRATION_CLIENT_URI, it.toString()) }
        tokenEndpointAuthMethod?.let { put(PARAM_TOKEN_ENDPOINT_AUTH_METHOD, it) }
        put(KEY_ADDITIONAL_PARAMETERS, additionalParameters.toJsonObject())
    }

    /**
     * Produces a JSON string representation of the registration response for persistent storage or
     * local transmission (e.g. between activities). This method is just a convenience wrapper
     * for [.jsonSerialize], converting the JSON object to its string form.
     */
    fun jsonSerializeString() = jsonSerialize().toString()

    /**
     * Determines whether the returned access token has expired.
     */
    val hasClientSecretExpired: Boolean
        get() = hasClientSecretExpired(SystemClock)

    @VisibleForTesting
    fun hasClientSecretExpired(clock: Clock): Boolean {
        val now = TimeUnit.MILLISECONDS.toSeconds(clock.currentTimeMillis)
        return clientSecretExpiresAt != null && now > clientSecretExpiresAt
    }

    companion object {
        const val PARAM_CLIENT_ID: String = "client_id"
        const val PARAM_CLIENT_SECRET: String = "client_secret"
        const val PARAM_CLIENT_SECRET_EXPIRES_AT: String = "client_secret_expires_at"
        const val PARAM_REGISTRATION_ACCESS_TOKEN: String = "registration_access_token"
        const val PARAM_REGISTRATION_CLIENT_URI: String = "registration_client_uri"
        const val PARAM_CLIENT_ID_ISSUED_AT: String = "client_id_issued_at"
        const val PARAM_TOKEN_ENDPOINT_AUTH_METHOD: String = "token_endpoint_auth_method"

        const val KEY_REQUEST: String = "request"
        const val KEY_ADDITIONAL_PARAMETERS: String = "additionalParameters"

        private val BUILT_IN_PARAMS: Set<String> = setOf(
            PARAM_CLIENT_ID,
            PARAM_CLIENT_SECRET,
            PARAM_CLIENT_SECRET_EXPIRES_AT,
            PARAM_REGISTRATION_ACCESS_TOKEN,
            PARAM_REGISTRATION_CLIENT_URI,
            PARAM_CLIENT_ID_ISSUED_AT,
            PARAM_TOKEN_ENDPOINT_AUTH_METHOD
        )

        /**
         * Reads a registration response JSON string received from an authorization server,
         * and associates it with the provided request.
         *
         * @throws JSONException if the JSON is malformed or missing required fields.
         * @throws MissingArgumentException if the JSON is missing fields required by the specification.
         */
        @Throws(JSONException::class, MissingArgumentException::class)
        fun fromJson(
            request: RegistrationRequest,
            jsonStr: String
        ): RegistrationResponse {
            require(jsonStr.isNotEmpty()) { "jsonStr cannot be empty" }
            return fromJson(request, JSONObject(jsonStr))
        }

        /**
         * Reads a registration response JSON object received from an authorization server,
         * and associates it with the provided request.
         *
         * @throws JSONException if the JSON is malformed or missing required fields.
         * @throws MissingArgumentException if the JSON is missing fields required by the specification.
         */
        @JvmStatic
        @Throws(JSONException::class, MissingArgumentException::class)
        fun fromJson(
            request: RegistrationRequest,
            json: JSONObject
        ) = Builder(request)
            .fromResponseJson(json)
            .build()

        /**
         * Reads a registration response from a JSON string representation produced by
         * [.jsonSerialize].
         *
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun jsonDeserialize(json: JSONObject): RegistrationResponse {
            require(json.has(KEY_REQUEST)) { "registration request not found in JSON" }

            return RegistrationResponse(
                request = RegistrationRequest.jsonDeserialize(json.getJSONObject(KEY_REQUEST)),
                clientId = json.getString(PARAM_CLIENT_ID),
                clientIdIssuedAt = json.getLongIfDefined(PARAM_CLIENT_ID_ISSUED_AT),
                clientSecret = json.getStringIfDefined(PARAM_CLIENT_SECRET),
                clientSecretExpiresAt = json.getLongIfDefined(PARAM_CLIENT_SECRET_EXPIRES_AT),
                registrationAccessToken = json.getStringIfDefined(PARAM_REGISTRATION_ACCESS_TOKEN),
                registrationClientUri = json.getUriIfDefined(PARAM_REGISTRATION_CLIENT_URI),
                tokenEndpointAuthMethod = json.getStringIfDefined(PARAM_TOKEN_ENDPOINT_AUTH_METHOD),
                additionalParameters = json.getStringMap(KEY_ADDITIONAL_PARAMETERS)
            )
        }

        /**
         * Reads a registration response from a JSON string representation produced by
         * [.jsonSerializeString]. This method is just a convenience wrapper for
         * [.jsonDeserialize], converting the JSON string to its JSON object form.
         *
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @Throws(JSONException::class)
        fun jsonDeserialize(jsonStr: String): RegistrationResponse {
            require(jsonStr.isNotEmpty()) { "jsonStr cannot be empty" }
            return jsonDeserialize(JSONObject(jsonStr))
        }
    }
}
