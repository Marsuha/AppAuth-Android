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
 * express or implied. See the License for the 'specific language governing permissions and
 * limitations under the License.
 */
package net.openid.appauthdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauthdemo.ui.theme.AppAuthDemoTheme

class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private val authResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        result.data?.let {
            viewModel.handleAuthorizationResponse(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppAuthDemoTheme {
                val uiState by viewModel.uiState.collectAsState()
                LoginScreen(
                    uiState = uiState,
                    onLoginHintChanged = viewModel::onLoginHintChanged,
                    onStartAuth = { viewModel.onStartAuth(this, authResultLauncher) },
                    onBrowserSelected = viewModel::onBrowserSelected,
                    onPendingIntentModeChanged = viewModel::onPendingIntentModeChanged,
                    onRefreshAccessToken = viewModel::refreshAccessToken,
                    onFetchUserInfo = viewModel::fetchUserInfo,
                    onSignOut = viewModel::signOut,
                    onRetry = viewModel::onRetry
                )

            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.extras != null && !intent.extras!!.isEmpty) {
            viewModel.handleAuthorizationResponse(intent)
        }
    }

    companion object {
        const val EXTRA_FAILED = "net.openid.appauthdemo.failed"
    }
}

@Composable
private fun LoginScreen(
    modifier: Modifier = Modifier,
    uiState: MainState,
    onLoginHintChanged: (String) -> Unit,
    onStartAuth: () -> Unit,
    onBrowserSelected: (BrowserInfo?) -> Unit,
    onPendingIntentModeChanged: (Boolean) -> Unit,
    onRefreshAccessToken: () -> Unit,
    onFetchUserInfo: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.appauth_96dp),
                contentDescription = stringResource(id = R.string.openid_logo_content_description),
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = stringResource(id = R.string.intro_header),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (uiState) {
                is MainState.Authenticated -> TokenContainer(
                    uiState = uiState,
                    onSignOut = onSignOut,
                    onRefreshAccessToken = onRefreshAccessToken,
                    onFetchUserInfo = onFetchUserInfo
                )

                is MainState.Unauthenticated -> {
                    LoginContainer(
                        uiState = uiState,
                        onLoginHintChanged = onLoginHintChanged,
                        onStartAuth = onStartAuth,
                        onBrowserSelected = onBrowserSelected,
                        onPendingIntentModeChanged = onPendingIntentModeChanged
                    )
                }

                is MainState.Loading -> {
                    Text(text = uiState.message)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is MainState.Error -> {
                    Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
                    if (uiState.recoverable) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = stringResource(id = R.string.retry_label))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginScreenLoadingPreview() {
    AppAuthDemoTheme {
        LoginScreen(
            uiState = MainState.Loading("Loading..."),
            onLoginHintChanged = {},
            onStartAuth = {},
            onBrowserSelected = {},
            onPendingIntentModeChanged = {},
            onRefreshAccessToken = {},
            onFetchUserInfo = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun LoginScreenErrorPreview() {
    AppAuthDemoTheme {
        LoginScreen(
            uiState = MainState.Error("Something went wrong", true),
            onLoginHintChanged = {},
            onStartAuth = {},
            onBrowserSelected = {},
            onPendingIntentModeChanged = {},
            onRefreshAccessToken = {},
            onFetchUserInfo = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun LoginScreenUnauthenticatedPreview() {
    AppAuthDemoTheme {
        LoginScreen(
            uiState = MainState.Unauthenticated(
                loginHint = "",
                browsers = emptyList(),
                selectedBrowserMatcher = AnyBrowserMatcher,
                isPendingIntentMode = false,
                clientId = "clientId",
                authEndpoint = "authEndpoint",
                isDynamicClientId = false
            ),
            onLoginHintChanged = {},
            onStartAuth = {},
            onBrowserSelected = {},
            onPendingIntentModeChanged = {},
            onRefreshAccessToken = {},
            onFetchUserInfo = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun LoginScreenAuthenticatedPreview() {
    AppAuthDemoTheme {
        LoginScreen(
            uiState = MainState.Authenticated(
                accessToken = "accessToken",
                accessTokenExpirationTime = 0L,
                idToken = "idToken",
                refreshToken = "refreshToken",
                canRefresh = true,
                canFetchUserInfo = true,
                userInfo = Json.parseToJsonElement("""{"name": "test"}""") as JsonObject
            ),
            onLoginHintChanged = {},
            onStartAuth = {},
            onBrowserSelected = {},
            onPendingIntentModeChanged = {},
            onRefreshAccessToken = {},
            onFetchUserInfo = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}
