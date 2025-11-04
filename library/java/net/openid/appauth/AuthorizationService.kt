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

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException.Companion.PARAM_ERROR
import net.openid.appauth.AuthorizationException.Companion.PARAM_ERROR_DESCRIPTION
import net.openid.appauth.AuthorizationException.Companion.PARAM_ERROR_URI
import net.openid.appauth.AuthorizationException.GeneralErrors
import net.openid.appauth.AuthorizationException.RegistrationRequestErrors
import net.openid.appauth.AuthorizationException.TokenRequestErrors
import net.openid.appauth.IdToken.IdTokenException
import net.openid.appauth.RegistrationResponse.MissingArgumentException
import net.openid.appauth.browser.BrowserDescriptor
import net.openid.appauth.browser.BrowserSelector
import net.openid.appauth.browser.CustomTabManager
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.internal.Logger
import net.openid.appauth.internal.formUrlEncode
import net.openid.appauth.internal.isActivity
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection.HTTP_MULT_CHOICE
import java.net.HttpURLConnection.HTTP_OK

/**
 * Manages the client's interactions with an OAuth 2.0 and OpenID Connect authorization service.
 *
 * This class provides methods to perform key tasks such as:
 * - Initiating authorization requests using a browser/custom tab.
 * - Handling authorization, end-session, and token exchange flows.
 * - Performing dynamic client registration.
 *
 * An `AuthorizationService` is created with a `Context` and an optional `AppAuthConfiguration`.
 * It automatically selects a browser that supports custom tabs if available, based on the
 * provided configuration.
 *
 * ### Lifecycle
 *
 * Instances of this class hold a connection to a `CustomTabsService` and must be disposed of
 * when no longer needed to avoid resource leaks. The `dispose()` method should be called, for
 * example, in the `onDestroy()` lifecycle event of an Activity. If the `AuthorizationService`
 * is created with an `Activity` context, it will automatically handle its own disposal.
 *
 * ### Example Usage:
 *
 * ```kotlin
 * // Create a service instance
 * val authService = AuthorizationService(this)
 *
 * // Create an authorization request
 * val authRequest = AuthorizationRequest.Builder(...)
 *     .build()
 *
 * // Define intents for success and cancellation
 * val successIntent = ...
 * val cancelIntent = ...
 *
 * // Perform the authorization request
 * lifecycleScope.launch {
 *     authService.performAuthorizationRequest(
 *         authRequest,
 *         successIntent,
 *         cancelIntent
 *     )
 * }
 *
 * // Remember to dispose of the service when done
 * override fun onDestroy() {
 *     super.onDestroy()
 *     authService.dispose()
 * }
 * ```
 *
 * @param context The context used to create the service. If it's an `Activity` context, the
 *     service will automatically be disposed of in `onDestroy`.
 * @param clientConfiguration The configuration for AppAuth, including the browser matcher and
 *     connection builder. Defaults to `AppAuthConfiguration.DEFAULT`.
 */
