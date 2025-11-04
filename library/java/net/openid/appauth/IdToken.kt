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
import net.openid.appauth.AuthorizationException.Companion.fromTemplate
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs
import androidx.core.net.toUri

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
class IdToken internal constructor(
    /**
     * Issuer Identifier for the Issuer of the response.
     */
    @JvmField val issuer: String,
    /**
     * Subject Identifier. A locally unique and never reassigned identifier within the Issuer
     * for the End-User.
     */
    @JvmField val subject: String,
    /**
     * Audience(s) that this ID Token is intended for.
     */
    @JvmField val audience: List<String>,
    /**
     * Expiration time on or after which the ID Token MUST NOT be accepted for processing.
     */
    val expiration: Long,
    /**
     * Time at which the JWT was issued.
     */
    val issuedAt: Long,
    /**
     * String value used to associate a Client session with an ID Token,
     * and to mitigate replay attacks.
     */
    @JvmField val nonce: String?,
    /**
     * Authorized party - the party to which the ID Token was issued.
     * If present, it MUST contain the OAuth 2.0 Client ID of this party.
     */
    val authorizedParty: String?,
    /**
     * Additional claims present in this ID Token.
     */
    @JvmField val additionalClaims: Map<String, Any>
) {
    @VisibleForTesting
    internal constructor(
        issuer: String,
        subject: String,
        audience: List<String>,
        expiration: Long,
        issuedAt: Long
    ) : this(
        issuer,
        subject,
        audience,
        expiration,
        issuedAt,
        null,
        null,
        emptyMap()
    )

    @VisibleForTesting
    internal constructor(
        issuer: String,
        subject: String,
        audience: List<String>,
        expiration: Long,
        issuedAt: Long,
        nonce: String?,
        authorizedParty: String?
    ) : this(
        issuer, subject, audience, expiration, issuedAt,
        nonce, authorizedParty, emptyMap()
    )

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
            if (this.issuer != expectedIssuer) {
                throw fromTemplate(
                    AuthorizationException.GeneralErrors.ID_TOKEN_VALIDATION_ERROR,
                    IdTokenException("Issuer mismatch")
                )
            }

            // OpenID Connect Core Section 2.
            // The iss value is a case sensitive URL using the https scheme that contains scheme,
            // host, and optionally, port number and path components and no query or fragment
            // components.
            val issuerUri = this.issuer.toUri()

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
            if (nonce.toString() == expectedNonce.toString()) {
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

        @Throws(JSONException::class)
        private fun parseJwtSection(section: String): JSONObject {
            val decodedSection = Base64.decode(section, Base64.URL_SAFE)
            val jsonString = String(decodedSection)
            return JSONObject(jsonString)
        }

        @JvmStatic
        @Throws(JSONException::class, IdTokenException::class)
        fun from(token: String): IdToken {
            val sections = token.split("\\.").dropLastWhile { it.isEmpty() }

            if (sections.size <= 1) {
                throw IdTokenException("ID token must have both header and claims section")
            }

            // We ignore header contents, but parse it to check that it is structurally valid JSON
            parseJwtSection(sections[0])
            val claims: JSONObject = parseJwtSection(sections[1])

            val issuer: String = claims.getString(KEY_ISSUER)
            val subject: String = claims.getString(KEY_SUBJECT)

            val audience: List<String> = try {
                claims.getStringList(KEY_AUDIENCE)
            } catch (_: JSONException) {
                listOf(claims.getString(KEY_AUDIENCE))
            }

            val expiration = claims.getLong(KEY_EXPIRATION)
            val issuedAt = claims.getLong(KEY_ISSUED_AT)
            val nonce = claims.getStringIfDefined(KEY_NONCE)
            val authorizedParty = claims.getStringIfDefined(KEY_AUTHORIZED_PARTY)
            BUILT_IN_CLAIMS.forEach { claims.remove(it) }
            val additionalClaims = claims.toMap()

            return IdToken(
                issuer,
                subject,
                audience,
                expiration,
                issuedAt,
                nonce,
                authorizedParty,
                additionalClaims
            )
        }
    }
}
