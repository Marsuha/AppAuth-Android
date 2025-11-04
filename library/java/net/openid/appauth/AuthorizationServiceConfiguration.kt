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
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import net.openid.appauth.internal.Logger
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

/**
 * Configuration details required to interact with an authorization service.
 */
class AuthorizationServiceConfiguration {
    /**
     * The authorization service's endpoint.
     */
    @JvmField
    val authorizationEndpoint: Uri

    /**
     * The authorization service's token exchange and refresh endpoint.
     */
    @JvmField
    val tokenEndpoint: Uri

    /**
     * The end session service's endpoint;
     */
    @JvmField
    val endSessionEndpoint: Uri?

    /**
     * The authorization service's client registration endpoint.
     */
    @JvmField
    val registrationEndpoint: Uri?


    /**
     * The discovery document describing the service, if it is an OpenID Connect provider.
     */
    @JvmField
    val discoveryDoc: AuthorizationServiceDiscovery?

    /**
     * Creates a service configuration for a basic OAuth2 provider.
     * @param authorizationEndpoint The
     * [authorization endpoint URI](https://tools.ietf.org/html/rfc6749#section-3.1)
     * for the service.
     * @param tokenEndpoint The
     * [token endpoint URI](https://tools.ietf.org/html/rfc6749#section-3.2)
     * for the service.
     * @param registrationEndpoint The optional
     * [client registration endpoint URI](https://tools.ietf.org/html/rfc7591#section-3)
     * @param endSessionEndpoint The optional
     * [end session endpoint URI](https://tools.ietf.org/html/rfc6749#section-2.2)
     * for the service.
     */
    @JvmOverloads
    constructor(
        authorizationEndpoint: Uri,
        tokenEndpoint: Uri,
        registrationEndpoint: Uri? = null,
        endSessionEndpoint: Uri? = null
    ) {
        this.authorizationEndpoint = authorizationEndpoint
        this.tokenEndpoint = tokenEndpoint
        this.registrationEndpoint = registrationEndpoint
        this.endSessionEndpoint = endSessionEndpoint
        this.discoveryDoc = null
    }

    /**
     * Creates an service configuration for an OpenID Connect provider, based on its
     * [discovery document][AuthorizationServiceDiscovery].
     *
     * @param discoveryDoc The OpenID Connect discovery document which describes this service.
     */
    constructor(discoveryDoc: AuthorizationServiceDiscovery) {
        this.discoveryDoc = discoveryDoc
        this.authorizationEndpoint = discoveryDoc.authorizationEndpoint
        this.tokenEndpoint = checkNotNull(discoveryDoc.tokenEndpoint)
        this.registrationEndpoint = discoveryDoc.registrationEndpoint
        this.endSessionEndpoint = discoveryDoc.endSessionEndpoint
    }

    /**
     * Converts the authorization service configuration to JSON for storage or transmission.
     */
    @SuppressLint("VisibleForTests")
    fun toJson() = JSONObject().apply {
        put(KEY_AUTHORIZATION_ENDPOINT, authorizationEndpoint.toString())
        put(KEY_TOKEN_ENDPOINT, tokenEndpoint.toString())
        registrationEndpoint?.let { put(KEY_REGISTRATION_ENDPOINT, it.toString()) }
        endSessionEndpoint?.let { put(KEY_END_SESSION_ENDPOINT, it.toString()) }
        discoveryDoc?.let { put(KEY_DISCOVERY_DOC, it.discoveryDoc) }
    }

    /**
     * Converts the authorization service configuration to a JSON string for storage or
     * transmission.
     */
    fun toJsonString() = toJson().toString()

