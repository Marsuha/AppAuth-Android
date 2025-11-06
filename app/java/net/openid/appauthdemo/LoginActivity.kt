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

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.ColorRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.appAuthConfiguration
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauth.browser.BrowserMatcher
import net.openid.appauth.browser.BrowserSelector.getAllBrowsers
import net.openid.appauth.browser.ExactBrowserMatcher
import net.openid.appauthdemo.BrowserSelectionAdapter.BrowserInfo

/**
 * Demonstrates the usage of the AppAuth to authorize a user with an OAuth2 / OpenID Connect
 * provider. Based on the configuration provided in `res/raw/auth_config.json`, the code
 * contained here will:
 *
 * - Retrieve an OpenID Connect discovery document for the provider, or use a local static
 * configuration.
 * - Utilize dynamic client registration, if no static client id is specified.
 * - Initiate the authorization request using the built-in heuristics or a user-selected browser.
 *
 * _NOTE_: From a clean checkout of this project, the authorization service is not configured.
 * Edit `res/raw/auth_config.json` to provide the required configuration properties. See the
 * README.md in the app/ directory for configuration instructions, and the adjacent IDP-specific
 * instructions.
 */
class LoginActivity : AppCompatActivity() {
    private var authService: AuthorizationService? = null
    private val authStateManager by lazy { AuthStateManager.getInstance(this) }
    private val configuration by lazy { Configuration.getInstance(this) }

    private lateinit var clientId: String
    private var authRequest: AuthorizationRequest? = null
    private var authCustomTabsIntent = CompletableDeferred<CustomTabsIntent>()

    private var isPendingIntentMode = false

    private var selectedBrowserMatcher: BrowserMatcher = AnyBrowserMatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (authStateManager.getCurrent().isAuthorized && !configuration.hasConfigurationChanged()) {
                Log.i(TAG, "User is already authenticated, proceeding to token activity")
                startActivity(Intent(this@LoginActivity, TokenActivity::class.java))
                finish()
                return@launch
            }

            setContentView(R.layout.activity_login)

            findViewById<Button>(R.id.retry).setOnClickListener {
                lifecycleScope.launch { initializeAppAuth() }
            }

            findViewById<Button>(R.id.start_auth).setOnClickListener { startAuth() }

            findViewById<EditText>(R.id.login_hint_value).addTextChangedListener(
                DebouncedLoginHintWatcher()
            )

            if (!configuration.isValid) {
                displayError(configuration.configurationError, false)
                return@launch
            }

            configureBrowserSelector()

            if (configuration.hasConfigurationChanged()) {
                // discard any existing authorization state due to the change of configuration
                Log.i(TAG, "Configuration change detected, discarding old state")
                authStateManager.replace(AuthState())
                configuration.acceptConfiguration()
            }

            if (intent.getBooleanExtra(EXTRA_FAILED, false)) {
                displayAuthCancelled()
            }

