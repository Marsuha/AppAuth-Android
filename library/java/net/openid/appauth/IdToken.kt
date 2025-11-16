/*
 * Copyright 2018 The AppAuth for Android Authors. All Rights Reserved.
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
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.openid.appauth.AuthorizationException.Companion.fromTemplate
import net.openid.appauth.internal.AudienceSerializer
import kotlin.math.abs

/**
 * An OpenID Connect ID Token. Contains claims about the authentication of an End-User by an
 * Authorization Server. Supports parsing ID Tokens from JWT Compact Serializations and validation
 * according to the OpenID Connect specification.
 *
 * @see "OpenID Connect Core ID Token, Section 2
 * <http:></http:>//openid.net/specs/openid-connect-core-1_0.html.IDToken>"
 *
 * @see "OpenID Connect Core ID Token Validation, Section 3.1.3.7
 * <http:></http:>//openid.net/specs/openid-connect-core-1_0.html.IDTokenValidation>"
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class IdToken(
    /**
     * Issuer Identifier for the Issuer of the response.
     */
    @SerialName(KEY_ISSUER)
    val issuer: String,
    /**
     * Subject Identifier. A locally unique and never reassigned identifier within the Issuer
     * for the End-User.
     */
    @SerialName(KEY_SUBJECT)
    val subject: String,
    /**
     * Audience(s) that this ID Token is intended for.
     */
    @SerialName(KEY_AUDIENCE)
    @Serializable(with = AudienceSerializer::class)
    val audience: List<String>,
    /**
     * Expiration time on or after which the ID Token MUST NOT be accepted for processing.
     */
    @SerialName(KEY_EXPIRATION)
    val expiration: Long,
    /**
     * Time at which the JWT was issued.
     */
    @SerialName(KEY_ISSUED_AT)
    val issuedAt: Long,
    /**
     * String value used to associate a Client session with an ID Token,
     * and to mitigate replay attacks.
     */
    @SerialName(KEY_NONCE)
    val nonce: String? = null,
    /**
     * Authorized party - the party to which the ID Token was issued.
     * If present, it MUST contain the OAuth 2.0 Client ID of this party.
     */
    @SerialName(KEY_AUTHORIZED_PARTY)
    val authorizedParty: String? = null,
    /**
     * Additional claims present in this ID Token.
     */
    val additionalClaims: JsonObject = emptyJsonObject()
) {
    @VisibleForTesting
    @Throws(AuthorizationException::class)
    fun validate(tokenRequest: TokenRequest, clock: Clock) {
        validate(tokenRequest, clock, false)
    }

    @Throws(AuthorizationException::class)
    fun validate(
        tokenRequest: TokenRequest,
        clock: Clock,
        skipIssuerHttpsCheck: Boolean
    ) {
        // OpenID Connect Core Section 3.1.3.7. rule #1
        // Not enforced: AppAuth does not support JWT encryption.

        // OpenID Connect Core Section 3.1.3.7. rule #2
        // Validates that the issuer in the ID Token matches that of the discovery document.

        tokenRequest.configuration.discoveryDoc?.let { discoveryDoc ->
            val expectedIssuer = discoveryDoc.issuer
            if (issuer != expectedIssuer) {
                throw fromTemplate(
                    AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                    IdTokenException("Issuer mismatch")
                )
            }

            // OpenID Connect Core Section 2.
            // The iss value is a case sensitive URL using the https scheme that contains scheme,
            // host, and optionally, port number and path components and no query or fragment
            // components.
            val issuerUri = issuer.toUri()

            if (!skipIssuerHttpsCheck && issuerUri.scheme != "https") {
                throw fromTemplate(
                    AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                    IdTokenException("Issuer must be an https URL")
                )
            }

            if (issuerUri.host.isNullOrEmpty()) {
                throw fromTemplate(
                    AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                    IdTokenException("Issuer host can not be empty")
                )
            }

            if (issuerUri.fragment != null || issuerUri.queryParameterNames.isNotEmpty()) {
                throw fromTemplate(
                    AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                    IdTokenException(
                        "Issuer URL should not contain query parameters or fragment components"
                    )
                )
            }
        }


        // OpenID Connect Core Section 3.1.3.7. rule #3 & Section 2 azp Claim
        // Validates that the aud (audience) Claim contains the client ID, or that the azp
        // (authorized party) Claim matches the client ID.
        val clientId = tokenRequest.clientId
        if (!this.audience.contains(clientId) && clientId != this.authorizedParty) {
            throw fromTemplate(
                AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                IdTokenException("Audience mismatch")
            )
        }

        // OpenID Connect Core Section 3.1.3.7. rules #4 & #5
        // Not enforced.

        // OpenID Connect Core Section 3.1.3.7. rule #6
        // As noted above, AppAuth only supports the code flow which results in direct
        // communication of the ID Token from the Token Endpoint to the Client, and we are
        // exercising the option to use TLS server validation instead of checking the token
        // signature. Users may additionally check the token signature should they wish.

        // OpenID Connect Core Section 3.1.3.7. rules #7 & #8
        // Not enforced. See rule #6.

        // OpenID Connect Core Section 3.1.3.7. rule #9
        // Validates that the current time is before the expiry time.
        val nowInSeconds: Long = clock.currentTimeMillis / MILLIS_PER_SECOND
        if (nowInSeconds > this.expiration) {
            throw fromTemplate(
                AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                IdTokenException("ID Token expired")
            )
        }

        // OpenID Connect Core Section 3.1.3.7. rule #10
        // Validates that the issued at time is not more than +/- 10 minutes on the current
        // time.
        if (abs(nowInSeconds - this.issuedAt) > TEN_MINUTES_IN_SECONDS) {
            throw fromTemplate(
                AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                IdTokenException(
                    "Issued at time is more than 10 minutes "
                            + "before or after the current time"
                )
            )
        }

        // Only relevant for the authorization_code response type
        if (GrantTypeValues.AUTHORIZATION_CODE == tokenRequest.grantType) {
            // OpenID Connect Core Section 3.1.3.7. rule #11
            // Validates the nonce.
            val expectedNonce = tokenRequest.nonce
            if (nonce.toString() != expectedNonce.toString()) {
                throw fromTemplate(
                    AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                    IdTokenException("Nonce mismatch")
                )
            }
        }

        // OpenID Connect Core Section 3.1.3.7. rules #12
        // ACR is not directly supported by AppAuth.

        // OpenID Connect Core Section 3.1.3.7. rules #13
        // max_age is not directly supported by AppAuth.
    }

    internal class IdTokenException(message: String) : Exception(message)

    companion object {
        private const val KEY_ISSUER = "iss"
        private const val KEY_SUBJECT = "sub"
        private const val KEY_AUDIENCE = "aud"
        private const val KEY_EXPIRATION = "exp"
        private const val KEY_ISSUED_AT = "iat"
        private const val KEY_NONCE = "nonce"
        private const val KEY_AUTHORIZED_PARTY = "azp"
        private const val MILLIS_PER_SECOND = 1000L
        private const val TEN_MINUTES_IN_SECONDS = 600L

        private val BUILT_IN_CLAIMS: Set<String> = setOf(
            KEY_ISSUER,
            KEY_SUBJECT,
            KEY_AUDIENCE,
            KEY_EXPIRATION,
            KEY_ISSUED_AT,
            KEY_NONCE,
            KEY_AUTHORIZED_PARTY
        )

        private fun String.decodeBase64(): String {
            val decodedSection = Base64.decode(this, Base64.URL_SAFE)
            return String(decodedSection)
        }

        /**
         * Parses an ID token from its compact JWT representation.
         *
         * @param token The JWT compact serialization representation of the ID token.
         * @return The parsed ID token.
         * @throws SerializationException if the token is not a structurally valid JWT.
         * @throws IdTokenException if the token is missing required claims.
         */
        @JvmStatic
        @Throws(SerializationException::class, IdTokenException::class)
        fun from(token: String): IdToken {
            val sections = token.split(".").dropLastWhile { it.isEmpty() }

            if (sections.size <= 1) {
                throw IdTokenException("ID token must have both header and claims section")
            }

            // We ignore header contents, but parse it to check that it is structurally valid JSON
            //json.decodeFromString<JwtHeader>(sections[0].decodeBase64())
            Json.parseToJsonElement(sections[0].decodeBase64())
            val claimsJsonString = sections[1].decodeBase64()
            val claims = Json.decodeFromString<IdToken>(claimsJsonString)
            val additionalClaims = Json.parseToJsonElement(claimsJsonString).jsonObject
                .filterKeys { it !in BUILT_IN_CLAIMS }

            return claims.copy(additionalClaims = JsonObject(additionalClaims))
        }
    }
}