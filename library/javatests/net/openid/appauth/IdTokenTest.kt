package net.openid.appauth

import android.util.Base64
import net.openid.appauth.IdToken.Companion.from
import net.openid.appauth.IdToken.IdTokenException
import net.openid.appauth.SystemClock.currentTimeMillis
import net.openid.appauth.TestValues.TEST_APP_REDIRECT_URI
import net.openid.appauth.TestValues.TEST_AUTH_CODE
import net.openid.appauth.TestValues.TEST_CLIENT_ID
import net.openid.appauth.TestValues.TEST_CODE_VERIFIER
import net.openid.appauth.TestValues.TEST_ISSUER
import net.openid.appauth.TestValues.TEST_NONCE
import net.openid.appauth.TestValues.getDiscoveryDocumentJson
import net.openid.appauth.TestValues.testAuthCodeExchangeRequest
import net.openid.appauth.TestValues.testAuthCodeExchangeRequestBuilder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IdTokenTest {
    @Test
    @Throws(Exception::class)
    fun testFrom() {
        val testToken: String = getUnsignedIdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            TEST_AUDIENCE,
            TEST_NONCE
        )

        val idToken = from(testToken)

        assertEquals(TEST_ISSUER, idToken.issuer)
        assertEquals(TEST_SUBJECT, idToken.subject)
        assertThat(idToken.audience, contains(TEST_AUDIENCE))
        assertEquals(TEST_NONCE, idToken.nonce)
    }

    @Test
    @Throws(Exception::class)
    fun testFrom_withAdditionalClaims() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val additionalClaims = mapOf(
            "claim1" to "value1",
            "claim2" to listOf("value2", "value3")
        )

        val testToken: String = getUnsignedIdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            TEST_AUDIENCE,
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds,
            TEST_NONCE,
            additionalClaims
        )

        val idToken = from(testToken)

        assertEquals("value1", idToken.additionalClaims["claim1"])

        @Suppress("UNCHECKED_CAST")
        (assertEquals(
            "value2",
            (idToken.additionalClaims["claim2"] as List<String>)[0]
        ))
    }

    @Test
    @Throws(Exception::class)
    fun testFrom_shouldParseAudienceList() {
        val audienceList = listOf(TEST_AUDIENCE, "AUDI3NCE2")

        val testToken: String = getUnsignedIdTokenWithAudienceList(
            TEST_ISSUER,
            TEST_SUBJECT,
            audienceList,
            TEST_NONCE
        )

        val idToken = from(testToken)

        assertEquals(TEST_ISSUER, idToken.issuer)
        assertEquals(TEST_SUBJECT, idToken.subject)
        assertEquals(audienceList, idToken.audience)
        assertEquals(TEST_NONCE, idToken.nonce)
    }

    @Test(expected = IdTokenException::class)
    @Throws(IdTokenException::class, JSONException::class)
    fun testFrom_shouldFailOnMissingSection() {
        from("header.")
    }

    @Test(expected = JSONException::class)
    @Throws(IdTokenException::class, JSONException::class)
    fun testFrom_shouldFailOnMalformedInput() {
        from("header.claims")
    }

    @Test(expected = JSONException::class)
    @Throws(IdTokenException::class, JSONException::class)
    fun testFrom_shouldFailOnMissingIssuer() {
        val testToken: String = getUnsignedIdToken(
            null,
            TEST_SUBJECT,
            TEST_AUDIENCE,
            TEST_NONCE
        )

        from(testToken)
    }

    @Test(expected = JSONException::class)
    @Throws(IdTokenException::class, JSONException::class)
    fun testFrom_shouldFailOnMissingSubject() {
        val testToken: String = getUnsignedIdToken(
            TEST_ISSUER,
            null,
            TEST_AUDIENCE,
            TEST_NONCE
        )

        from(testToken)
    }

    @Test(expected = JSONException::class)
    @Throws(IdTokenException::class, JSONException::class)
    fun testFrom_shouldFailOnMissingAudience() {
        val testToken: String = getUnsignedIdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            null,
            TEST_NONCE
        )

        from(testToken)
    }

    @Test(expected = JSONException::class)
    @Throws(IdTokenException::class, JSONException::class)
    fun testFrom_shouldFailOnMissingExpiration() {
        val testToken: String = getUnsignedIdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            TEST_AUDIENCE,
            null,
            0L,
            TEST_NONCE
        )

        from(testToken)
    }

    @Test(expected = JSONException::class)
    @Throws(IdTokenException::class, JSONException::class)
    fun testFrom_shouldFailOnMissingIssuedAt() {
        val testToken: String = getUnsignedIdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            TEST_AUDIENCE,
            0L,
            null,
            TEST_NONCE
        )

        from(testToken)
    }

    @Test
    @Throws(AuthorizationException::class)
    fun testValidate() {
        val idToken: IdToken = validIdToken
        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test
    @Throws(AuthorizationException::class)
    fun testValidate_withoutNonce() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val tokenRequest = testAuthCodeExchangeRequestBuilder.build()
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(AuthorizationException::class)
    fun testValidate_shouldFailOnIssuerMismatch() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            "https://other.issuer",
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(
        AuthorizationException::class,
        JSONException::class,
        AuthorizationServiceDiscovery.MissingArgumentException::class
    )
    fun testValidate_shouldFailOnNonHttpsIssuer() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            "http://other.issuer",
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val serviceDocJsonWithOtherIssuer = getDiscoveryDocJsonWithIssuer("http://other.issuer")
        val discoveryDoc = AuthorizationServiceDiscovery(JSONObject(serviceDocJsonWithOtherIssuer))
        val serviceConfiguration = AuthorizationServiceConfiguration(discoveryDoc)

        val tokenRequest = TokenRequest.Builder(serviceConfiguration, TEST_CLIENT_ID)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setCodeVerifier(TEST_CODE_VERIFIER)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)
            .build()

        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test
    @Throws(
        AuthorizationException::class,
        JSONException::class,
        AuthorizationServiceDiscovery.MissingArgumentException::class
    )
    fun testValidate_shouldSkipNonHttpsIssuer() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            "http://other.issuer",
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val serviceDocJsonWithOtherIssuer = getDiscoveryDocJsonWithIssuer("http://other.issuer")
        val discoveryDoc = AuthorizationServiceDiscovery(JSONObject(serviceDocJsonWithOtherIssuer))
        val serviceConfiguration = AuthorizationServiceConfiguration(discoveryDoc)

        val tokenRequest = TokenRequest.Builder(serviceConfiguration, TEST_CLIENT_ID)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setCodeVerifier(TEST_CODE_VERIFIER)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)
            .build()

        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock, true)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(
        AuthorizationException::class,
        JSONException::class,
        AuthorizationServiceDiscovery.MissingArgumentException::class
    )
    fun testValidate_shouldFailOnIssuerMissingHost() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            "https://",
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val serviceDocJsonWithIssuerMissingHost = getDiscoveryDocJsonWithIssuer("https://")
        val discoveryDoc =
            AuthorizationServiceDiscovery(JSONObject(serviceDocJsonWithIssuerMissingHost))
        val serviceConfiguration = AuthorizationServiceConfiguration(discoveryDoc)

        val tokenRequest = TokenRequest.Builder(serviceConfiguration, TEST_CLIENT_ID)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setCodeVerifier(TEST_CODE_VERIFIER)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)
            .build()

        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(
        AuthorizationException::class,
        JSONException::class,
        AuthorizationServiceDiscovery.MissingArgumentException::class
    )
    fun testValidate_shouldFailOnIssuerWithQueryParam() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            "https://some.issuer?param=value",
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val serviceDocJsonWithIssuerMissingHost = getDiscoveryDocJsonWithIssuer(
            "https://some.issuer?param=value"
        )

        val discoveryDoc =
            AuthorizationServiceDiscovery(JSONObject(serviceDocJsonWithIssuerMissingHost))

        val serviceConfiguration = AuthorizationServiceConfiguration(discoveryDoc)

        val tokenRequest = TokenRequest.Builder(serviceConfiguration, TEST_CLIENT_ID)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setCodeVerifier(TEST_CODE_VERIFIER)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)
            .build()

        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(
        AuthorizationException::class,
        JSONException::class,
        AuthorizationServiceDiscovery.MissingArgumentException::class
    )
    fun testValidate_shouldFailOnIssuerWithFragment() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            "https://some.issuer/#/fragment",
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val serviceDocJsonWithIssuerMissingHost = getDiscoveryDocJsonWithIssuer(
            "https://some.issuer/#/fragment"
        )
        val discoveryDoc =
            AuthorizationServiceDiscovery(JSONObject(serviceDocJsonWithIssuerMissingHost))

        val serviceConfiguration = AuthorizationServiceConfiguration(discoveryDoc)

        val tokenRequest = TokenRequest.Builder(serviceConfiguration, TEST_CLIENT_ID)
            .setAuthorizationCode(TEST_AUTH_CODE)
            .setCodeVerifier(TEST_CODE_VERIFIER)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setRedirectUri(TEST_APP_REDIRECT_URI)
            .build()

        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test
    @Throws(AuthorizationException::class)
    fun testValidate_audienceMatch() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val tokenRequest = testAuthCodeExchangeRequest
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(AuthorizationException::class)
    fun testValidate_shouldFailOnAudienceMismatch() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf("some_other_audience"),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds
        )

        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test
    @Throws(AuthorizationException::class)
    fun testValidate_authorizedPartyMatch() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf("some_other_audience"),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds,
            TEST_NONCE,
            TEST_CLIENT_ID
        )

        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(AuthorizationException::class)
    fun testValidate_shouldFailOnAudienceAndAuthorizedPartyMismatch() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf("some_other_audience"),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds,
            TEST_NONCE,
            "some_other_party",
            emptyMap()
        )
        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(AuthorizationException::class)
    fun testValidate_shouldFailOnExpiredToken() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds - tenMinutesInSeconds,
            nowInSeconds
        )

        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(AuthorizationException::class)
    fun testValidate_shouldFailOnIssuedAtOverTenMinutesAgo() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds - (tenMinutesInSeconds * 2)
        )

        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    @Test(expected = AuthorizationException::class)
    @Throws(AuthorizationException::class)
    fun testValidate_shouldFailOnNonceMismatch() {
        val nowInSeconds = currentTimeMillis / 1000
        val tenMinutesInSeconds = (10 * 60).toLong()

        val idToken = IdToken(
            TEST_ISSUER,
            TEST_SUBJECT,
            listOf(TEST_CLIENT_ID),
            nowInSeconds + tenMinutesInSeconds,
            nowInSeconds,
            "some_other_nonce",
            null
        )

        val tokenRequest = this.authCodeExchangeRequestWithNonce
        val clock: Clock = SystemClock
        idToken.validate(tokenRequest, clock)
    }

    private fun getDiscoveryDocJsonWithIssuer(issuer: String): String {
        return getDiscoveryDocumentJson(
            issuer,
            AuthorizationServiceDiscoveryTest.TEST_AUTHORIZATION_ENDPOINT,
            AuthorizationServiceDiscoveryTest.TEST_TOKEN_ENDPOINT,
            AuthorizationServiceDiscoveryTest.TEST_USERINFO_ENDPOINT,
            AuthorizationServiceDiscoveryTest.TEST_REGISTRATION_ENDPOINT,
            AuthorizationServiceDiscoveryTest.TEST_END_SESSION_ENDPOINT,
            AuthorizationServiceDiscoveryTest.TEST_JWKS_URI,
            AuthorizationServiceDiscoveryTest.TEST_RESPONSE_TYPES_SUPPORTED,
            AuthorizationServiceDiscoveryTest.TEST_SUBJECT_TYPES_SUPPORTED,
            AuthorizationServiceDiscoveryTest.TEST_ID_TOKEN_SIGNING_ALG_VALUES,
            AuthorizationServiceDiscoveryTest.TEST_SCOPES_SUPPORTED,
            AuthorizationServiceDiscoveryTest.TEST_TOKEN_ENDPOINT_AUTH_METHODS,
            AuthorizationServiceDiscoveryTest.TEST_CLAIMS_SUPPORTED
        )
    }

    private val authCodeExchangeRequestWithNonce: TokenRequest
        get() = testAuthCodeExchangeRequestBuilder
            .setNonce(TEST_NONCE)
            .build()

    companion object {
        const val TEST_SUBJECT: String = "SUBJ3CT"
        const val TEST_AUDIENCE: String = "AUDI3NCE"


        private fun base64UrlNoPaddingEncode(data: ByteArray) = Base64.encodeToString(
            data,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )


        private val validIdToken: IdToken
            get() {
                val nowInSeconds = currentTimeMillis / 1000
                val tenMinutesInSeconds = (10 * 60).toLong()

                return IdToken(
                    TEST_ISSUER,
                    TEST_SUBJECT,
                    listOf(TEST_CLIENT_ID),
                    nowInSeconds + tenMinutesInSeconds,
                    nowInSeconds,
                    TEST_NONCE,
                    TEST_CLIENT_ID,
                    emptyMap()
                )
            }

        fun getUnsignedIdTokenWithAudienceList(
            issuer: String?,
            subject: String?,
            audience: List<String>,
            nonce: String?
        ): String {
            val nowInSeconds = currentTimeMillis / 1000
            val tenMinutesInSeconds = (10 * 60).toLong()

            return getUnsignedIdToken(
                issuer,
                subject,
                audience,
                nowInSeconds + tenMinutesInSeconds,
                nowInSeconds,
                nonce
            )
        }

        fun getUnsignedIdToken(
            issuer: String?,
            subject: String?,
            audience: String?,
            nonce: String?
        ): String {
            val nowInSeconds = currentTimeMillis / 1000
            val tenMinutesInSeconds = (10 * 60).toLong()

            return getUnsignedIdToken(
                issuer,
                subject,
                audience,
                nowInSeconds + tenMinutesInSeconds,
                nowInSeconds,
                nonce
            )
        }

        fun getUnsignedIdToken(
            issuer: String?,
            subject: String?,
            audience: List<String>,
            expiration: Long?,
            issuedAt: Long?,
            nonce: String?
        ): String {
            val header = JSONObject().put("typ", "JWT")

            val claims = JSONObject().apply {
                putIfNotNull("iss", issuer)
                putIfNotNull("sub", subject)
                put("aud", JSONArray(audience))
                putIfNotNull("exp", expiration?.toString())
                putIfNotNull("iat", issuedAt?.toString())
                putIfNotNull("nonce", nonce)
            }

            val encodedHeader = base64UrlNoPaddingEncode(header.toString().toByteArray())
            val encodedClaims = base64UrlNoPaddingEncode(claims.toString().toByteArray())
            return "$encodedHeader.$encodedClaims"
        }

        fun getUnsignedIdToken(
            issuer: String?,
            subject: String?,
            audience: String?,
            expiration: Long?,
            issuedAt: Long?,
            nonce: String?
        ) = getUnsignedIdToken(
            issuer,
            subject,
            audience,
            expiration,
            issuedAt,
            nonce,
            emptyMap()
        )

        fun getUnsignedIdToken(
            issuer: String?,
            subject: String?,
            audience: String?,
            expiration: Long?,
            issuedAt: Long?,
            nonce: String?,
            additionalClaims: Map<String, Any>
        ): String {
            val header = JSONObject().put("typ", "JWT")

            val claims = JSONObject().apply {
                putIfNotNull("iss", issuer)
                putIfNotNull("sub", subject)
                putIfNotNull("aud", audience)
                putIfNotNull("exp", expiration?.toString())
                putIfNotNull("iat", issuedAt?.toString())
                putIfNotNull("nonce", nonce)

                additionalClaims.forEach {
                    putIfNotNull(it.key, it.value)
                }
            }

            val encodedHeader = base64UrlNoPaddingEncode(header.toString().toByteArray())
            val encodedClaims = base64UrlNoPaddingEncode(claims.toString().toByteArray())
            return "$encodedHeader.$encodedClaims"
        }
    }
}
