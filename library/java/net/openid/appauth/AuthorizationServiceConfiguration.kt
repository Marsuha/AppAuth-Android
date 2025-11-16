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

import android.net.Uri
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import net.openid.appauth.internal.Logger
import net.openid.appauth.internal.UriSerializer
import java.io.IOException
import java.io.InputStream

/**
 * Configuration details required to interact with an authorization service.
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
@Serializable
data class AuthorizationServiceConfiguration(
    /**
     * The authorization service's endpoint.
     */
    @Serializable(with = UriSerializer::class)
    val authorizationEndpoint: Uri,
    /**
     * The authorization service's token exchange and refresh endpoint.
     */
    @Serializable(with = UriSerializer::class)
    val tokenEndpoint: Uri,
    /**
     * The end session service's endpoint;
     */
    @Serializable(with = UriSerializer::class)
    val endSessionEndpoint: Uri? = null,
    /**
     * The authorization service's client registration endpoint.
     */
    @Serializable(with = UriSerializer::class)
    val registrationEndpoint: Uri? = null,
    /**
     * The discovery document describing the service, if it is an OpenID Connect provider.
     */
    val discoveryDoc: AuthorizationServiceDiscovery? = null
) {
    /**
     * Creates an service configuration for an OpenID Connect provider, based on its
     * [discovery document][AuthorizationServiceDiscovery].
     *
     * @param discoveryDoc The OpenID Connect discovery document which describes this service.
     */
    constructor(discoveryDoc: AuthorizationServiceDiscovery) : this(
        discoveryDoc = discoveryDoc,
        authorizationEndpoint = discoveryDoc.authorizationEndpoint,
        tokenEndpoint = checkNotNull(discoveryDoc.tokenEndpoint),
        registrationEndpoint = discoveryDoc.registrationEndpoint,
        endSessionEndpoint = discoveryDoc.endSessionEndpoint
    )

    /**
     * Converts the authorization service configuration to a JSON string for storage or
     * transmission.
     */
    val asJsonString get() = Json.encodeToString(this)

    @VisibleForTesting
    val asJsonObject get() = Json.encodeToJsonElement(this).jsonObject


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

        /**
         * Reads an authorization service configuration from a JSON string, either one produced by
         * [asJsonString] or a JWKS discovery document. If a discovery document is used, then the
         * `authorizationEndpoint` and `tokenEndpoint` are required.
         *
         * @param json The json string to read.
         * @return The authorization service configuration.
         * @throws SerializationException if the provided JSON does not match the expected structure.
         */
        @Throws(SerializationException::class)
        fun fromJsonString(json: String): AuthorizationServiceConfiguration {
            val config = Json.decodeFromString<AuthorizationServiceConfiguration>(json)
            return config.discoveryDoc?.let { AuthorizationServiceConfiguration(it) }
                ?: config
        }

        @Suppress("unused")
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
        @OptIn(ExperimentalSerializationApi::class)
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
                    connection.connect()
                    `is` = connection.inputStream
                    val json = `is`.readString()
                    val discovery = AuthorizationServiceDiscovery.fromJsonString(json)
                    AuthorizationServiceConfiguration(discovery)
                } catch (ex: IOException) {
                    Logger.errorWithStack(ex, "Network error when retrieving discovery document")

                    throw AuthorizationException.fromTemplate(
                        AuthorizationException.GeneralErrors.NETWORK_ERROR,
                        ex
                    )
                } catch (ex: MissingFieldException) {
                    Logger.errorWithStack(ex, "Malformed discovery document")

                    throw AuthorizationException.fromTemplate(
                        AuthorizationException.GeneralErrors.INVALID_DISCOVERY_DOCUMENT,
                        ex
                    )
                } catch (ex: SerializationException) {
                    Logger.errorWithStack(ex, "Error parsing discovery document")

                    throw AuthorizationException.fromTemplate(
                        AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR,
                        ex
                    )
                } finally {
                    `is`.closeQuietly()
                }
            }
        }
    }
}
