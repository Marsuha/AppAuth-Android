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
import androidx.core.net.toUri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.openid.appauth.internal.UriSerializer
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
class RegistrationResponse private constructor(
    /**
     * The registration request associated with this response.
     */
    val request: RegistrationRequest,
    /**
     * The registered client identifier.
     *
     * @see "The OAuth 2.0 Authorization Framework
     * @see "The OAuth 2.0 Authorization Framework
     */
    @SerialName(PARAM_CLIENT_ID)
    val clientId: String,
    /**
     * Timestamp of when the client identifier was issued, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @SerialName(PARAM_CLIENT_ID_ISSUED_AT)
    val clientIdIssuedAt: Long?,
    /**
     * The client secret, which is part of the client credentials, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @SerialName(PARAM_CLIENT_SECRET)
    val clientSecret: String?,
    /**
     * Timestamp of when the client credentials expires, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @SerialName(PARAM_CLIENT_SECRET_EXPIRES_AT)
    val clientSecretExpiresAt: Long?,
    /**
     * Client registration access token that can be used for subsequent operations upon the client
     * registration.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @SerialName(PARAM_REGISTRATION_ACCESS_TOKEN)
    val registrationAccessToken: String?,
    /**
     * Location of the client configuration endpoint, if provided.
     *
     * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
     * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
     */
    @SerialName(PARAM_REGISTRATION_CLIENT_URI)
    @Serializable(with = UriSerializer::class)
    val registrationClientUri: Uri?,
    /**
     * Client authentication method to use at the token endpoint, if provided.
     *
     * @see "OpenID Connect Core 1.0, Section 9
     * <https:></https:>//openid.net/specs/openid-connect-core-1_0.html.rfc.section.9>"
     */
    @SerialName(PARAM_TOKEN_ENDPOINT_AUTH_METHOD)
    val tokenEndpointAuthMethod: String?,
    /**
     * Additional, non-standard parameters in the response.
     */
    val additionalParameters: JsonObject = JsonObject(emptyMap())
) {
    /**
     * Determines whether the returned access token has expired.
     */
    val hasClientSecretExpired: Boolean
        get() = hasClientSecretExpired(SystemClock)

    /**
     * The JSON string representation of this registration response.
     */
    val asJsonString get() = Json.encodeToString(this)

    @VisibleForTesting
    val asJsonObject get() = Json.encodeToJsonElement(this).jsonObject

    /**
     * Thrown when a mandatory property is missing from the registration response.
     */
    class MissingArgumentException(
        /**
         * Indicates that the specified mandatory field is missing from the registration response.
         */
        val missingField: String
    ) : Exception("Missing mandatory registration field: $missingField")

    class Builder(private var request: RegistrationRequest) {
        private var clientId: String? = null
        private var clientIdIssuedAt: Long? = null
        private var clientSecret: String? = null
        private var clientSecretExpiresAt: Long? = null
        private var registrationAccessToken: String? = null
        private var registrationClientUri: Uri? = null
        private var tokenEndpointAuthMethod: String? = null

        private var additionalParameters: JsonObject = JsonObject(emptyMap())

        /**
         * Specifies the request associated with this response. Must not be null.
         */
        fun setRequest(request: RegistrationRequest): Builder {
            this@Builder.request = request
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
            this@Builder.clientId = clientId
            return this
        }

        /**
         * Specifies the timestamp for when the client identifier was issued.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setClientIdIssuedAt(clientIdIssuedAt: Long?): Builder {
            this@Builder.clientIdIssuedAt = clientIdIssuedAt
            return this
        }

        /**
         * Specifies the client secret.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setClientSecret(clientSecret: String?): Builder {
            this@Builder.clientSecret = clientSecret
            return this
        }

        /**
         * Specifies the expiration time of the client secret.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setClientSecretExpiresAt(clientSecretExpiresAt: Long?): Builder {
            this@Builder.clientSecretExpiresAt = clientSecretExpiresAt
            return this
        }

        /**
         * Specifies the registration access token.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setRegistrationAccessToken(registrationAccessToken: String?): Builder {
            this@Builder.registrationAccessToken = registrationAccessToken
            return this
        }

        /**
         * Specifies the client authentication method to use at the token endpoint.
         */
        fun setTokenEndpointAuthMethod(tokenEndpointAuthMethod: String?): Builder {
            this@Builder.tokenEndpointAuthMethod = tokenEndpointAuthMethod
            return this
        }

        /**
         * Specifies the client configuration endpoint.
         *
         * @see "OpenID Connect Dynamic Client Registration 1.0, Section 3.2
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html.rfc.section.3.2>"
         */
        fun setRegistrationClientUri(registrationClientUri: Uri?): Builder {
            this@Builder.registrationClientUri = registrationClientUri
            return this
        }

        /**
         * Specifies the additional, non-standard parameters received as part of the response.
         */
        fun setAdditionalParameters(parameters: JsonObject?): Builder {
            additionalParameters = parameters.checkAdditionalParams(BUILT_IN_PARAMS)
            return this
        }

        /**
         * Creates the token response instance.
         */
        fun build() = RegistrationResponse(
            request = request,
            clientId = clientId!!,
            clientIdIssuedAt = clientIdIssuedAt,
            clientSecret = clientSecret,
            clientSecretExpiresAt = clientSecretExpiresAt,
            registrationAccessToken = registrationAccessToken,
            registrationClientUri = registrationClientUri,
            tokenEndpointAuthMethod = tokenEndpointAuthMethod,
            additionalParameters = additionalParameters
        )

        /**
         * Extracts registration response fields from a JSON object.
         *
         * @throws SerializationException if the JSON is malformed or has incorrect value types for fields.
         * @throws MissingArgumentException if the JSON is missing fields required by the
         * specification.
         */
        @Throws(SerializationException::class, MissingArgumentException::class)
        fun fromResponseJson(json: JsonObject): Builder {
            setClientId(json[PARAM_CLIENT_ID]!!.jsonPrimitive.content)
            setClientIdIssuedAt(json[PARAM_CLIENT_ID_ISSUED_AT]?.jsonPrimitive?.long)

            json[PARAM_CLIENT_SECRET]?.jsonPrimitive?.content?.let { secret ->
                val secretAt = json[PARAM_CLIENT_SECRET_EXPIRES_AT]?.jsonPrimitive?.long
                    ?: throw MissingArgumentException(PARAM_CLIENT_SECRET_EXPIRES_AT)

                setClientSecret(secret)
                setClientSecretExpiresAt(secretAt)
            }

            if (json.containsKey(PARAM_REGISTRATION_ACCESS_TOKEN)
                != json.containsKey(PARAM_REGISTRATION_CLIENT_URI)
            ) {
                val missingParameter = if (json.containsKey(PARAM_REGISTRATION_ACCESS_TOKEN)) {
                    PARAM_REGISTRATION_CLIENT_URI
                } else {
                    PARAM_REGISTRATION_ACCESS_TOKEN
                }

                throw MissingArgumentException(missingParameter)
            }

            setRegistrationAccessToken(json[PARAM_REGISTRATION_ACCESS_TOKEN]?.jsonPrimitive?.content)
            setRegistrationClientUri(json[PARAM_REGISTRATION_CLIENT_URI]?.jsonPrimitive?.content?.toUri())
            setTokenEndpointAuthMethod(json[PARAM_TOKEN_ENDPOINT_AUTH_METHOD]?.jsonPrimitive?.content)
            setAdditionalParameters(json.extractAdditionalParams(BUILT_IN_PARAMS))
            return this
        }
    }

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
         * Reads a registration response JSON object received from an authorization server,
         * and associates it with the provided request.
         *
         * @throws SerializationException if the JSON is malformed or missing required fields.
         * @throws MissingArgumentException if the JSON is missing fields required by the specification.
         */
        @VisibleForTesting
        fun buildFromJson(request: RegistrationRequest, json: JsonObject) = Builder(request)
            .fromResponseJson(json)
            .build()

        /**
         * Reads a `RegistrationResponse` from a JSON string. This method is primarily intended for
         * serializing and deserializing `RegistrationResponse` objects for storage; for example,
         * in `SharedPreferences`. It is not intended for parsing registration responses from
         * authorization servers, as it requires all fields of the `RegistrationResponse` to be
         * present in the JSON.
         *
         * @param json The JSON string representation of the `RegistrationResponse`.
         * @return The deserialized `RegistrationResponse`.
         * @throws SerializationException if the JSON string is malformed or missing required fields.
         */
        fun fromJsonString(json: String): RegistrationResponse {
            require(json.isNotEmpty()) { "jsonStr cannot be empty" }
            return Json.decodeFromString<RegistrationResponse>(json)
        }
    }
}