class AuthorizationService @JvmOverloads constructor(
    val context: Context,
    private val clientConfiguration: AppAuthConfiguration = AppAuthConfiguration.DEFAULT,
    /**
     * Returns the BrowserDescriptor of the chosen browser.
     * Can for example be used to set the browsers package name to a CustomTabsIntent.
     */
    val browserDescriptor: BrowserDescriptor? = BrowserSelector.select(
        context,
        clientConfiguration.browserMatcher
    ),
    val customTabManager: CustomTabManager = CustomTabManager(context)
) {
    private var mDisposed = false

    /**
     * Constructor that injects a url builder into the service for testing.
     */
    init {
        browserDescriptor?.let {
            if (it.useCustomTab) {
                customTabManager.bind(it.packageName)

                if (context.isActivity()) {
                    (context as LifecycleOwner).lifecycle.addObserver(
                        LifecycleEventObserver { _, event ->
                            if (event == ON_DESTROY) dispose()
                        }
                    )
                }
            }
        }
    }

    /**
     * Creates a custom tab builder, that will use a tab session from an existing connection to
     * a web browser, if available.
     */
    suspend fun createCustomTabsIntentBuilder(vararg possibleUris: Uri?): CustomTabsIntent.Builder {
        checkNotDisposed()
        return customTabManager.createTabBuilder(*possibleUris)
    }

    /**
     * Sends an authorization request to the authorization service, using a
     * [custom tab](https://developer.chrome.com/multidevice/android/customtabs).
     * The parameters of this request are determined by both the authorization service
     * configuration and the provided [request object][AuthorizationRequest]. Upon completion
     * of this request, the provided [completion PendingIntent][PendingIntent] will be invoked.
     * If the user cancels the authorization request, the provided
     * [cancel PendingIntent][PendingIntent] will be invoked.
     *
     * @param customTabsIntent
     * The intent that will be used to start the custom tab. It is recommended that this intent
     * be created with the help of [.createCustomTabsIntentBuilder], which will
     * ensure that a warmed-up version of the browser will be used, minimizing latency.
     *
     * @throws ActivityNotFoundException if no suitable browser is available to
     * perform the authorization flow.
     */
    @JvmOverloads
    suspend fun performAuthorizationRequest(
        request: AuthorizationRequest,
        completedIntent: PendingIntent,
        canceledIntent: PendingIntent? = null,
        customTabsIntent: CustomTabsIntent? = null
    ) {
        performAuthManagementRequest(
            request,
            completedIntent,
            canceledIntent,
            customTabsIntent ?: createCustomTabsIntentBuilder().build()
        )
    }

    /**
     * Sends an end session request to the authorization service, using a
     * [custom tab](https://developer.chrome.com/multidevice/android/customtabs).
     * The parameters of this request are determined by both the authorization service
     * configuration and the provided [request object][EndSessionRequest]. Upon completion
     * of this request, the provided [completion PendingIntent][PendingIntent] will be invoked.
     * If the user cancels the authorization request, the provided
     * [cancel PendingIntent][PendingIntent] will be invoked.
     *
     * @param customTabsIntent
     * The intent that will be used to start the custom tab. It is recommended that this intent
     * be created with the help of [.createCustomTabsIntentBuilder], which will
     * ensure that a warmed-up version of the browser will be used, minimizing latency.
     *
     * @throws ActivityNotFoundException if no suitable browser is available to
     * perform the authorization flow.
     */
    @JvmOverloads
    suspend fun performEndSessionRequest(
        request: EndSessionRequest,
        completedIntent: PendingIntent,
        canceledIntent: PendingIntent? = null,
        customTabsIntent: CustomTabsIntent? = null
    ) {
        performAuthManagementRequest(
            request,
            completedIntent,
            canceledIntent,
            customTabsIntent ?: createCustomTabsIntentBuilder().build()
        )
    }

    private fun performAuthManagementRequest(
        request: AuthorizationManagementRequest,
        completedIntent: PendingIntent,
        canceledIntent: PendingIntent?,
        customTabsIntent: CustomTabsIntent
    ) {
        checkNotDisposed()

        val authIntent = prepareAuthorizationRequestIntent(request, customTabsIntent)
        val startIntent = AuthorizationManagementActivity.createStartIntent(
            context,
            request,
            authIntent,
            completedIntent,
            canceledIntent
        )

        // Calling start activity from outside an activity requires FLAG_ACTIVITY_NEW_TASK.
        if (!context.isActivity()) {
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(startIntent)
    }

    /**
     * Constructs an intent that encapsulates the provided request and custom tabs intent,
     * and is intended to be launched via [Activity.startActivityForResult].
     * The parameters of this request are determined by both the authorization service
     * configuration and the provided [request object][AuthorizationRequest]. Upon completion
     * of this request, the activity that gets launched will call [Activity.setResult] with
     * [Activity.RESULT_OK] and an [Intent] containing authorization completion
     * information. If the user presses the back button or closes the browser tab, the launched
     * activity will call [Activity.setResult] with
     * [Activity.RESULT_CANCELED] without a data [Intent]. Note that
     * [Activity.RESULT_OK] indicates the authorization request completed,
     * not necessarily that it was a successful authorization.
     *
     * @param customTabsIntent
     * The intent that will be used to start the custom tab. It is recommended that this intent
     * be created with the help of [.createCustomTabsIntentBuilder], which will
     * ensure that a warmed-up version of the browser will be used, minimizing latency.
     *
     * @throws ActivityNotFoundException if no suitable browser is available to
     * perform the authorization flow.
     */
    @JvmOverloads
    suspend fun getAuthorizationRequestIntent(
        request: AuthorizationRequest,
        customTabsIntent: CustomTabsIntent? = null
    ): Intent {
        val authIntent = prepareAuthorizationRequestIntent(
            request,
            customTabsIntent ?: createCustomTabsIntentBuilder().build()
        )

        return AuthorizationManagementActivity.createStartForResultIntent(
            context,
            request,
            authIntent
        )
    }

    /**
     * Constructs an intent that encapsulates the provided request and custom tabs intent,
     * and is intended to be launched via [Activity.startActivityForResult].
     * The parameters of this request are determined by both the authorization service
     * configuration and the provided [request object][AuthorizationRequest]. Upon completion
     * of this request, the activity that gets launched will call [Activity.setResult] with
     * [Activity.RESULT_OK] and an [Intent] containing authorization completion
     * information. If the user presses the back button or closes the browser tab, the launched
     * activity will call [Activity.setResult] with
     * [Activity.RESULT_CANCELED] without a data [Intent]. Note that
     * [Activity.RESULT_OK] indicates the authorization request completed,
     * not necessarily that it was a successful authorization.
     *
     * @param customTabsIntent
     * The intent that will be used to start the custom tab. It is recommended that this intent
     * be created with the help of [.createCustomTabsIntentBuilder], which will
     * ensure that a warmed-up version of the browser will be used, minimizing latency.
     *
     * @throws ActivityNotFoundException if no suitable browser is available to
     * perform the authorization flow.
     */
    @JvmOverloads
    suspend fun getEndSessionRequestIntent(
        request: EndSessionRequest,
        customTabsIntent: CustomTabsIntent? = null
    ): Intent {
        val authIntent = prepareAuthorizationRequestIntent(
            request,
            customTabsIntent ?: createCustomTabsIntentBuilder().build()
        )

        return AuthorizationManagementActivity.createStartForResultIntent(
            context,
            request,
            authIntent
        )
    }

    /**
     * Sends a request to the authorization service to exchange a code granted as part of an
     * authorization request for a token, or to refresh an access token using a refresh token.
     *
     * This is a convenience wrapper for the more detailed `performTokenRequest` function,
     * providing sensible defaults. It automatically handles client authentication if provided.
     *
     * @param request The token request to be performed, which can be for an authorization code
     *     exchange or a token refresh.
     * @param clientAuthentication The mechanism for authenticating the client to the token endpoint.
     *     Defaults to [NoClientAuthentication] if not specified.
     * @return A [TokenResponse] containing the new tokens from the server.
     * @throws AuthorizationException if the request fails due to a network error, a server error
     *     response, or an invalid ID token.
     */
    @JvmOverloads
    suspend fun performTokenRequest(
        request: TokenRequest,
        clientAuthentication: ClientAuthentication = NoClientAuthentication,
    ): TokenResponse {
        checkNotDisposed()
        Logger.debug(
            "Initiating code exchange request to %s",
            request.configuration.tokenEndpoint
        )

        return performTokenRequest(
            request = request,
            clientAuthentication = clientAuthentication,
            connectionBuilder = clientConfiguration.connectionBuilder,
            clock = SystemClock,
            skipIssuerHttpsCheck = clientConfiguration.skipIssuerHttpsCheck
        )
    }

    /**
     * Performs a token request to the authorization server's token endpoint.
     *
     * This function is used for various grant types, such as exchanging an authorization code
     * for a token or using a refresh token to obtain a new access token. It constructs and sends a
     * POST request to the token endpoint as defined in the `TokenRequest`'s configuration.
     *
     * The method handles:
     * - Setting the appropriate headers, including `Content-Type` and `Accept`.
     * - Applying client authentication headers and parameters via the [ClientAuthentication] provider.
     * - Form-URL-encoding the request parameters.
     * - Sending the request over the network using the provided [ConnectionBuilder].
     * - Parsing the JSON response.
     * - Validating the ID token if one is received, using the provided [Clock].
     * - Handling network errors, JSON parsing errors, and OAuth2.0 specified error responses.
     *
     * @param request The token request to be performed.
     * @param clientAuthentication The mechanism for authenticating the client to the token endpoint.
     * @param connectionBuilder The builder for creating network connections.
     * @param clock The clock used for validating the ID token's expiration and issuance time.
     * @param skipIssuerHttpsCheck If set to `true`, HTTPS is not required for the issuer URI,
     * which can be useful for development and testing. This should not be `true` in production.
     * @return A [TokenResponse] containing the tokens and related information from the server.
     * @throws AuthorizationException if the request fails due to a network error, a server error
     * response, or an invalid ID token.
     */
    @Throws(AuthorizationException::class)
    private suspend fun performTokenRequest(
        request: TokenRequest,
        clientAuthentication: ClientAuthentication,
        connectionBuilder: ConnectionBuilder,
        clock: Clock,
        skipIssuerHttpsCheck: Boolean
    ): TokenResponse {
        var `is`: InputStream? = null

        val responseJson = withContext(Dispatchers.IO) {
            try {
                val conn = connectionBuilder.openConnection(request.configuration.tokenEndpoint)
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                if (conn.getRequestProperty("Accept").isNullOrEmpty()) {
                    conn.setRequestProperty("Accept", "application/json")
                }

                conn.doOutput = true

                clientAuthentication.getRequestHeaders(request.clientId)?.let { headers ->
                    headers.forEach { conn.setRequestProperty(it.key, it.value) }
                }

                val parameters = clientAuthentication.getRequestParameters(request.clientId)?.let {
                    request.requestParameters + it
                } ?: request.requestParameters

                val queryData = parameters.formUrlEncode()
                conn.setRequestProperty("Content-Length", queryData.length.toString())

                OutputStreamWriter(conn.outputStream).use { wr ->
                    wr.write(queryData)
                    wr.flush()
                }

                `is` = if (conn.responseCode in HTTP_OK until HTTP_MULT_CHOICE) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                JSONObject(`is`.readString())
            } catch (ex: IOException) {
                Logger.debugWithStack(ex, "Failed to complete exchange request")
                throw AuthorizationException.fromTemplate(
                    GeneralErrors.NETWORK_ERROR,
                    ex
                )
            } catch (ex: JSONException) {
                Logger.debugWithStack(ex, "Failed to complete exchange request")
                throw AuthorizationException.fromTemplate(
                    GeneralErrors.JSON_DESERIALIZATION_ERROR,
                    ex
                )
            } finally {
                `is`.closeQuietly()
            }
        }

        if (responseJson.has(PARAM_ERROR)) {
            val error = responseJson.getString(PARAM_ERROR)
            throw AuthorizationException.fromOAuthTemplate(
                TokenRequestErrors.byString(error),
                error,
                responseJson.optString(PARAM_ERROR_DESCRIPTION),
                responseJson.optString(PARAM_ERROR_URI).toUri()
            )
        }

        val tokenResponse = TokenResponse.Builder(request)
            .fromResponseJson(responseJson)
            .build()

        tokenResponse.idToken?.let { idTokenString ->
            try {
                val idToken = IdToken.from(idTokenString)
                idToken.validate(request, clock, skipIssuerHttpsCheck)
            } catch (ex: IdTokenException) {
                throw AuthorizationException.fromTemplate(
                    GeneralErrors.ID_TOKEN_PARSING_ERROR,
                    ex
                )
            } catch (ex: JSONException) {
                throw AuthorizationException.fromTemplate(
                    GeneralErrors.ID_TOKEN_PARSING_ERROR,
                    ex
                )
            }
        }

        Logger.debug("Token exchange with %s completed", request.configuration.tokenEndpoint)
        return tokenResponse
    }

    /**
     * Performs a registration request as per the
     * [OAuth 2.0 Dynamic Client Registration Protocol](https://tools.ietf.org/html/rfc7591).
     *
     * @param request The registration request.
     * @return The registration response.
     * @throws AuthorizationException for networking errors or when the authorization server
     *     returns an error. See [AuthorizationException.error] for the formal error code.
     */
    @Throws(AuthorizationException::class)
    suspend fun performRegistrationRequest(
        request: RegistrationRequest
    ): RegistrationResponse {
        checkNotDisposed()

        Logger.debug(
            "Initiating dynamic client registration %s",
            request.configuration.registrationEndpoint.toString()
        )

        var `is`: InputStream? = null

        val responseJson = withContext(Dispatchers.IO) {
            try {
                val postData = request.toJsonString()

                val conn = clientConfiguration.connectionBuilder
                    .openConnection(request.configuration.registrationEndpoint!!)

                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.setRequestProperty("Content-Length", postData.length.toString())

                OutputStreamWriter(conn.getOutputStream()).use { wr ->
                    wr.write(postData)
                    wr.flush()
                }

                `is` = conn.getInputStream()
                JSONObject(`is`.readString())
            } catch (ex: IOException) {
                Logger.debugWithStack(ex, "Failed to complete registration request")
                throw AuthorizationException.fromTemplate(GeneralErrors.NETWORK_ERROR, ex)
            } catch (ex: JSONException) {
                Logger.debugWithStack(ex, "Failed to complete registration request")
                throw AuthorizationException.fromTemplate(
                    GeneralErrors.JSON_DESERIALIZATION_ERROR,
                    ex
                )
            } finally {
                `is`.closeQuietly()
            }
        }

        if (responseJson.has(PARAM_ERROR)) {
            try {
                val error = responseJson.getString(PARAM_ERROR)

                throw AuthorizationException.fromOAuthTemplate(
                    RegistrationRequestErrors.byString(error),
                    error,
                    responseJson.getString(PARAM_ERROR_DESCRIPTION),
                    responseJson.getString(PARAM_ERROR_URI).toUri()
                )
            } catch (jsonEx: JSONException) {
                throw AuthorizationException.fromTemplate(
                    GeneralErrors.JSON_DESERIALIZATION_ERROR,
                    jsonEx
                )
            }
        }

        val registrationResponse = try {
            RegistrationResponse.Builder(request)
                .fromResponseJson(responseJson)
                .build()
        } catch (jsonEx: JSONException) {
            throw AuthorizationException.fromTemplate(
                GeneralErrors.JSON_DESERIALIZATION_ERROR,
                jsonEx
            )
        } catch (ex: MissingArgumentException) {
            Logger.errorWithStack(ex, "Malformed registration response")

            throw AuthorizationException.fromTemplate(
                GeneralErrors.INVALID_REGISTRATION_RESPONSE,
                ex
            )
        }

        Logger.debug(
            "Dynamic registration with %s completed",
            request.configuration.registrationEndpoint
        )

        return registrationResponse
    }

    /**
     * Disposes state that will not normally be handled by garbage collection. This should be
     * called when the authorization service is no longer required, including when any owning
     * activity is paused or destroyed.
     */
    fun dispose() {
        if (mDisposed) return
        customTabManager.dispose()
        mDisposed = true
    }

    private fun checkNotDisposed() {
        check(!mDisposed) { "Service has been disposed and rendered inoperable" }
    }

    /**
     * Prepares an intent to be used to launch the authorization flow in a browser.
     * This will be either a [CustomTabsIntent] or a standard `ACTION_VIEW` intent,
     * depending on the capabilities of the selected browser. The intent will be
     * configured with the correct package name and the authorization request URI.
     *
     * @param request The authorization management request to be sent.
     * @param customTabsIntent The pre-built [CustomTabsIntent] to be used if the selected
     *     browser supports custom tabs.
     * @return An [Intent] ready to be used to launch the authorization flow.
     * @throws ActivityNotFoundException if no suitable browser is available.
     */
    private fun prepareAuthorizationRequestIntent(
        request: AuthorizationManagementRequest,
        customTabsIntent: CustomTabsIntent
    ): Intent {
        checkNotDisposed()
        browserDescriptor ?: throw ActivityNotFoundException()
        val requestUri = request.toUri()

        val intent: Intent = if (browserDescriptor.useCustomTab) {
            customTabsIntent.intent
        } else {
            Intent(Intent.ACTION_VIEW)
        }

        intent.`package` = browserDescriptor.packageName
        intent.data = requestUri

        Logger.debug(
            "Using %s as browser for auth, custom tab = %s",
            intent.`package`,
            browserDescriptor.useCustomTab.toString()
        )

        //TODO fix logger for configuration
        //Logger.debug("Initiating authorization request to %s"
        //request.configuration.authorizationEndpoint);
        return intent
    }
}