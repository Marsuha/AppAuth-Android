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

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.truth.content.IntentSubject
import net.openid.appauth.AuthorizationException.AuthorizationRequestErrors
import net.openid.appauth.AuthorizationException.Companion.fromIntent
import net.openid.appauth.TestValues.testAuthRequest
import net.openid.appauth.TestValues.testAuthRequestBuilder
import net.openid.appauth.TestValues.testEndSessionRequest
import net.openid.appauth.TestValues.testEndSessionRequestBuilder
import net.openid.appauth.TestValues.testServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthorizationManagementActivityTest {
    private lateinit var context: Context
    private lateinit var authRequest: AuthorizationRequest
    private lateinit var endSessionRequest: EndSessionRequest
    private lateinit var authIntent: Intent
    private lateinit var completeIntent: Intent
    private lateinit var completePendingIntent: PendingIntent
    private lateinit var cancelIntent: Intent
    private lateinit var cancelPendingIntent: PendingIntent
    private lateinit var startAuthIntentWithPendings: Intent
    private lateinit var startAuthIntentWithPendingsWithoutCancel: Intent
    private lateinit var startAuthForResultIntent: Intent
    private lateinit var endSessionIntentWithPendings: Intent
    private lateinit var endSessionIntentWithPendingsWithoutCancel: Intent
    private lateinit var endSessionForResultIntent: Intent
    private lateinit var controller: ActivityController<AuthorizationManagementActivity>
    private lateinit var activity: AuthorizationManagementActivity
    private lateinit var activityShadow: ShadowActivity
    private lateinit var successAuthRedirect1: Uri
    private lateinit var errorAuthRedirect: Uri
    private lateinit var successEndSessionRedirect: Uri
    private lateinit var errorEndSessionRedirect: Uri

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        authRequest = testAuthRequest
        endSessionRequest = testEndSessionRequest
        authIntent = Intent("AUTH")
        completeIntent = Intent("COMPLETE")
        completePendingIntent = PendingIntent.getActivity(context, 0, completeIntent, 0)
        cancelIntent = Intent("CANCEL")
        cancelPendingIntent = PendingIntent.getActivity(context, 0, cancelIntent, 0)

        startAuthIntentWithPendings =
            createStartIntentWithPendingIntents(authRequest, cancelPendingIntent)

        startAuthIntentWithPendingsWithoutCancel =
            createStartIntentWithPendingIntents(authRequest, null)

        startAuthForResultIntent = createStartForResultIntent(authRequest)

        endSessionIntentWithPendings =
            createStartIntentWithPendingIntents(endSessionRequest, cancelPendingIntent)
        endSessionIntentWithPendingsWithoutCancel =
            createStartIntentWithPendingIntents(endSessionRequest, null)
        endSessionForResultIntent =
            createStartForResultIntent(endSessionRequest)

        successAuthRedirect1 = authRequest.redirectUri.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, authRequest.state)
            .appendQueryParameter(AuthorizationResponse.KEY_AUTHORIZATION_CODE, "12345")
            .build()

        assertNotNull(endSessionRequest.postLogoutRedirectUri)

        successEndSessionRedirect = endSessionRequest.postLogoutRedirectUri!!.buildUpon()
            .appendQueryParameter(EndSessionResponse.KEY_STATE, endSessionRequest.state)
            .build()

        errorAuthRedirect = authRequest.redirectUri.buildUpon()
            .appendQueryParameter(
                AuthorizationException.PARAM_ERROR,
                AuthorizationRequestErrors.ACCESS_DENIED.error
            )
            .appendQueryParameter(
                AuthorizationException.PARAM_ERROR_DESCRIPTION,
                AuthorizationRequestErrors.ACCESS_DENIED.errorDescription
            )
            .build()

        errorEndSessionRedirect = endSessionRequest.postLogoutRedirectUri!!.buildUpon()
            .appendQueryParameter(
                AuthorizationException.PARAM_ERROR,
                AuthorizationRequestErrors.ACCESS_DENIED.error
            )
            .appendQueryParameter(
                AuthorizationException.PARAM_ERROR_DESCRIPTION,
                AuthorizationRequestErrors.ACCESS_DENIED.errorDescription
            )
            .build()

        instantiateActivity(startAuthIntentWithPendings)
    }

    private fun createStartIntentWithPendingIntents(
        authRequest: AuthorizationManagementRequest,
        cancelIntent: PendingIntent?
    ): Intent {
        return AuthorizationManagementActivity.createStartIntent(
            context,
            authRequest,
            authIntent,
            completePendingIntent,
            cancelIntent
        )
    }

    private fun createStartForResultIntent(
        authRequest: AuthorizationManagementRequest
    ): Intent {
        return AuthorizationManagementActivity.createStartForResultIntent(
            context,
            authRequest,
            authIntent
        )
    }

    private fun instantiateActivity(managementIntent: Intent) {
        controller = Robolectric.buildActivity(
            AuthorizationManagementActivity::class.java,
            managementIntent
        )

        activity = controller.get()
        activityShadow = Shadows.shadowOf(activity)
    }

    @Test
    fun testLoginSuccessFlow_withPendingIntentsAndWithoutDestroy_shouldSendCompleteIntent() {
        // start the flow
        instantiateActivity(startAuthIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // on completion of the authorization activity, the result will be forwarded to the
        // management activity via newIntent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successAuthRedirect1
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        val nextStartedActivity = activityShadow.nextStartedActivity

        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(successAuthRedirect1)

        IntentSubject.assertThat(nextStartedActivity).extras()
            .containsKey(AuthorizationResponse.EXTRA_RESPONSE)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndOfSessionSuccessFlow_withPendingIntentsAndWithoutDestroy_shouldSendCompleteIntent() {
        // start the flow
        instantiateActivity(endSessionIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // on completion of the authorization activity, the result will be forwarded to the
        // management activity via newIntent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successEndSessionRedirect
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        val nextStartedActivity = activityShadow.nextStartedActivity

        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(successEndSessionRedirect)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginSuccessFlow_withPendingIntentsAndNoState() {
        val request = testAuthRequestBuilder
            .setState(null)
            .build()

        emulateFlowToAuthorizationActivityLaunch(
            createStartIntentWithPendingIntents(request, cancelPendingIntent)
        )

        val successAuthRedirect = authRequest.redirectUri.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_AUTHORIZATION_CODE, "12345")
            .build()

        val nextStartedActivity = emulateAuthorizationResponseReceived(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successAuthRedirect
            )
        )

        // after which the completion intent should be fired
        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(successAuthRedirect)

        IntentSubject.assertThat(nextStartedActivity).extras()
            .containsKey(AuthorizationResponse.EXTRA_RESPONSE)

        IntentSubject.assertThat(nextStartedActivity).extras()
            .doesNotContainKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionSuccessFlow_withPendingIntentsAndNoState() {
        val request = testEndSessionRequestBuilder
            .setState(null)
            .build()

        emulateFlowToAuthorizationActivityLaunch(
            createStartIntentWithPendingIntents(request, cancelPendingIntent)
        )

        val authResponseUri = request.postLogoutRedirectUri

        val nextStartedActivity = emulateAuthorizationResponseReceived(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        // after which the completion intent should be fired
        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(authResponseUri)

        IntentSubject.assertThat(nextStartedActivity).extras()
            .doesNotContainKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginSuccessFlow_withoutPendingIntentsAndWithoutDestroy_shouldSetResult() {
        // start the flow
        instantiateActivity(startAuthForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // on completion of the authorization activity, the result will be forwarded to the
        // management activity via newIntent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successAuthRedirect1
            )
        )

        // the management activity is then resumed
        controller.resume()

        // and then sets a result before finishing as there is no completion intent
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_OK)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionSuccessFlow_withoutPendingIntentsAndWithoutDestroy_shouldSetResult() {
        // start the flow
        instantiateActivity(endSessionForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // on completion of the authorization activity, the result will be forwarded to the
        // management activity via newIntent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successEndSessionRedirect
            )
        )

        // the management activity is then resumed
        controller.resume()

        // and then sets a result before finishing as there is no completion intent
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_OK)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginSuccessFlow_withPendingIntentsAndWithDestroy_shouldSendCompleteIntent() {
        // start the flow
        instantiateActivity(startAuthIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // on a device under memory pressure, the management activity will be destroyed, but
        // it will be able to save its state
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState).destroy()

        // on completion of the authorization activity, a new management activity will be created
        instantiateActivity(startAuthIntentWithPendings)
        controller.create(savedState).start()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successAuthRedirect1
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        val nextStartedActivity = activityShadow.nextStartedActivity

        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(successAuthRedirect1)

        IntentSubject.assertThat(nextStartedActivity).extras()
            .containsKey(AuthorizationResponse.EXTRA_RESPONSE)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionSuccessFlow_withPendingIntentsAndWithDestroy_shouldSendCompleteIntent() {
        // start the flow
        instantiateActivity(endSessionIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // on a device under memory pressure, the management activity will be destroyed, but
        // it will be able to save its state
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState).destroy()

        // on completion of the authorization activity, a new management activity will be created
        instantiateActivity(startAuthIntentWithPendings)
        controller.create(savedState).start()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successAuthRedirect1
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        val nextStartedActivity = activityShadow.nextStartedActivity
        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(successAuthRedirect1)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginSuccessFlow_withoutPendingIntentsAndWithDestroy_shouldSetResult() {
        // start the flow
        instantiateActivity(startAuthForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // on a device under memory pressure, the management activity will be destroyed, but
        // it will be able to save its state
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState).destroy()

        // on completion of the authorization activity, a new management activity will be created
        instantiateActivity(startAuthIntentWithPendings)
        controller.create(savedState).start()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successAuthRedirect1
            )
        )

        // the management activity is then resumed
        controller.resume()

        // and then sets a result before finishing as there is no completion intent
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_OK)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionSuccessFlow_withoutPendingIntentsAndWithDestroy_shouldSetResult() {
        // start the flow
        instantiateActivity(endSessionForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // on a device under memory pressure, the management activity will be destroyed, but
        // it will be able to save its state
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState).destroy()

        // on completion of the authorization activity, a new management activity will be created
        instantiateActivity(startAuthIntentWithPendings)
        controller.create(savedState).start()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                successEndSessionRedirect
            )
        )

        // the management activity is then resumed
        controller.resume()

        // and then sets a result before finishing as there is no completion intent
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_OK)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginFailureFlow_withPendingIntentsAndWithoutDestroy_shouldSendCompleteIntent() {
        // start the flow
        instantiateActivity(startAuthIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                errorAuthRedirect
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        val nextStartedActivity = activityShadow.nextStartedActivity
        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(errorAuthRedirect)

        IntentSubject.assertThat(nextStartedActivity).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionFailureFlow_withPendingIntentsAndWithoutDestroy_shouldSendCompleteIntent() {
        // start the flow
        instantiateActivity(endSessionIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                errorEndSessionRedirect
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        val nextStartedActivity = activityShadow.nextStartedActivity
        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(errorEndSessionRedirect)

        IntentSubject.assertThat(nextStartedActivity).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginFailureFlow_withoutPendingIntentsAndWithoutDestroy_shouldSetResult() {
        // start the flow
        instantiateActivity(startAuthForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                errorAuthRedirect
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_OK)
        val resultIntent = activityShadow.resultIntent
        assertThat(resultIntent.data).isEqualTo(errorAuthRedirect)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionFailureFlow_withoutPendingIntentsAndWithoutDestroy_shouldSetResult() {
        // start the flow
        instantiateActivity(endSessionForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                errorAuthRedirect
            )
        )

        // the management activity is then resumed
        controller.resume()

        // after which the completion intent should be fired
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_OK)
        val resultIntent = activityShadow.resultIntent
        assertThat(resultIntent.data).isEqualTo(errorEndSessionRedirect)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginMismatchedState_withPendingIntentsAndResponseDiffersFromRequest() {
        emulateFlowToAuthorizationActivityLaunch(startAuthIntentWithPendings)

        val authResponseUri = authRequest.redirectUri.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, "differentState")
            .appendQueryParameter(AuthorizationResponse.KEY_AUTHORIZATION_CODE, "12345")
            .build()

        val nextStartedActivity = emulateAuthorizationResponseReceived(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        // the next activity should be from the completion intent, carrying an error
        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(authResponseUri)
        IntentSubject.assertThat(nextStartedActivity).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(fromIntent(nextStartedActivity))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testEndSessionMismatchedState_withPendingIntentsAndResponseDiffersFromRequest() {
        emulateFlowToAuthorizationActivityLaunch(endSessionIntentWithPendings)

        assertNotNull(endSessionRequest.postLogoutRedirectUri)

        val authResponseUri = endSessionRequest.postLogoutRedirectUri!!.buildUpon()
            .appendQueryParameter(EndSessionResponse.KEY_STATE, "differentState")
            .build()

        val nextStartedActivity = emulateAuthorizationResponseReceived(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        // the next activity should be from the completion intent, carrying an error
        IntentSubject.assertThat(nextStartedActivity).hasAction("COMPLETE")
        IntentSubject.assertThat(nextStartedActivity).hasData(authResponseUri)
        IntentSubject.assertThat(nextStartedActivity).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(fromIntent(nextStartedActivity))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testLoginMismatchedState_withoutPendingIntentsAndResponseDiffersFromRequest() {
        emulateFlowToAuthorizationActivityLaunch(startAuthForResultIntent)

        val authResponseUri = authRequest.redirectUri.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, "differentState")
            .appendQueryParameter(AuthorizationResponse.KEY_AUTHORIZATION_CODE, "12345")
            .build()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        // the management activity is then resumed
        controller.resume()

        // no completion intent, so exception should be passed to calling activity
        // via the result intent supplied to setResult
        val resultIntent = activityShadow.resultIntent

        IntentSubject.assertThat(resultIntent).hasData(authResponseUri)
        IntentSubject.assertThat(resultIntent).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(fromIntent(resultIntent))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testEndSessionMismatchedState_withoutPendingIntentsAndResponseDiffersFromRequest() {
        emulateFlowToAuthorizationActivityLaunch(endSessionForResultIntent)

        assertNotNull(endSessionRequest.postLogoutRedirectUri)

        val authResponseUri = endSessionRequest.postLogoutRedirectUri!!.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, "differentState")
            .build()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        // the management activity is then resumed
        controller.resume()

        // no completion intent, so exception should be passed to calling activity
        // via the result intent supplied to setResult
        val resultIntent = activityShadow.resultIntent

        IntentSubject.assertThat(resultIntent).hasData(authResponseUri)
        IntentSubject.assertThat(resultIntent).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(fromIntent(resultIntent))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testLoginMismatchedState_withPendingIntentsAndNoStateInRequestWithStateInResponse() {
        val request = AuthorizationRequest.Builder(
            testServiceConfig,
            TestValues.TEST_CLIENT_ID,
            ResponseTypeValues.CODE,
            TestValues.TEST_APP_REDIRECT_URI
        ).setState(null)
            .build()

        val startIntent = createStartIntentWithPendingIntents(request, cancelPendingIntent)
        emulateFlowToAuthorizationActivityLaunch(startIntent)

        val authResponseUri = authRequest.redirectUri.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, "differentState")
            .appendQueryParameter(AuthorizationResponse.KEY_AUTHORIZATION_CODE, "12345")
            .build()

        // the next activity should be from the completion intent, carrying an error
        val nextStartedActivity = emulateAuthorizationResponseReceived(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        assertThat(fromIntent(nextStartedActivity))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testEndSessionMismatchedState_withPendingIntentsAndNoStateInRequestWithStateInResponse() {
        val request = EndSessionRequest.Builder(testServiceConfig)
            .setIdTokenHint(TestValues.TEST_ID_TOKEN)
            .setPostLogoutRedirectUri(TestValues.TEST_APP_REDIRECT_URI)
            .setState("state")
            .build()

        val startIntent = createStartIntentWithPendingIntents(request, cancelPendingIntent)
        emulateFlowToAuthorizationActivityLaunch(startIntent)

        assertNotNull(endSessionRequest.postLogoutRedirectUri)

        val authResponseUri = endSessionRequest.postLogoutRedirectUri!!.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, "differentState")
            .build()

        // the next activity should be from the completion intent, carrying an error
        val nextStartedActivity = emulateAuthorizationResponseReceived(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        assertThat(fromIntent(nextStartedActivity))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testLoginMismatchedState_withoutPendingIntentsAndNoStateInRequestWithStateInResponse() {
        val request = AuthorizationRequest.Builder(
            testServiceConfig,
            TestValues.TEST_CLIENT_ID,
            ResponseTypeValues.CODE,
            TestValues.TEST_APP_REDIRECT_URI
        )
            .setState(null)
            .build()

        val startIntent = createStartForResultIntent(request)
        emulateFlowToAuthorizationActivityLaunch(startIntent)

        val authResponseUri = authRequest.redirectUri.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, "differentState")
            .appendQueryParameter(AuthorizationResponse.KEY_AUTHORIZATION_CODE, "12345")
            .build()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        // the management activity is then resumed
        controller.resume()
        val resultIntent = activityShadow.resultIntent

        assertThat(fromIntent(resultIntent))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testEndSessionMismatchedState_withoutPendingIntentsAndNoStateInRequestWithStateInResponse() {
        val request = EndSessionRequest.Builder(testServiceConfig)
            .setIdTokenHint(TestValues.TEST_ID_TOKEN)
            .setPostLogoutRedirectUri(TestValues.TEST_APP_REDIRECT_URI)
            .setState("state")
            .build()

        val startIntent = createStartForResultIntent(request)
        emulateFlowToAuthorizationActivityLaunch(startIntent)

        assertNotNull(endSessionRequest.postLogoutRedirectUri)

        val authResponseUri = endSessionRequest.postLogoutRedirectUri!!.buildUpon()
            .appendQueryParameter(AuthorizationResponse.KEY_STATE, "differentState")
            .build()

        // the authorization redirect will be forwarded via a new intent
        controller.newIntent(
            AuthorizationManagementActivity.createResponseHandlingIntent(
                context,
                authResponseUri
            )
        )

        // the management activity is then resumed
        controller.resume()
        val resultIntent = activityShadow.resultIntent

        assertThat(fromIntent(resultIntent))
            .isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

    @Test
    fun testLoginCancelFlow_withPendingIntentsAndWithoutDestroy() {
        // start the flow
        instantiateActivity(startAuthIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNotNull()

        // the management activity will be paused while this auth intent is running
        controller.pause()

        // when the user cancels the auth intent, the management activity will be resumed
        controller.resume()

        // at which point the cancel intent should be fired
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("CANCEL")
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionCancelFlow_withPendingIntentsAndWithoutDestroy() {
        // start the flow
        instantiateActivity(endSessionIntentWithPendings)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNotNull()

        // the management activity will be paused while this auth intent is running
        controller.pause()

        // when the user cancels the auth intent, the management activity will be resumed
        controller.resume()

        // at which point the cancel intent should be fired
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("CANCEL")
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginCancelFlow_withoutPendingIntentsAndWithoutDestroy() {
        // start the flow
        instantiateActivity(startAuthForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNotNull()

        // the management activity will be paused while this auth intent is running
        controller.pause()

        // when the user cancels the auth intent, the management activity will be resumed
        controller.resume()

        // at which point the cancel intent should be fired
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_CANCELED)

        val resultIntent = activityShadow.resultIntent

        IntentSubject.assertThat(resultIntent).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(fromIntent(resultIntent))
            .isEqualTo(AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionCancelFlow_withoutPendingIntentsAndWithoutDestroy() {
        // start the flow
        instantiateActivity(endSessionForResultIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNotNull()

        // the management activity will be paused while this auth intent is running
        controller.pause()

        // when the user cancels the auth intent, the management activity will be resumed
        controller.resume()

        // at which point the cancel intent should be fired
        assertThat(activityShadow.resultCode).isEqualTo(RESULT_CANCELED)

        val resultIntent = activityShadow.resultIntent

        IntentSubject.assertThat(resultIntent).extras()
            .containsKey(AuthorizationException.EXTRA_EXCEPTION)

        assertThat(fromIntent(resultIntent))
            .isEqualTo(AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW)

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testLoginCancelFlow_withCompletionIntentButNoCancelIntent() {
        // start the flow
        instantiateActivity(startAuthIntentWithPendingsWithoutCancel)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNotNull()

        // the management activity will be paused while this auth intent is running
        controller.pause()

        // when the user cancels the auth intent, the management activity will be resumed
        controller.resume()

        // as there is no cancel intent, the activity simply finishes
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNull()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun testEndSessionCancelFlow_withCompletionIntentButNoCancelIntent() {
        // start the flow
        instantiateActivity(endSessionIntentWithPendingsWithoutCancel)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNotNull()

        // the management activity will be paused while this auth intent is running
        controller.pause()

        // when the user cancels the auth intent, the management activity will be resumed
        controller.resume()

        // as there is no cancel intent, the activity simply finishes
        IntentSubject.assertThat(activityShadow.nextStartedActivity).isNull()
        assertThat(activity.isFinishing).isTrue()
    }

    private fun emulateFlowToAuthorizationActivityLaunch(startIntent: Intent) {
        // start the flow
        instantiateActivity(startIntent)
        controller.create().start().resume()

        // an activity should be started for auth
        IntentSubject.assertThat(activityShadow.nextStartedActivity).hasAction("AUTH")

        // the management activity will be paused while the authorization flow is running.
        // if there is no memory pressure, the activity will remain in the paused state.
        controller.pause()
    }

    private fun emulateAuthorizationResponseReceived(relaunchIntent: Intent?): Intent {
        if (relaunchIntent != null) {
            // the authorization redirect will be forwarded via a new intent
            controller.newIntent(relaunchIntent)
        }

        // the management activity is then resumed
        controller.resume()

        return activityShadow.nextStartedActivity
    }

    @Suppress("unused")
    private fun assertIsStateMismatchError(intent: Intent, responseUri: Uri) {
        IntentSubject.assertThat(intent).hasData(responseUri)
        IntentSubject.assertThat(intent).extras().containsKey(AuthorizationException.EXTRA_EXCEPTION)
        assertThat(fromIntent(intent)).isEqualTo(AuthorizationRequestErrors.STATE_MISMATCH)
    }

}
