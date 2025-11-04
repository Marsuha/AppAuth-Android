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

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.net.Uri
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.AuthorizationException.AuthorizationRequestErrors
import net.openid.appauth.AuthorizationException.Companion.PARAM_ERROR
import net.openid.appauth.AuthorizationManagementUtil.RequestType
import net.openid.appauth.AuthorizationManagementUtil.requestTypeFor
import net.openid.appauth.AuthorizationManagementUtil.responseWith
import net.openid.appauth.internal.Logger
import org.json.JSONException

/**
 * Stores state and handles events related to the authorization management flow. The activity is
 * started by [AuthorizationService.performAuthorizationRequest] or
 * [AuthorizationService.performEndSessionRequest], and records all state pertinent to
 * the authorization management request before invoking the authorization intent. It also functions
 * to control the back stack, ensuring that the authorization activity will not be reachable
 * via the back button after the flow completes.
 *
 *
 * The following diagram illustrates the operation of the activity:
 *
 *
 * ```
 * Back Stack Towards Top
 * +------------------------------------------>
 *
 *
 * +------------+            +---------------+      +----------------+      +--------------+
 * |            |     (1)    |               | (2)  |                | (S1) |              |
 * | Initiating +----------->| Authorization +----->| Authorization  +----->| Redirect URI |
 * |  Activity  |            |  Management   |      |   Activity     |      |   Receiver   |
 * |            |<-----------+   Activity    |<-----+ (e.g. browser) |      |   Activity   |
 * |            | (C2b, S3b) |               | (C1) |                |      |              |
 * +------------+            +-+---+---------+      +----------------+      +-------+------+
 *                           |  |  ^                                              |
 *                           |  |  |                                              |
 *                   +-------+  |  |                      (S2)                    |
 *                   |          |  +----------------------------------------------+
 *                   |          |
 *                   |          v (S3a)
 *             (C2a) |      +------------+
 *                   |      |            |
 *                   |      | Completion |
 *                   |      |  Activity  |
 *                   |      |            |
 *                   |      +------------+
 *                   |
 *                   |      +-------------+
 *                   |      |             |
 *                   +----->| Cancelation |
 *                          |  Activity   |
 *                          |             |
 *                          +-------------+
 * ```
 *
 *
 * The process begins with an activity requesting that an authorization flow be started,
 * using [AuthorizationService.performAuthorizationRequest] or
 * [AuthorizationService.performEndSessionRequest].
 *
 *
 * - Step 1: Using an intent derived from [.createStartIntent], this activity is
 * started. The state delivered in this intent is recorded for future use.
 *
 *
 * - Step 2: The authorization intent, typically a browser tab, is started. At this point,
 * depending on user action, we will either end up in a "completion" flow (S) or
 * "cancelation flow" (C).
 *
 *
 * - Cancelation (C) flow:
 * - Step C1: If the user presses the back button or otherwise causes the authorization
 * activity to finish, the AuthorizationManagementActivity will be recreated or restarted.
 *
 *
 * - Step C2a: If a cancellation PendingIntent was provided in the call to
 * [AuthorizationService.performAuthorizationRequest] or
 * [AuthorizationService.performEndSessionRequest], then this is
 * used to invoke a cancelation activity.
 *
 *
 * - Step C2b: If no cancellation PendingIntent was provided (legacy behavior, or
 * AuthorizationManagementActivity was started with an intent from
 * [AuthorizationService.getAuthorizationRequestIntent] or
 * @link AuthorizationService#performEndOfSessionRequest}), then the
 * AuthorizationManagementActivity simply finishes after calling [android.app.Activity.setResult],
 * with [android.app.Activity.RESULT_CANCELED], returning control to the activity above
 * it in the back stack (typically, the initiating activity).
 *
 *
 * - Completion (S) flow:
 * - Step S1: The authorization activity completes with a success or failure, and sends this
 * result to [RedirectUriReceiverActivity].
 *
 *
 * - Step S2: [RedirectUriReceiverActivity] extracts the forwarded data, and invokes
 * AuthorizationManagementActivity using an intent derived from
 * [.createResponseHandlingIntent]. This intent has flag CLEAR_TOP set, which will
 * result in both the authorization activity and [RedirectUriReceiverActivity] being
 * destroyed, if necessary, such that AuthorizationManagementActivity is once again at the
 * top of the back stack.
 *
 *
 * - Step S3a: If this activity was invoked via
 * [AuthorizationService.performAuthorizationRequest] or
 * [AuthorizationService.performEndSessionRequest], then the pending intent provided
 * for completion of the authorization flow is invoked, providing the decoded
 * [AuthorizationManagementResponse] or [AuthorizationException] as appropriate.
 * The AuthorizationManagementActivity finishes, removing itself from the back stack.
 *
 *
 * - Step S3b: If this activity was invoked via an intent returned by
 * [AuthorizationService.getAuthorizationRequestIntent], then this activity
 * calls [setResult] with [android.app.Activity.RESULT_OK]
 * and a data intent containing the [AuthorizationResponse] or
 * [AuthorizationException] as appropriate.
 * The AuthorizationManagementActivity finishes, removing itself from the back stack.
 */
