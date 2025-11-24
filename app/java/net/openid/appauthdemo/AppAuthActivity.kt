package net.openid.appauthdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.openid.appauthdemo.ui.AppAuthScreen
import net.openid.appauthdemo.ui.AppAuthViewModel
import net.openid.appauthdemo.ui.theme.AppAuthDemoTheme

class AppAuthActivity : ComponentActivity() {
    private val viewModel: AppAuthViewModel by viewModels()

    private val authResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let {
            viewModel.handleAuthorizationResponse(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppAuthDemoTheme {
                val uiState by viewModel.uiState.collectAsState()
                AppAuthScreen(
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