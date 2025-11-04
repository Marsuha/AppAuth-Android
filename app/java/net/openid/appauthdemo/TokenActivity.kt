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
package net.openid.appauthdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthState.FreshTokenResult
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientAuthentication.UnsupportedAuthenticationMethod
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import okio.buffer
import okio.source
import org.joda.time.format.DateTimeFormat
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

/**
 * Displays the authorized state of the user. This activity is provided with the outcome of the
 * authorization flow, which it uses to negotiate the final authorized state,
 * by performing an authorization code exchange if necessary. After this, the activity provides
 * additional post-authorization operations if available, such as fetching user info and refreshing
 * access tokens.
 */
class TokenActivity : AppCompatActivity() {
    private val authService by lazy {
        AuthorizationService(
            context = this,
            clientConfiguration = AppAuthConfiguration.Builder()
                .setConnectionBuilder(configuration.connectionBuilder)
                .build()
        )
    }
    private val stateManager by lazy { AuthStateManager.getInstance(this) }
    private val userInfoJsonRef = AtomicReference<JSONObject?>()

    private val configuration by lazy { Configuration.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (configuration.hasConfigurationChanged()) {
            Toast.makeText(
                this,
                "Configuration change detected",
                Toast.LENGTH_SHORT
            ).show()

            signOut()
            return
        }

        setContentView(R.layout.activity_token)
        displayLoading("Restoring state...")

        savedInstanceState?.let { state ->
            try {
                state.getString(KEY_USER_INFO)?.let { userInfoJsonRef.set(JSONObject(it)) }
            } catch (ex: JSONException) {
                Log.e(TAG, "Failed to parse saved user info JSON, discarding", ex)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            if (stateManager.getCurrent().isAuthorized) {
                displayAuthorized()
                return@launch
            }

            // the stored AuthState is incomplete, so check if we are currently receiving the result of
            // the authorization flow from the browser.
            val response = AuthorizationResponse.fromIntent(intent)
            val ex = AuthorizationException.fromIntent(intent)

            if (response != null || ex != null) {
                stateManager.updateAfterAuthorization(response, ex)
            }

            when {
                response != null && response.authorizationCode != null -> {
                    // authorization code exchange is required
                    stateManager.updateAfterAuthorization(response, ex)
                    lifecycleScope.launch { exchangeAuthorizationCode(response) }
                }

                ex != null -> {
                    displayNotAuthorized("Authorization flow failed: " + ex.message)
                }

                else -> {
                    displayNotAuthorized("No authorization state retained - reauthorization required")
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // user info is retained to survive activity restarts, such as when rotating the
        // device or switching apps. This isn't essential, but it helps provide a less
        // jarring UX when these events occur - data does not just disappear from the view.
        if (userInfoJsonRef.get() != null) {
            outState.putString(KEY_USER_INFO, userInfoJsonRef.toString())
        }
    }

    override fun onDestroy() {
        authService.dispose()
        super.onDestroy()
        //mExecutor!!.shutdownNow()
    }

    @MainThread
    private fun displayNotAuthorized(explanation: String?) {
        findViewById<View>(R.id.not_authorized).visibility = View.VISIBLE
        findViewById<View>(R.id.authorized).visibility = View.GONE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<TextView>(R.id.explanation).text = explanation
        findViewById<Button>(R.id.reauth).setOnClickListener { signOut() }
    }

    @MainThread
    private fun displayLoading(message: String?) {
        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
        findViewById<View>(R.id.authorized).visibility = View.GONE
        findViewById<View>(R.id.not_authorized).visibility = View.GONE
        findViewById<TextView>(R.id.loading_description).text = message
    }

    @MainThread
    private suspend fun displayAuthorized() {
        findViewById<View>(R.id.authorized).visibility = View.VISIBLE
        findViewById<View>(R.id.not_authorized).visibility = View.GONE
        findViewById<View>(R.id.loading_container).visibility = View.GONE

        val state = stateManager.getCurrent()
        val refreshTokenInfoView = findViewById<TextView>(R.id.refresh_token_info)

        refreshTokenInfoView.setText(
            if (state.refreshToken == null)
                R.string.no_refresh_token_returned
            else
                R.string.refresh_token_returned
        )

        val idTokenInfoView = findViewById<TextView>(R.id.id_token_info)

        idTokenInfoView.setText(
            if ((state.idToken) == null)
                R.string.no_id_token_returned
            else
                R.string.id_token_returned
        )

        val accessTokenInfoView = findViewById<TextView>(R.id.access_token_info)

        if (state.accessToken == null) {
            accessTokenInfoView.setText(R.string.no_access_token_returned)
        } else {
            val expiresAt = state.accessTokenExpirationTime

            when {
                expiresAt == null -> {
                    accessTokenInfoView.setText(R.string.no_access_token_expiry)
                }

                expiresAt < System.currentTimeMillis() -> {
                    accessTokenInfoView.setText(R.string.access_token_expired)
                }

                else -> {
                    val template = resources.getString(R.string.access_token_expires_at)

                    accessTokenInfoView.text = String.format(
                        template,
                        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss ZZ").print(expiresAt)
                    )
                }
            }
        }

        val refreshTokenButton = findViewById<Button>(R.id.refresh_token)
        refreshTokenButton.visibility = state.refreshToken?.let { View.VISIBLE } ?: View.GONE
        refreshTokenButton.setOnClickListener { lifecycleScope.launch { refreshAccessToken() } }

        val viewProfileButton = findViewById<Button>(R.id.view_profile)
        val discoveryDoc = state.authorizationServiceConfiguration?.discoveryDoc

        if ((discoveryDoc == null || discoveryDoc.userinfoEndpoint == null)
            && configuration.userInfoEndpointUri == null
        ) {
            viewProfileButton.visibility = View.GONE
        } else {
            viewProfileButton.visibility = View.VISIBLE
            viewProfileButton.setOnClickListener { fetchUserInfo() }
        }

        findViewById<Button>(R.id.sign_out).setOnClickListener {
            lifecycleScope.launch { endSession() }
        }

        val userInfoCard = findViewById<View>(R.id.userinfo_card)
        val userInfo = userInfoJsonRef.get()

        if (userInfo == null) {
            userInfoCard.visibility = View.INVISIBLE
        } else {
            try {
                var name = "???"
                if (userInfo.has("name")) name = userInfo.getString("name")
                findViewById<TextView>(R.id.userinfo_name).text = name

                if (userInfo.has("picture")) {
                    Glide.with(this@TokenActivity)
                        .load(userInfo.getString("picture").toUri())
                        .fitCenter()
                        .into(findViewById(R.id.userinfo_profile))
                }

                findViewById<TextView>(R.id.userinfo_json).text = userInfoJsonRef.toString()
                userInfoCard.visibility = View.VISIBLE
            } catch (ex: JSONException) {
                Log.e(TAG, "Failed to read userinfo JSON", ex)
            }
        }
    }

    @MainThread
    private suspend fun refreshAccessToken() {
        displayLoading("Refreshing access token")

        try {
            val response = performTokenRequest(
                stateManager.getCurrent().createTokenRefreshRequest(),
            )

            response?.let { handleAccessTokenResponse(it, null) }
        } catch (ex: AuthorizationException) {
            handleAccessTokenResponse(null, ex)
        }
    }

    @MainThread
    private suspend fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        displayLoading("Exchanging authorization code")

        try {
            val response = performTokenRequest(
                authorizationResponse.createTokenExchangeRequest()
            )

            response?.let { handleCodeExchangeResponse(it, null) }
        } catch (ex: AuthorizationException) {
            handleCodeExchangeResponse(null, ex)
        }
    }

    @MainThread
    private suspend fun performTokenRequest(request: TokenRequest): TokenResponse? {
        val clientAuthentication: ClientAuthentication?

        try {
            clientAuthentication = stateManager.getCurrent().clientAuthentication
        } catch (ex: UnsupportedAuthenticationMethod) {
            Log.d(
                TAG,
                "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)",
                ex
            )

            displayNotAuthorized("Client authentication method is unsupported")
            return null
        }

        return authService.performTokenRequest(request, clientAuthentication)
    }

    private suspend fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        stateManager.updateAfterTokenResponse(tokenResponse, authException)
        displayAuthorized()
    }

    private suspend fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) = withContext(Dispatchers.IO) {
        stateManager.updateAfterTokenResponse(tokenResponse, authException)

        if (!stateManager.getCurrent().isAuthorized) {
            val message = "Authorization Code exchange failed ${(authException?.error ?: "")}"

            // WrongThread inference is incorrect for lambdas
            displayNotAuthorized(message)
        } else {
            displayAuthorized()
        }
    }

    /**
     * Demonstrates the use of [AuthState.performActionWithFreshTokens] to retrieve user info from
     * the IDP's user info endpoint. This method will negotiate a new access token and id token if
     * necessary for the follow-up action.
     */
    @MainThread
    private fun fetchUserInfo() {
        displayLoading("Fetching user info")
        lifecycleScope.launch {
            stateManager.getCurrent()
                .performActionWithFreshTokens(service = authService, action = ::fetchUserInfo)
        }
    }

    @Suppress("unused")
    @MainThread
    private suspend fun fetchUserInfo(result: FreshTokenResult) {
        when (result) {
            is FreshTokenResult.Failure -> {
                Log.e(TAG, "Token refresh failed when fetching user info")
                userInfoJsonRef.set(null)
                return displayAuthorized()
            }

            is FreshTokenResult.Success -> {
                val discovery = stateManager.getCurrent()
                    .authorizationServiceConfiguration!!
                    .discoveryDoc

                val userInfoEndpoint = if (configuration.userInfoEndpointUri != null)
                    configuration.userInfoEndpointUri.toString().toUri()
                else
                    discovery!!.userinfoEndpoint.toString().toUri()

                try {
                    val conn = configuration.connectionBuilder.openConnection(userInfoEndpoint)
                    conn.setRequestProperty("Authorization", "Bearer ${result.accessToken}")
                    conn.instanceFollowRedirects = false

                    val response = withContext(Dispatchers.IO) {
                        conn.inputStream.source().buffer()
                            .readString(Charset.forName("UTF-8"))
                    }

                    userInfoJsonRef.set(JSONObject(response))
                } catch (ioEx: IOException) {
                    Log.e(TAG, "Network error when querying userinfo endpoint", ioEx)
                    showSnackbar("Fetching user info failed")
                } catch (_: JSONException) {
                    Log.e(TAG, "Failed to parse userinfo response")
                    showSnackbar("Failed to parse user info")
                }

                displayAuthorized()
            }
        }
    }

    private fun displayEndSessionCancelled() {
        Snackbar.make(
            findViewById(R.id.coordinator),
            "Sign out canceled",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    @MainThread
    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(R.id.coordinator),
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    @MainThread
    private suspend fun endSession() {
        val currentState = stateManager.getCurrent()
        val config = currentState.authorizationServiceConfiguration
        if (config!!.endSessionEndpoint != null) {
            val endSessionIntent = authService.getEndSessionRequestIntent(
                EndSessionRequest.Builder(config)
                    .setIdTokenHint(currentState.idToken)
                    .setPostLogoutRedirectUri(configuration.endSessionRedirectUri)
                    .build()
            )

            registerForActivityResult(StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    signOut()
                    finish()
                } else {
                    displayEndSessionCancelled()
                }
            }.launch(endSessionIntent)
        } else {
            signOut()
        }
    }

    @MainThread
    private fun signOut() = lifecycleScope.launch {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState = stateManager.getCurrent()
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        currentState.lastRegistrationResponse?.let { clearedState.update(it) }
        stateManager.replace(clearedState)

        val mainIntent = Intent(this@TokenActivity, LoginActivity::class.java)
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(mainIntent)
        finish()
    }

    companion object {
        private const val TAG = "TokenActivity"

        private const val KEY_USER_INFO = "userInfo"
    }
}