    companion object {
        /**
         * The standard base path for well-known resources on domains.
         *
         * @see "Defining Well-Known Uniform Resource Identifiers
         */
        const val WELL_KNOWN_PATH: String = ".well-known"

        /**
         * The standard resource under [.well-known][.WELL_KNOWN_PATH] at which an OpenID Connect
         * discovery document can be found under an issuer's base URI.
         *
         * @see "OpenID Connect discovery 1.0
         * <https:></https:>//openid.net/specs/openid-connect-discovery-1_0.html>"
         */
        const val OPENID_CONFIGURATION_RESOURCE: String = "openid-configuration"

        private const val KEY_AUTHORIZATION_ENDPOINT = "authorizationEndpoint"
        private const val KEY_TOKEN_ENDPOINT = "tokenEndpoint"
        private const val KEY_REGISTRATION_ENDPOINT = "registrationEndpoint"
        private const val KEY_DISCOVERY_DOC = "discoveryDoc"
        private const val KEY_END_SESSION_ENDPOINT = "endSessionEndpoint"

        /**
         * Reads an Authorization service configuration from a JSON representation produced by the
         * [.toJson] method or some other equivalent producer.
         *
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject): AuthorizationServiceConfiguration {
            if (json.has(KEY_DISCOVERY_DOC)) {
                try {
                    val discoveryDoc = AuthorizationServiceDiscovery(
                        discoveryDoc = json.optJSONObject(KEY_DISCOVERY_DOC)!!
                    )

                    return AuthorizationServiceConfiguration(discoveryDoc)
                } catch (ex: AuthorizationServiceDiscovery.MissingArgumentException) {
                    throw JSONException("Missing required field in discovery doc: ${ex.missingField}")
                }
            } else {
                require(json.has(KEY_AUTHORIZATION_ENDPOINT)) { "missing authorizationEndpoint" }
                require(json.has(KEY_TOKEN_ENDPOINT)) { "missing tokenEndpoint" }
                return AuthorizationServiceConfiguration(
                    authorizationEndpoint = json.getUri(KEY_AUTHORIZATION_ENDPOINT),
                    tokenEndpoint = json.getUri(KEY_TOKEN_ENDPOINT),
                    registrationEndpoint = json.getUriIfDefined(KEY_REGISTRATION_ENDPOINT),
                    endSessionEndpoint = json.getUriIfDefined(KEY_END_SESSION_ENDPOINT)
                )
            }
        }

        /**
         * Reads an Authorization service configuration from a JSON representation produced by the
         * [.toJson] method or some other equivalent producer.
         *
         * @throws JSONException if the provided JSON does not match the expected structure.
         */
        @Throws(JSONException::class)
        fun fromJson(jsonStr: String) = fromJson(JSONObject(jsonStr))

        @JvmStatic
        suspend fun fetchFromIssuer(
            openIdConnectIssuerUri: Uri,
            connectionBuilder: ConnectionBuilder = DefaultConnectionBuilder
        ) = fetchFromUrl(
            buildConfigurationUriFromIssuer(openIdConnectIssuerUri),
            connectionBuilder
        )

        @JvmStatic
        fun buildConfigurationUriFromIssuer(openIdConnectIssuerUri: Uri): Uri {
            return openIdConnectIssuerUri.buildUpon()
                .appendPath(WELL_KNOWN_PATH)
                .appendPath(OPENID_CONFIGURATION_RESOURCE)
                .build()
        }

        /**
         * Fetch a AuthorizationServiceConfiguration from an OpenID Connect discovery URI.
         *
         * @param openIdConnectDiscoveryUri The OpenID Connect discovery URI
         * @param connectionBuilder The connection builder that is used to establish a connection
         * to the resource server, [default connection builder][DefaultConnectionBuilder]
         * is used by default
         * @return AuthorizationServiceConfiguration
         * @throws AuthorizationException
         *
         * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">
         *     OpenID Connect discovery 1.0</a>
         */
        @JvmStatic
        suspend fun fetchFromUrl(
            openIdConnectDiscoveryUri: Uri,
            connectionBuilder: ConnectionBuilder = DefaultConnectionBuilder
        ): AuthorizationServiceConfiguration {
            var `is`: InputStream? = null

            return withContext(Dispatchers.IO) {
                try {
                    val connection = connectionBuilder.openConnection(openIdConnectDiscoveryUri)
                    connection.requestMethod = "GET"
                    connection.doInput = true
                    //connection.connect()
                    `is` = connection.inputStream
                    val json = JSONObject(`is`.readString())
                    val discovery = AuthorizationServiceDiscovery(json)
                    AuthorizationServiceConfiguration(discovery)
                } catch (ex: IOException) {
                    Logger.errorWithStack(ex, "Network error when retrieving discovery document")

                    throw AuthorizationException.fromTemplate(
                        AuthorizationException.GeneralErrors.NETWORK_ERROR,
                        ex
                    )
                } catch (ex: JSONException) {
                    Logger.errorWithStack(ex, "Error parsing discovery document")

                    throw AuthorizationException.fromTemplate(
                        AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR,
                        ex
                    )
                } catch (ex: AuthorizationServiceDiscovery.MissingArgumentException) {
                    Logger.errorWithStack(ex, "Malformed discovery document")

                    throw AuthorizationException.fromTemplate(
                        AuthorizationException.GeneralErrors.INVALID_DISCOVERY_DOCUMENT,
                        ex
                    )
                } finally {
                    `is`.closeQuietly()
                }
            }
        }
    }
}
