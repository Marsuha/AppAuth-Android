/*
 * Copyright 2023 The AppAuth for Android Authors. All Rights Reserved.
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

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.appAuthConfiguration
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauth.browser.BrowserDescriptor
import net.openid.appauth.browser.BrowserMatcher
import net.openid.appauth.browser.BrowserSelector
import net.openid.appauth.browser.ExactBrowserMatcher
import net.openid.appauthdemo.LoginActivity.Companion.EXTRA_FAILED
import okio.buffer
import okio.source
import java.io.IOException
import java.nio.charset.Charset

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val authStateManager by lazy { AuthStateManager.init(application) }
    private val configuration by lazy { Configuration.getInstance(application) }
    private val authService: AuthorizationService by lazy {
        AuthorizationService(
            getApplication(),
            appAuthConfiguration {
                connectionBuilder = configuration.connectionBuilder
            }
        )
    }

    private val _uiState = MutableStateFlow<MainState>(MainState.Loading("Initializing"))
    val uiState = _uiState.asStateFlow()

    private var _authRequest: AuthorizationRequest? = null
    private var authCustomTabsIntent = CompletableDeferred<CustomTabsIntent>()


    init {
        viewModelScope.launch {
            if (configuration.hasConfigurationChanged()) {
                signOut()
                return@launch
            }

            if (!configuration.isValid) {
                _uiState.update {
                    MainState.Error(
                        configuration.configurationError ?: "Unknown error",
                        false
                    )
                }

                return@launch
            }

            if (authStateManager.getCurrent().isAuthorized) {
                displayAuthorized()
            } else {
                initializeAppAuth()
            }
        }
    }

    @SuppressLint("WrongThread")
    private suspend fun initializeAppAuth() {
        Log.i(TAG, "Initializing AppAuth")
        _uiState.update { MainState.Loading("Initializing") }

        val serviceConfig = authStateManager.getCurrent().authorizationServiceConfiguration
        if (serviceConfig != null) {
            Log.i(TAG, "auth config already established")
            initializeClient()
            return
        }

        val config = if (configuration.discoveryUri == null) {
            Log.i(TAG, "Creating auth config from res/raw/auth_config.json")
            AuthorizationServiceConfiguration(
                configuration.authEndpointUri!!,
                configuration.tokenEndpointUri!!,
                configuration.registrationEndpointUri,
                configuration.endSessionEndpoint
            )
        } else {
            Log.i(TAG, "Retrieving OpenID discovery doc")
            _uiState.update { MainState.Loading("Retrieving discovery document") }

            try {
                AuthorizationServiceConfiguration.fetchFromUrl(
                    configuration.discoveryUri!!,
                    configuration.connectionBuilder
                )
            } catch (ex: AuthorizationException) {
                Log.e(TAG, "Failed to retrieve discovery document", ex)
                _uiState.update {
                    MainState.Error(
                        "Failed to retrieve discovery document: ${ex.errorDescription}",
                        true
                    )
                }

                return
            }
        }

        authStateManager.replace(AuthState(config))
        initializeClient()
    }

    private suspend fun initializeClient() {
        val lastRegistrationResponse = authStateManager.getCurrent().lastRegistrationResponse

        if (configuration.clientId == null && lastRegistrationResponse == null) {
            Log.i(TAG, "Dynamically registering client")
            _uiState.update { MainState.Loading("Dynamically registering client") }
            val registrationRequest = RegistrationRequest.Builder(
                authStateManager.getCurrent().authorizationServiceConfiguration!!,
                listOf(configuration.redirectUri)
            ).setTokenEndpointAuthenticationMethod(ClientSecretBasic.NAME).build()

            try {
                val response = authService.performRegistrationRequest(registrationRequest)
                authStateManager.updateAfterRegistration(response, null)
            } catch (ex: AuthorizationException) {
                Log.e(TAG, "Failed to dynamically register client", ex)
                _uiState.update {
                    MainState.Error(
                        "Failed to register client: ${ex.errorDescription}",
                        true
                    )
                }

                return
            }
        }

        initializeAuthRequest()
    }

    private suspend fun initializeAuthRequest() {
        configureBrowserSelector()
        val unauthenticatedState = MainState.Unauthenticated(
            loginHint = "",
            browsers = emptyList(),
            selectedBrowserMatcher = AnyBrowserMatcher,
            isPendingIntentMode = false,
            clientId = authStateManager.getCurrent().lastRegistrationResponse?.clientId
                ?: configuration.clientId ?: "",
            authEndpoint = authStateManager.getCurrent().authorizationServiceConfiguration?.authorizationEndpoint.toString(),
            isDynamicClientId = authStateManager.getCurrent().lastRegistrationResponse != null,
        )

        _uiState.update { unauthenticatedState }

        createAuthRequest()
        warmUpBrowser()
    }

    private suspend fun createAuthRequest(loginHint: String? = null) {
        val unauthenticatedState = _uiState.value as? MainState.Unauthenticated ?: return
        val authRequestBuilder = AuthorizationRequest.Builder(
            configuration = authStateManager.getCurrent().authorizationServiceConfiguration!!,
            clientId = unauthenticatedState.clientId,
            responseType = ResponseTypeValues.CODE,
            redirectUri = configuration.redirectUri
        ).setScope(configuration.scope)

        if (!loginHint.isNullOrEmpty()) authRequestBuilder.setLoginHint(loginHint)
        _authRequest = authRequestBuilder.build()
    }

    private suspend fun warmUpBrowser() {
        authCustomTabsIntent = CompletableDeferred()
        Log.i(TAG, "Warming up browser instance for auth request")
        val intentBuilder = authService.createCustomTabsIntentBuilder(_authRequest!!.toUri())
        val colorScheme = CustomTabColorSchemeParams.Builder().build()
        intentBuilder.setDefaultColorSchemeParams(colorScheme)
        authCustomTabsIntent.complete(intentBuilder.build())
    }

    fun onLoginHintChanged(loginHint: String) = viewModelScope.launch {
        val currentState = _uiState.value as? MainState.Unauthenticated ?: return@launch
        _uiState.update { currentState.copy(loginHint = loginHint) }
        createAuthRequest(loginHint)
    }

    fun onStartAuth(context: Context, launcher: ActivityResultLauncher<Intent>) =
        viewModelScope.launch {
            val currentState = _uiState.value as? MainState.Unauthenticated ?: return@launch
            _uiState.update { MainState.Loading("Making authorization request") }

            if (currentState.isPendingIntentMode) {
                val completionIntent = Intent(context, LoginActivity::class.java).apply {
                    addFlags(FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                val cancelIntent = Intent(context, LoginActivity::class.java).apply {
                    putExtra(EXTRA_FAILED, true)
                    addFlags(FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else 0

                authService.performAuthorizationRequest(
                    request = _authRequest!!,
                    completedIntent = PendingIntent.getActivity(
                        context,
                        0,
                        completionIntent,
                        flags
                    ),
                    canceledIntent = PendingIntent.getActivity(
                        context,
                        0,
                        cancelIntent,
                        flags
                    ),
                    customTabsIntent = authCustomTabsIntent.await()
                )
            } else {
                val intent = authService.getAuthorizationRequestIntent(
                    request = _authRequest!!,
                    customTabsIntent = authCustomTabsIntent.await()
                )

                launcher.launch(intent)
            }
        }


    private fun configureBrowserSelector() {
        val context = getApplication<Application>().applicationContext
        val descriptors = BrowserSelector.getAllBrowsers(context)
        val browserInfoList = descriptors.mapNotNull { descriptor ->
            try {
                val appInfo = context.packageManager.getApplicationInfo(descriptor.packageName, 0)
                BrowserInfo(
                    descriptor = descriptor,
                    label = context.packageManager.getApplicationLabel(appInfo),
                    icon = context.packageManager.getApplicationIcon(descriptor.packageName)
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

        (_uiState.value as? MainState.Unauthenticated)?.let { currentState ->
            _uiState.update { currentState.copy(browsers = browserInfoList) }
        }
    }

    fun onBrowserSelected(info: BrowserInfo?) = viewModelScope.launch {
        val currentState = _uiState.value as? MainState.Unauthenticated ?: return@launch
        val browserMatcher =
            info?.let { ExactBrowserMatcher(it.descriptor) } ?: AnyBrowserMatcher
        _uiState.update { currentState.copy(selectedBrowserMatcher = browserMatcher) }
    }

    fun onPendingIntentModeChanged(isPendingIntentMode: Boolean) = viewModelScope.launch {
        val currentState = _uiState.value as? MainState.Unauthenticated ?: return@launch
        _uiState.update { currentState.copy(isPendingIntentMode = isPendingIntentMode) }
    }

    fun handleAuthorizationResponse(intent: Intent) = viewModelScope.launch {
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        authStateManager.updateAfterAuthorization(response, ex)
        when {
            response?.authorizationCode != null -> {
                _uiState.update { MainState.Loading("Exchanging authorization code") }
                exchangeAuthorizationCode(response)
            }

            ex != null -> _uiState.update {
                MainState.Error(
                    "Authorization flow failed: ${ex.errorDescription}",
                    true
                )
            }

            else -> _uiState.update {
                MainState.Error(
                    "No authorization state retained - reauthorization required",
                    true
                )
            }
        }
    }

    private suspend fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        try {
            val tokenResponse =
                performTokenRequest(authorizationResponse.createTokenExchangeRequest())

            authStateManager.updateAfterTokenResponse(tokenResponse, null)
            if (authStateManager.getCurrent().isAuthorized) {
                displayAuthorized()
            } else {
                _uiState.update { MainState.Error("Authorization Code exchange failed", true) }
            }
        } catch (ex: AuthorizationException) {
            _uiState.update {
                MainState.Error(
                    "Failed to exchange authorization code: ${ex.errorDescription}",
                    true
                )
            }
        }
    }

    private suspend fun performTokenRequest(request: TokenRequest): TokenResponse? {
        val clientAuthentication = try {
            authStateManager.getCurrent().clientAuthentication
        } catch (_: ClientAuthentication.UnsupportedAuthenticationMethod) {
            _uiState.update { MainState.Error("Client authentication method is unsupported", true) }
            return null
        }
        return authService.performTokenRequest(request, clientAuthentication)
    }

    private suspend fun displayAuthorized(userInfo: JsonObject? = null) {
        val authState = authStateManager.getCurrent()
        val authenticatedState = MainState.Authenticated(
            accessToken = authState.accessToken,
            accessTokenExpirationTime = authState.accessTokenExpirationTime,
            idToken = authState.idToken,
            refreshToken = authState.refreshToken,
            canRefresh = authState.refreshToken != null,
            canFetchUserInfo = authState.authorizationServiceConfiguration?.discoveryDoc?.userinfoEndpoint != null || configuration.userInfoEndpointUri != null,
            userInfo = userInfo
        )
        _uiState.update { authenticatedState }
    }

    fun refreshAccessToken() = viewModelScope.launch {
        _uiState.update { MainState.Loading("Refreshing access token") }

        try {
            val tokenResponse =
                performTokenRequest(authStateManager.getCurrent().createTokenRefreshRequest())
            authStateManager.updateAfterTokenResponse(tokenResponse, null)
            displayAuthorized()
        } catch (ex: AuthorizationException) {
            _uiState.update {
                MainState.Error(
                    "Failed to refresh token: ${ex.errorDescription}",
                    true
                )
            }
        }
    }

    fun fetchUserInfo() = viewModelScope.launch {
        _uiState.update { MainState.Loading("Fetching user info") }

        authStateManager.getCurrent().performActionWithFreshTokens(
            service = authService,
            action = ::handleFreshTokensForUserInfo
        )
    }

    private suspend fun handleFreshTokensForUserInfo(result: AuthState.FreshTokenResult) {
        when (result) {
            is AuthState.FreshTokenResult.Failure -> {
                Log.e(TAG, "Token refresh failed when fetching user info")
                _uiState.update {
                    MainState.Error(
                        "Failed to refresh token for user info: ${result.exception.errorDescription}",
                        true
                    )
                }
            }

            is AuthState.FreshTokenResult.Success -> {
                val userInfoEndpoint = configuration.userInfoEndpointUri
                    ?: authStateManager.getCurrent().authorizationServiceConfiguration?.discoveryDoc?.userinfoEndpoint?.toString()
                        ?.toUri()

                if (userInfoEndpoint == null) {
                    Log.e(TAG, "User info endpoint is not available")
                    displayAuthorized()
                    return
                }

                try {
                    val response = withContext(Dispatchers.IO) {
                        val conn = configuration.connectionBuilder.openConnection(userInfoEndpoint)
                        conn.setRequestProperty("Authorization", "Bearer ${result.accessToken}")
                        conn.instanceFollowRedirects = false
                        conn.inputStream.source().buffer().readString(Charset.forName("UTF-8"))
                    }

                    val userInfo = Json.parseToJsonElement(response).jsonObject
                    displayAuthorized(userInfo)
                } catch (ioEx: IOException) {
                    Log.e(TAG, "Network error when querying userinfo endpoint", ioEx)
                    displayAuthorized()
                } catch (jsonEx: SerializationException) {
                    Log.e(TAG, "Failed to parse userinfo response", jsonEx)
                    displayAuthorized()
                }
            }
        }
    }

    fun signOut() = viewModelScope.launch {
        val currentState = authStateManager.getCurrent()
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        currentState.lastRegistrationResponse?.let { clearedState.update(it) }
        authStateManager.replace(clearedState)
        initializeAppAuth()
    }

    fun onRetry() {
        viewModelScope.launch {
            initializeAppAuth()
        }
    }

    override fun onCleared() {
        authService.dispose()
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}

sealed class MainState {
    data class Loading(val message: String) : MainState()
    data class Error(val message: String, val recoverable: Boolean) : MainState()
    data class Unauthenticated(
        val loginHint: String,
        val browsers: List<BrowserInfo>,
        val selectedBrowserMatcher: BrowserMatcher,
        val isPendingIntentMode: Boolean,
        val clientId: String,
        val authEndpoint: String,
        val isDynamicClientId: Boolean,
    ) : MainState()

    data class Authenticated(
        val accessToken: String?,
        val accessTokenExpirationTime: Long?,
        val idToken: String?,
        val refreshToken: String?,
        val canRefresh: Boolean,
        val canFetchUserInfo: Boolean,
        val userInfo: JsonObject? = null
    ) : MainState()
}

data class BrowserInfo(
    val descriptor: BrowserDescriptor,
    val label: CharSequence,
    val icon: Drawable?
) {
    val customTabLabel
        @Composable get() = if (descriptor.useCustomTab) {
            stringResource(R.string.custom_tab_label, label)
        } else {
            label.toString()
        }
}