class AuthorizationManagementActivity : AppCompatActivity() {
    /**
     * Indicates whether the authorization process has started.
     * This flag is used to ensure that the authorization intent is only started once.
     */
    private var authorizationStarted = false

    /**
     * The intent used to initiate the authorization flow.
     * This intent is created based on the authorization request and is used to start the authorization activity.
     */
    private var authIntent: Intent? = null

    /**
     * The authorization management request associated with the current flow.
     * This object contains the details of the authorization request, such as
     * the client ID, redirect URI, and other parameters.
     */
    private var authRequest: AuthorizationManagementRequest? = null

    /**
     * The PendingIntent to be invoked upon successful completion of the authorization flow.
     * This intent is used to deliver the result of the authorization process back to the caller.
     */
    private var completeIntent: PendingIntent? = null

    /**
     * The PendingIntent to be invoked if the authorization flow is canceled.
     * This intent is used to notify the caller that the authorization process was not completed.
     */
    private var cancelIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            extractState(intent.extras)
        } else {
            extractState(savedInstanceState)
        }
    }

    override fun onResume() {
        super.onResume()

        /*
         * If this is the first run of the activity, start the authorization intent.
         * Note that we do not finish the activity at this point, in order to remain on the back
         * stack underneath the authorization activity.
         */
        if (!authorizationStarted) {
            try {
                startActivity(authIntent)
                authorizationStarted = true
            } catch (_: ActivityNotFoundException) {
                handleBrowserNotFound()
                finish()
            }

            return
        }

        /*
         * On a subsequent run, it must be determined whether we have returned to this activity
         * due to an OAuth2 redirect, or the user canceling the authorization flow. This can
         * be done by checking whether a response URI is available, which would be provided by
         * RedirectUriReceiverActivity. If it is not, we have returned here due to the user
         * pressing the back button, or the authorization activity finishing without
         * RedirectUriReceiverActivity having been invoked - this can occur when the user presses
         * the back button, or closes the browser tab.
         */
        if (intent.data != null) {
            handleAuthorizationComplete()
        } else {
            handleAuthorizationCanceled()
        }

        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putBoolean(KEY_AUTHORIZATION_STARTED, authorizationStarted)
            putParcelable(KEY_AUTH_INTENT, authIntent)
            putString(KEY_AUTH_REQUEST, authRequest!!.jsonSerializeString())
            putString(KEY_AUTH_REQUEST_TYPE, requestTypeFor(authRequest!!).name)
            putParcelable(KEY_COMPLETE_INTENT, completeIntent)
            putParcelable(KEY_CANCEL_INTENT, cancelIntent)
        }
    }

    private fun handleAuthorizationComplete() {
        val responseUri: Uri = checkNotNull(intent.data)
        val responseData = extractResponseData(responseUri)
        responseData.data = responseUri
        sendResult(completeIntent, responseData, RESULT_OK)
    }

    private fun handleAuthorizationCanceled() {
        Logger.debug("Authorization flow canceled by user")

        val cancelData = AuthorizationException.fromTemplate(
            AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW,
            null
        ).toIntent()

        sendResult(cancelIntent, cancelData, RESULT_CANCELED)
    }

    private fun handleBrowserNotFound() {
        Logger.debug("Authorization flow canceled due to missing browser")

        val cancelData = AuthorizationException.fromTemplate(
            AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW,
            null
        ).toIntent()

        sendResult(cancelIntent, cancelData, RESULT_CANCELED)
    }

    @Suppress("DEPRECATION")
    private fun extractState(state: Bundle?) {
        if (state == null) {
            Logger.warn("No stored state - unable to handle response")
            finish()
            return
        }

        authIntent = state.getParcelable(KEY_AUTH_INTENT)
        authorizationStarted = state.getBoolean(KEY_AUTHORIZATION_STARTED, false)
        completeIntent = state.getParcelable(KEY_COMPLETE_INTENT)
        cancelIntent = state.getParcelable(KEY_CANCEL_INTENT)

        try {
            val authRequestJson = state.getString(KEY_AUTH_REQUEST, null)
            val authRequestType = state.getString(KEY_AUTH_REQUEST_TYPE, null)

            authRequest = authRequestJson?.let {
                AuthorizationManagementUtil.requestFrom(
                    it,
                    RequestType.valueOf(authRequestType)
                )
            }
        } catch (_: JSONException) {
            sendResult(
                cancelIntent,
                AuthorizationRequestErrors.INVALID_REQUEST.toIntent(),
                RESULT_CANCELED
            )
        }
    }

    /**
     * Sends the result of the authorization flow back to the caller.
     *
     * If a `callback` `PendingIntent` is provided, it is used to send the result.
     * This is the standard mechanism for AppAuth when initiated via
     * [AuthorizationService.performAuthorizationRequest]. If the `PendingIntent` has been
     * canceled, an error is logged.
     *
     * If no `callback` is provided, `setResult` is called on the activity. This is for
     * backward-compatibility with flows started using `startActivityForResult`.
     *
     * @param callback The `PendingIntent` to invoke with the result, if provided.
     * @param cancelData The `Intent` containing the result data.
     * @param resultCode The result code to send back (e.g., `RESULT_OK` or `RESULT_CANCELED`).
     */
    private fun sendResult(callback: PendingIntent?, cancelData: Intent?, resultCode: Int) {
        callback?.let {
            try {
                it.send(this, 0, cancelData)
            } catch (e: PendingIntent.CanceledException) {
                Logger.error("Failed to send cancel intent", e)
            }
        } ?: setResult(resultCode, cancelData)
    }

    /**
     * Extracts response data from the provided URI and converts it into an Intent.
     *
     * This method determines whether the response URI contains an error or a valid response.
     * If the URI contains an error, it creates an Intent representing the error.
     * If the URI contains a valid response, it validates the state parameter (if present)
     * and creates an Intent representing the response.
     *
     * @param responseUri The URI containing the response data from the authorization flow.
     * @return An Intent representing either an error or a valid authorization response.
     */
    private fun extractResponseData(responseUri: Uri): Intent {
        // Check if the response URI contains an error parameter
        if (responseUri.getQueryParameterNames().contains(PARAM_ERROR)) {
            // Create and return an Intent representing the error
            return AuthorizationException.fromOAuthRedirect(responseUri).toIntent()
        }

        // Verify that we have a valid authorization request
        if (authRequest == null) {
            Logger.error("No authorization request found")
            return AuthorizationRequestErrors.INVALID_REQUEST.toIntent()
        }

        // Extract the response using the authorization request and response URI
        val response = responseWith(authRequest!!, responseUri)

        // Validate the state parameter to ensure it matches the original request
        if (authRequest!!.state == null && response.state != null
            || (authRequest!!.state != null && (authRequest!!.state != response.state))
        ) {
            // Log a warning if the state does not match and return an error Intent
            Logger.warn(
                "State returned in authorization response (%s) does not match state "
                        + "from request (%s) - discarding response",
                response.state,
                authRequest!!.state
            )

            return AuthorizationRequestErrors.STATE_MISMATCH.toIntent()
        }

        // Create and return an Intent representing the valid response
        return response.toIntent()
    }

    companion object {
        @VisibleForTesting
        const val KEY_AUTH_INTENT: String = "authIntent"

        @VisibleForTesting
        const val KEY_AUTH_REQUEST: String = "authRequest"

        @VisibleForTesting
        const val KEY_AUTH_REQUEST_TYPE: String = "authRequestType"

        @VisibleForTesting
        const val KEY_COMPLETE_INTENT: String = "completeIntent"

        @VisibleForTesting
        const val KEY_CANCEL_INTENT: String = "cancelIntent"

        @VisibleForTesting
        const val KEY_AUTHORIZATION_STARTED: String = "authStarted"

        /**
         * Creates an intent to start an authorization flow.
         * @param context the package context for the app.
         * @param request the authorization request which is to be sent.
         * @param authIntent the intent to be used to get authorization from the user.
         * @param completeIntent the intent to be sent when the flow completes.
         * @param cancelIntent the intent to be sent when the flow is canceled.
         */
        @JvmStatic
        fun createStartIntent(
            context: Context,
            request: AuthorizationManagementRequest,
            authIntent: Intent?,
            completeIntent: PendingIntent?,
            cancelIntent: PendingIntent?
        ) = createBaseIntent(context).apply {
            putExtra(KEY_AUTH_INTENT, authIntent)
            putExtra(KEY_AUTH_REQUEST, request.jsonSerializeString())
            putExtra(KEY_AUTH_REQUEST_TYPE, requestTypeFor(request).name)
            putExtra(KEY_COMPLETE_INTENT, completeIntent)
            putExtra(KEY_CANCEL_INTENT, cancelIntent)
        }

        /**
         * Creates an intent to start an authorization flow.
         * @param context the package context for the app.
         * @param request the authorization management request which is to be sent.
         * @param authIntent the intent to be used to get authorization from the user.
         */
        @JvmStatic
        fun createStartForResultIntent(
            context: Context,
            request: AuthorizationManagementRequest,
            authIntent: Intent?
        ) = createStartIntent(
            context = context,
            request = request,
            authIntent = authIntent,
            completeIntent = null,
            cancelIntent = null
        )

        /**
         * Creates an intent to handle the completion of an authorization flow. This restores
         * the original AuthorizationManagementActivity that was created at the start of the flow.
         * @param context the package context for the app.
         * @param responseUri the response URI, which carries the parameters describing the response.
         */
        @JvmStatic
        fun createResponseHandlingIntent(
            context: Context,
            responseUri: Uri?
        ) = createBaseIntent(context).apply {
            data = responseUri
            addFlags(FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP)
        }

        /**
         * Creates a base intent to start the [AuthorizationManagementActivity].
         *
         * @param context The context to use for creating the intent.
         * @return An [Intent] configured to start the [AuthorizationManagementActivity].
         */
        private fun createBaseIntent(context: Context) = Intent(
            context,
            AuthorizationManagementActivity::class.java
        )
    }
}