            displayLoading("Initializing")
            initializeAppAuth()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authService?.dispose()
    }

    @MainThread
    fun startAuth() {
        displayLoading("Making authorization request")
        isPendingIntentMode = findViewById<CheckBox>(R.id.pending_intents_checkbox).isChecked

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        lifecycleScope.launch { doAuth() }
    }

    /**
     * Initializes the authorization service configuration if necessary, either from the local
     * static values or by retrieving an OpenID discovery document.
     */
    private suspend fun initializeAppAuth() {
        Log.i(TAG, "Initializing AppAuth")
        recreateAuthorizationService()

        authStateManager.getCurrent().authorizationServiceConfiguration?.let  {
            // configuration is already created, skip to client initialization
            Log.i(TAG, "auth config already established")
            initializeClient()
            return
        }

        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.
        if (configuration.discoveryUri == null) {
            Log.i(TAG, "Creating auth config from res/raw/auth_config.json")
            val config = AuthorizationServiceConfiguration(
                configuration.authEndpointUri!!,
                configuration.tokenEndpointUri!!,
                configuration.registrationEndpointUri,
                configuration.endSessionEndpoint
            )

            authStateManager.replace(AuthState(config))
            initializeClient()
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        displayLoading("Retrieving discovery document")
        Log.i(TAG, "Retrieving OpenID discovery doc")

        try {
            val config = AuthorizationServiceConfiguration.fetchFromUrl(
                configuration.discoveryUri!!,
                configuration.connectionBuilder
            )

            Log.i(TAG, "Discovery document retrieved")
            authStateManager.replace(AuthState(config))
            initializeClient()
        } catch (ex: AuthorizationException) {
            Log.i(TAG, "Failed to retrieve discovery document", ex)
            displayError("Failed to retrieve discovery document: " + ex.message, true)
            return
        }
    }

    /**
     * Initiates a dynamic registration request if a client ID is not provided by the static
     * configuration.
     */
    private suspend fun initializeClient() {
        configuration.clientId?.let {
            Log.i(TAG, "Using static client ID: $it")
            // use a statically configured client ID
            clientId = it
            initializeAuthRequest()
            return
        }

        authStateManager.getCurrent().lastRegistrationResponse?.let {
            Log.i(TAG, "Using dynamic client ID: ${it.clientId}")
            // already dynamically registered a client ID
            clientId = it.clientId
            initializeAuthRequest()
            return
        }

        // WrongThread inference is incorrect for lambdas
        // noinspection WrongThread
        displayLoading("Dynamically registering client")
        Log.i(TAG, "Dynamically registering client")

        val registrationRequest = RegistrationRequest.Builder(
            authStateManager.getCurrent().authorizationServiceConfiguration!!,
            listOf(configuration.redirectUri)
        ).setTokenEndpointAuthenticationMethod(ClientSecretBasic.NAME).build()


        try {
            val response = authService!!.performRegistrationRequest(registrationRequest)
            authStateManager.updateAfterRegistration(response, null)
            Log.i(TAG, "Dynamically registered client: ${response.clientId}")
            clientId = response.clientId
            initializeAuthRequest()
        } catch (ex: AuthorizationException) {
            authStateManager.updateAfterRegistration(null, ex)
            Log.i(TAG, "Failed to dynamically register client", ex)
            displayErrorLater("Failed to register client: ${ex.message}")
            return
        }
    }

    /**
     * Enumerates the browsers installed on the device and populates a spinner, allowing the
     * demo user to easily test the authorization flow against different browser and custom
     * tab configurations.
     */
    @MainThread
    private fun configureBrowserSelector() {
        val spinner = findViewById<Spinner>(R.id.browser_selector)
        val adapter = BrowserSelectionAdapter(this)
        spinner.setAdapter(adapter)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val info = adapter.getItem(position)
                if (info == null) {
                    selectedBrowserMatcher = AnyBrowserMatcher
                    return
                } else {
                    selectedBrowserMatcher = ExactBrowserMatcher(info.descriptor)
                }

                recreateAuthorizationService()
                createAuthRequest(loginHint)
                warmUpBrowser()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedBrowserMatcher = AnyBrowserMatcher
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val descriptors = getAllBrowsers(this@LoginActivity)

            val browserInfoList = descriptors.mapNotNull { descriptor ->
                try {
                    val appInfo = packageManager.getApplicationInfo(descriptor.packageName, 0)

                    BrowserInfo(
                        descriptor = descriptor,
                        label = packageManager.getApplicationLabel(appInfo),
                        icon = packageManager.getApplicationIcon(descriptor.packageName)
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(
                        TAG,
                        "Browser package not found: ${descriptor.packageName}",
                        e
                    )

                    null
                }
            }

            withContext(Dispatchers.Main) {
                adapter.updateBrowsers(browserInfoList)
            }
        }
    }

    /**
     * Performs the authorization request, using the browser selected in the spinner,
     * and a user-provided `login_hint` if available.
     */
    private suspend fun doAuth() {
        val authIntent = authCustomTabsIntent.await()

        if (isPendingIntentMode) {
            val completionIntent = Intent(this, TokenActivity::class.java)
            val cancelIntent = Intent(this, LoginActivity::class.java)
            cancelIntent.putExtra(EXTRA_FAILED, true)
            cancelIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else 0

            authService!!.performAuthorizationRequest(
                request = authRequest!!,
                completedIntent = PendingIntent.getActivity(this, 0, completionIntent, flags),
                canceledIntent = PendingIntent.getActivity(this, 0, cancelIntent, flags),
                customTabsIntent = authIntent
            )
        } else {
            val intent = authService!!.getAuthorizationRequestIntent(
                request = authRequest!!,
                customTabsIntent = authIntent
            )

            registerForActivityResult(StartActivityForResult()) { result ->
                displayAuthOptions()

                if (result.resultCode == RESULT_CANCELED) {
                    displayAuthCancelled()
                } else {
                    val intent = Intent(this, TokenActivity::class.java)
                    intent.putExtras(result.data!!.extras!!)
                    startActivity(intent)
                }
            }.launch(intent)
        }
    }

    private fun recreateAuthorizationService() {
        authService?.let {
            Log.i(TAG, "Discarding existing AuthService instance")
            it.dispose()
        }

        authService = createAuthorizationService()
        authRequest = null
    }

    private fun createAuthorizationService(): AuthorizationService {
        Log.i(TAG, "Creating authorization service")
        val config = appAuthConfiguration {
            browserMatcher = selectedBrowserMatcher
            connectionBuilder = configuration.connectionBuilder
        }

        return AuthorizationService(this, config)
    }

    @MainThread
    private fun displayLoading(loadingMessage: String) {
        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
        findViewById<View>(R.id.auth_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE
        findViewById<TextView>(R.id.loading_description).text = loadingMessage
    }

    @MainThread
    private fun displayError(error: String?, recoverable: Boolean) {
        findViewById<View>(R.id.error_container).visibility = View.VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.auth_container).visibility = View.GONE
        findViewById<TextView>(R.id.error_description).text = error
        findViewById<View>(R.id.retry).visibility = if (recoverable) View.VISIBLE else View.GONE
    }

    // WrongThread inference is incorrect in this case
    @MainThread
    private fun displayErrorLater(error: String, recoverable: Boolean = true) {
        displayError(error, recoverable)
    }

    @MainThread
    private fun initializeAuthRequest() {
        createAuthRequest(loginHint)
        warmUpBrowser()
        displayAuthOptions()
    }

    /**
     * Displays the authorization options screen.
     */
    @MainThread
    private fun displayAuthOptions() = lifecycleScope.launch{
        findViewById<View>(R.id.auth_container).visibility = View.VISIBLE
        findViewById<View>(R.id.loading_container).visibility = View.GONE
        findViewById<View>(R.id.error_container).visibility = View.GONE

        val state = authStateManager.getCurrent()
        val config = state.authorizationServiceConfiguration

        val authEndpointStr = if (config?.discoveryDoc != null) {
            "Discovered auth endpoint: \n${config.authorizationEndpoint}"
        } else {
            "Static auth endpoint: \n${config?.authorizationEndpoint}"
        }

        findViewById<TextView>(R.id.auth_endpoint).text = authEndpointStr

        val clientIdStr = if (state.lastRegistrationResponse != null) {
            "Dynamic client ID: \n$clientId"
        } else {
            "Static client ID: \n$clientId"
        }

        findViewById<TextView>(R.id.client_id).text = clientIdStr
    }

    private fun displayAuthCancelled() {
        Snackbar.make(
            findViewById(R.id.coordinator),
            "Authorization canceled",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun warmUpBrowser() {
        authCustomTabsIntent = CompletableDeferred()
        Log.i(TAG, "Warming up browser instance for auth request")

        lifecycleScope.launch {
            val intentBuilder = authService!!.createCustomTabsIntentBuilder(authRequest!!.toUri())
            val colorScheme = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(getColorCompat(R.color.colorPrimary))
                .build()
            intentBuilder.setDefaultColorSchemeParams(colorScheme)
            authCustomTabsIntent.complete(intentBuilder.build())
        }
    }

    private fun createAuthRequest(loginHint: String?) = lifecycleScope.launch {
        Log.i(TAG, "Creating auth request for login hint: $loginHint")

        val authRequestBuilder = AuthorizationRequest.Builder(
            configuration = authStateManager.getCurrent().authorizationServiceConfiguration!!,
            clientId = clientId,
            responseType = ResponseTypeValues.CODE,
            redirectUri = configuration.redirectUri
        ).setScope(configuration.scope)

        if (!loginHint.isNullOrEmpty()) authRequestBuilder.setLoginHint(loginHint)
        authRequest = authRequestBuilder.build()
    }

    private val loginHint: String
        get() = findViewById<EditText>(R.id.login_hint_value).text.toString().trim()

    @Suppress("deprecation")
    private fun getColorCompat(@ColorRes color: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getColor(color)
        } else {
            resources.getColor(color)
        }
    }

    /**
     * Responds to changes in the login hint. After a "debounce" delay, warms up the browser
     * for a request with the new login hint; this avoids constantly re-initializing the
     * browser while the user is typing.
     */
    private inner class DebouncedLoginHintWatcher : TextWatcher {
        private val debounceDelay = 500L
        private var debounceJob: Job? = null

        override fun beforeTextChanged(cs: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(cs: CharSequence?, start: Int, before: Int, count: Int) {
            debounceJob?.cancel()
            debounceJob = lifecycleScope.launch {
                delay(debounceDelay)
                createAuthRequest(loginHint)
                warmUpBrowser()
            }
        }

        override fun afterTextChanged(ed: Editable?) {}
    }

    @Suppress("unused")
    companion object {
        private const val TAG = "LoginActivity"
        private const val EXTRA_FAILED = "failed"
        private const val RC_AUTH = 100
    }
}
