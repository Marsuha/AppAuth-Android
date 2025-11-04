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

import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import org.json.JSONException
import org.json.JSONObject

/**
 * Returned as a response to OAuth2 requests if they fail. Specifically:
 *
 * - The response to [token requests][AuthorizationService.performTokenRequest],
 *
 * - The response to [configuration retrieval][AuthorizationServiceConfiguration.fetchFromUrl].
 */
@Suppress("unused")
class AuthorizationException @JvmOverloads constructor(
    /**
     * The type of the error.
     * @see .TYPE_GENERAL_ERROR
     *
     * @see .TYPE_OAUTH_AUTHORIZATION_ERROR
     *
     * @see .TYPE_OAUTH_TOKEN_ERROR
     *
     * @see .TYPE_RESOURCE_SERVER_AUTHORIZATION_ERROR
     */
    @JvmField val type: Int,
    /**
     * The error code describing the class of problem encountered from the set defined in this
     * class.
     */
    @JvmField val code: Int,
    /**
     * The error string as it is found in the OAuth2 protocol.
     */
    @JvmField val error: String? = null,
    /**
     * The human readable error message associated with this exception, if available.
     */
    @JvmField val errorDescription: String? = null,
    /**
     * A URI identifying a human-readable web page with information about this error.
     */
    val errorUri: Uri? = null,
    rootCause: Throwable? = null
) : Exception(errorDescription, rootCause) {
    /**
     * Error codes specific to AppAuth for Android, rather than those defined in the OAuth2 and
     * OpenID specifications.
     */
    object GeneralErrors {
        // codes in this group should be between 0-999
        /**
         * Indicates a problem parsing an OpenID Connect Service Discovery document.
         */
        val INVALID_DISCOVERY_DOCUMENT: AuthorizationException =
            generalEx(0, "Invalid discovery document")

        /**
         * Indicates the user manually canceled the OAuth authorization code flow.
         */
        @JvmField
        val USER_CANCELED_AUTH_FLOW: AuthorizationException = generalEx(1, "User cancelled flow")

        /**
         * Indicates an OAuth authorization flow was programmatically cancelled.
         */
        val PROGRAM_CANCELED_AUTH_FLOW: AuthorizationException =
            generalEx(2, "Flow cancelled programmatically")

        /**
         * Indicates a network error occurred.
         */
        @JvmField
        val NETWORK_ERROR: AuthorizationException = generalEx(3, "Network error")

        /**
         * Indicates a server error occurred.
         */
        val SERVER_ERROR: AuthorizationException = generalEx(4, "Server error")

        /**
         * Indicates a problem occurred deserializing JSON.
         */
        val JSON_DESERIALIZATION_ERROR: AuthorizationException =
            generalEx(5, "JSON deserialization error")

        /**
         * Indicates a problem occurred constructing a [token response][TokenResponse] object
         * from the JSON provided by the server.
         */
        val TOKEN_RESPONSE_CONSTRUCTION_ERROR: AuthorizationException =
            generalEx(6, "Token response construction error")

        /**
         * Indicates a problem parsing an OpenID Connect Registration Response.
         */
        val INVALID_REGISTRATION_RESPONSE: AuthorizationException =
            generalEx(7, "Invalid registration response")

        /**
         * Indicates that a received ID token could not be parsed
         */
        val ID_TOKEN_PARSING_ERROR: AuthorizationException =
            generalEx(8, "Unable to parse ID Token")

        /**
         * Indicates that a received ID token is invalid
         */
        @JvmField
        val ID_TOKEN_VALIDATION_ERROR: AuthorizationException = generalEx(9, "Invalid ID Token")
    }

    /**
     * Error codes related to failed authorization requests.
     *
     * @see "The OAuth 2.0 Authorization Framework
     */
    object AuthorizationRequestErrors {
        // codes in this group should be between 1000-1999
        /**
         * An `invalid_request` OAuth2 error response.
         */
        @JvmField
        val INVALID_REQUEST: AuthorizationException = authEx(1000, "invalid_request")

        /**
         * An `unauthorized_client` OAuth2 error response.
         */
        val UNAUTHORIZED_CLIENT: AuthorizationException = authEx(1001, "unauthorized_client")

        /**
         * An `access_denied` OAuth2 error response.
         */
        @JvmField
        val ACCESS_DENIED: AuthorizationException = authEx(1002, "access_denied")

        /**
         * An `unsupported_response_type` OAuth2 error response.
         */
        val UNSUPPORTED_RESPONSE_TYPE: AuthorizationException =
            authEx(1003, "unsupported_response_type")

        /**
         * An `invalid_scope` OAuth2 error response.
         */
        val INVALID_SCOPE: AuthorizationException = authEx(1004, "invalid_scope")

        /**
         * An `server_error` OAuth2 error response, equivalent to an HTTP 500 error code, but
         * sent via redirect.
         */
        val SERVER_ERROR: AuthorizationException = authEx(1005, "server_error")

        /**
         * A `temporarily_unavailable` OAuth2 error response, equivalent to an HTTP 503 error
         * code, but sent via redirect.
         */
        val TEMPORARILY_UNAVAILABLE: AuthorizationException =
            authEx(1006, "temporarily_unavailable")

        /**
         * An authorization error occurring on the client rather than the server. For example,
         * due to client misconfiguration. This error should be treated as unrecoverable.
         */
        @JvmField
        val CLIENT_ERROR: AuthorizationException = authEx(1007)

        /**
         * Indicates an OAuth error as per RFC 6749, but the error code is not known to the
         * AppAuth for Android library. It could be a custom error or code, or one from an
         * OAuth extension. The [.error] field provides the exact error string returned by
         * the server.
         */
        val OTHER: AuthorizationException = authEx(1008)

        /**
         * Indicates that the response state param did not match the request state param,
         * resulting in the response being discarded.
         */
        @JvmField
        val STATE_MISMATCH: AuthorizationException =
            generalEx(9, "Response state param did not match request state")

        private val STRING_TO_EXCEPTION: Map<String, AuthorizationException> =
            exceptionMapByString(
                INVALID_REQUEST,
                UNAUTHORIZED_CLIENT,
                ACCESS_DENIED,
                UNSUPPORTED_RESPONSE_TYPE,
                INVALID_SCOPE,
                SERVER_ERROR,
                TEMPORARILY_UNAVAILABLE,
                CLIENT_ERROR,
                OTHER
            )

        /**
         * Returns the matching exception type for the provided OAuth2 error string, or
         * [.OTHER] if unknown.
         */
        fun byString(error: String) = STRING_TO_EXCEPTION[error] ?: OTHER
    }

    /**
     * Error codes related to failed token requests.
     *
     * @see "The OAuth 2.0 Authorization Framework"
     */
    object TokenRequestErrors {
        // codes in this group should be between 2000-2999
        /**
         * An `invalid_request` OAuth2 error response.
         */
        val INVALID_REQUEST: AuthorizationException = tokenEx(2000, "invalid_request")

        /**
         * An `invalid_client` OAuth2 error response.
         */
        val INVALID_CLIENT: AuthorizationException = tokenEx(2001, "invalid_client")

        /**
         * An `invalid_grant` OAuth2 error response.
         */
        val INVALID_GRANT: AuthorizationException = tokenEx(2002, "invalid_grant")

        /**
         * An `unauthorized_client` OAuth2 error response.
         */
        @JvmField
        val UNAUTHORIZED_CLIENT: AuthorizationException = tokenEx(2003, "unauthorized_client")

        /**
         * An `unsupported_grant_type` OAuth2 error response.
         */
        val UNSUPPORTED_GRANT_TYPE: AuthorizationException = tokenEx(2004, "unsupported_grant_type")

        /**
         * An `invalid_scope` OAuth2 error response.
         */
        val INVALID_SCOPE: AuthorizationException = tokenEx(2005, "invalid_scope")

        /**
         * An authorization error occurring on the client rather than the server. For example,
         * due to client misconfiguration. This error should be treated as unrecoverable.
         */
        @JvmField
        val CLIENT_ERROR: AuthorizationException = tokenEx(2006)

        /**
         * Indicates an OAuth error as per RFC 6749, but the error code is not known to the
         * AppAuth for Android library. It could be a custom error or code, or one from an
         * OAuth extension. The [.error] field provides the exact error string returned by
         * the server.
         */
        val OTHER: AuthorizationException = tokenEx(2007)

        private val STRING_TO_EXCEPTION: Map<String, AuthorizationException> =
            exceptionMapByString(
                INVALID_REQUEST,
                INVALID_CLIENT,
                INVALID_GRANT,
                UNAUTHORIZED_CLIENT,
                UNSUPPORTED_GRANT_TYPE,
                INVALID_SCOPE,
                CLIENT_ERROR,
                OTHER
            )

        /**
         * Returns the matching exception type for the provided OAuth2 error string, or
         * [.OTHER] if unknown.
         */
        fun byString(error: String) = STRING_TO_EXCEPTION[error] ?: OTHER
    }

    /**
     * Error codes related to failed registration requests.
     */
    object RegistrationRequestErrors {
        // codes in this group should be between 4000-4999
        /**
         * An `invalid_request` OAuth2 error response.
         */
        val INVALID_REQUEST: AuthorizationException = registrationEx(4000, "invalid_request")

        /**
         * An `invalid_client` OAuth2 error response.
         */
        val INVALID_REDIRECT_URI: AuthorizationException =
            registrationEx(4001, "invalid_redirect_uri")

        /**
         * An `invalid_grant` OAuth2 error response.
         */
        val INVALID_CLIENT_METADATA: AuthorizationException =
            registrationEx(4002, "invalid_client_metadata")

        /**
         * An authorization error occurring on the client rather than the server. For example,
         * due to client misconfiguration. This error should be treated as unrecoverable.
         */
        val CLIENT_ERROR: AuthorizationException = registrationEx(4003)

        /**
         * Indicates an OAuth error as per RFC 6749, but the error code is not known to the
         * AppAuth for Android library. It could be a custom error or code, or one from an
         * OAuth extension. The [.error] field provides the exact error string returned by
         * the server.
         */
        val OTHER: AuthorizationException = registrationEx(4004)

        private val STRING_TO_EXCEPTION: Map<String, AuthorizationException> =
            exceptionMapByString(
                INVALID_REQUEST,
                INVALID_REDIRECT_URI,
                INVALID_CLIENT_METADATA,
                CLIENT_ERROR,
                OTHER
            )

        /**
         * Returns the matching exception type for the provided OAuth2 error string, or
         * [.OTHER] if unknown.
         */
        fun byString(error: String) = STRING_TO_EXCEPTION[error] ?: OTHER
    }

    /**
     * Produces a JSON representation of the authorization exception, for transmission or storage.
     * This does not include any provided root cause.
     */
    fun toJson() = JSONObject().apply {
        put(KEY_TYPE, type)
        put(KEY_CODE, code)
        error?.let { put(KEY_ERROR, it) }
        errorDescription?.let { put(KEY_ERROR_DESCRIPTION, it) }
        errorUri?.let { put(KEY_ERROR_URI, it.toString()) }
    }

    /**
     * Provides a JSON string representation of an authorization exception, for transmission or
     * storage. This does not include any provided root cause.
     */
    fun toJsonString() = toJson().toString()

    /**
     * Creates an intent from this exception. Used to carry error responses to the handling activity
     * specified in calls to [AuthorizationService.performAuthorizationRequest].
     */
    fun toIntent() = Intent().apply {
        putExtra(EXTRA_EXCEPTION, toJsonString())
    }

    /**
     * Exceptions are considered to be equal if their [type][.type] and [code][.code]
     * are the same; all other properties are irrelevant for comparison.
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || other !is AuthorizationException) return false
        return this.type == other.type && this.code == other.code
    }

    // equivalent to Arrays.hashCode(new int[] { type, code });
    override fun hashCode() = (HASH_MULTIPLIER * (HASH_MULTIPLIER + type)) + code

    override fun toString() = "AuthorizationException: ${toJsonString()}"

    companion object {
        /**
         * The extra string that used to store an [AuthorizationException] in an intent by
         * [.toIntent].
         */
        const val EXTRA_EXCEPTION: String = "net.openid.appauth.AuthorizationException"

        /**
         * The OAuth2 parameter used to indicate the type of error during an authorization or
         * token request.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework"
         */
        const val PARAM_ERROR: String = "error"

        /**
         * The OAuth2 parameter used to provide a human readable description of the error which
         * occurred.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework"
         */
        const val PARAM_ERROR_DESCRIPTION: String = "error_description"

        /**
         * The OAuth2 parameter used to provide a URI to a human-readable page which describes the
         * error.
         *
         * @see "The OAuth 2.0 Authorization Framework
         * @see "The OAuth 2.0 Authorization Framework"
         */
        const val PARAM_ERROR_URI: String = "error_uri"


        /**
         * The error type used for all errors that are not specific to OAuth related responses.
         */
        const val TYPE_GENERAL_ERROR: Int = 0

        /**
         * The error type for OAuth specific errors on the authorization endpoint. This error type is
         * used when the server responds to an authorization request with an explicit OAuth error, as
         * defined by [the OAuth2 specification, section 4.1.2.1](
 * https://tools.ietf.org/html/rfc6749#section-4.1.2.1). If the authorization response is
         * invalid and not explicitly an error response, another error type will be used.
         *
         * @see "The OAuth 2.0 Authorization Framework
         */
        const val TYPE_OAUTH_AUTHORIZATION_ERROR: Int = 1

        /**
         * The error type for OAuth specific errors on the token endpoint. This error type is used when
         * the server responds with HTTP 400 and an OAuth error, as defined by
         * [the OAuth2 specification, section 5.2](https://tools.ietf.org/html/rfc6749#section-5.2).
         * If an HTTP 400 response does not parse as an OAuth error (i.e. no 'error' field is present
         * or the JSON is invalid), another error domain will be used.
         *
         * @see "The OAuth 2.0 Authorization Framework"
         */
        const val TYPE_OAUTH_TOKEN_ERROR: Int = 2

        /**
         * The error type for authorization errors encountered out of band on the resource server.
         */
        const val TYPE_RESOURCE_SERVER_AUTHORIZATION_ERROR: Int = 3

        /**
         * The error type for OAuth specific errors on the registration endpoint.
         */
        const val TYPE_OAUTH_REGISTRATION_ERROR: Int = 4

        @VisibleForTesting
        const val KEY_TYPE: String = "type"

        @VisibleForTesting
        const val KEY_CODE: String = "code"

        @VisibleForTesting
        const val KEY_ERROR: String = "error"

        @VisibleForTesting
        const val KEY_ERROR_DESCRIPTION: String = "errorDescription"

        @VisibleForTesting
        const val KEY_ERROR_URI: String = "errorUri"

        /**
         * Prime number multiplier used to produce a reasonable hash value distribution.
         */
        private const val HASH_MULTIPLIER = 31

        private fun generalEx(code: Int, errorDescription: String? = null): AuthorizationException {
            return AuthorizationException(
                type = TYPE_GENERAL_ERROR,
                code = code,
                errorDescription = errorDescription
            )
        }

        private fun authEx(code: Int, error: String? = null): AuthorizationException {
            return AuthorizationException(
                type = TYPE_OAUTH_AUTHORIZATION_ERROR,
                code = code,
                error = error
            )
        }

        private fun tokenEx(code: Int, error: String? = null): AuthorizationException {
            return AuthorizationException(
                type = TYPE_OAUTH_TOKEN_ERROR,
                code = code,
                error = error
            )
        }

        private fun registrationEx(code: Int, error: String? = null): AuthorizationException {
            return AuthorizationException(
                type = TYPE_OAUTH_REGISTRATION_ERROR,
                code = code,
                error = error
            )
        }

        /**
         * Creates an exception based on one of the existing values defined in
         * [GeneralErrors], [AuthorizationRequestErrors] or [TokenRequestErrors],
         * providing a root cause.
         */
        @JvmStatic
        fun fromTemplate(
            ex: AuthorizationException,
            rootCause: Throwable?
        ): AuthorizationException {
            return AuthorizationException(
                ex.type,
                ex.code,
                ex.error,
                ex.errorDescription,
                ex.errorUri,
                rootCause
            )
        }

        /**
         * Creates an exception based on one of the existing values defined in
         * [AuthorizationRequestErrors] or [TokenRequestErrors], adding information
         * retrieved from OAuth error response.
         */
        @JvmOverloads
        fun fromOAuthTemplate(
            ex: AuthorizationException,
            errorOverride: String? = null,
            errorDescriptionOverride: String? = null,
            errorUriOverride: Uri? = null
        ): AuthorizationException {
            return AuthorizationException(
                type = ex.type,
                code = ex.code,
                error = errorOverride ?: ex.error,
                errorDescription = errorDescriptionOverride?.takeIf { it.isNotEmpty() }
                    ?: ex.errorDescription,
                errorUri = errorUriOverride ?: ex.errorUri,
            )
        }

        /**
         * Creates an exception from an OAuth redirect URI that describes an authorization failure.
         */
        fun fromOAuthRedirect(redirectUri: Uri): AuthorizationException {
            val error = redirectUri.getQueryParameter(PARAM_ERROR)
            val errorDescription = redirectUri.getQueryParameter(PARAM_ERROR_DESCRIPTION)
            val errorUri = redirectUri.getQueryParameter(PARAM_ERROR_URI)
            val base = error?.let { AuthorizationRequestErrors.byString(it) }
                ?: AuthorizationRequestErrors.OTHER

            return AuthorizationException(
                type = base.type,
                code = base.code,
                error = error,
                errorDescription = errorDescription ?: base.errorDescription,
                errorUri = errorUri?.toUri() ?: base.errorUri,
            )
        }

        /**
         * Reconstructs an [AuthorizationException] from the JSON produced by
         * [.toJsonString].
         * @throws JSONException if the JSON is malformed or missing required properties
         */
        @Throws(JSONException::class)
        fun fromJson(jsonStr: String): AuthorizationException {
            require(jsonStr.isNotEmpty()) { "jsonStr cannot be empty" }
            return fromJson(JSONObject(jsonStr))
        }

        /**
         * Reconstructs an [AuthorizationException] from the JSON produced by
         * [toJson].
         * @throws JSONException if the JSON is malformed or missing required properties
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJson(json: JSONObject): AuthorizationException {
            return AuthorizationException(
                type = json.getInt(KEY_TYPE),
                code = json.getInt(KEY_CODE),
                error = json.getStringIfDefined(KEY_ERROR),
                errorDescription = json.getStringIfDefined(KEY_ERROR_DESCRIPTION),
                errorUri = json.getUriIfDefined(KEY_ERROR_URI),
            )
        }

        /**
         * Extracts an [AuthorizationException] from an intent produced by [.toIntent].
         * This is used to retrieve an error response in the handler registered for a call to
         * [AuthorizationService.performAuthorizationRequest].
         */
        @JvmStatic
        fun fromIntent(data: Intent): AuthorizationException? {
            try {
                return data.getStringExtra(EXTRA_EXCEPTION)?.let { fromJson(it) }
            } catch (ex: JSONException) {
                throw IllegalArgumentException("Intent contains malformed exception data", ex)
            }
        }

        private fun exceptionMapByString(
            vararg exceptions: AuthorizationException
        ) = exceptions.filter { it.error != null }.associateBy { it.error!! }
    }
}
