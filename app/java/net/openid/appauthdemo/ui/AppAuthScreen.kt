package net.openid.appauthdemo.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauthdemo.R
import net.openid.appauthdemo.ui.theme.AppAuthDemoTheme

@Composable
fun AppAuthScreen(
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
private fun AppAuthScreenLoadingPreview() {
    AppAuthDemoTheme {
        AppAuthScreen(
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
private fun AppAuthScreenErrorPreview() {
    AppAuthDemoTheme {
        AppAuthScreen(
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
private fun AppAuthScreenUnauthenticatedPreview() {
    AppAuthDemoTheme {
        AppAuthScreen(
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
private fun AppAuthScreenAuthenticatedPreview() {
    AppAuthDemoTheme {
        AppAuthScreen(
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