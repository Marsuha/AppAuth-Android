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
package net.openid.appauthdemo

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import okio.Buffer
import okio.buffer
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Reads and validates the demo app configuration from `res/raw/auth_config.json`. Configuration
 * changes are detected by comparing the hash of the last known configuration to the read
 * configuration. When a configuration change is detected, the app state is reset.
 */
class Configuration(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private val resources: Resources = context.resources

    private var configJson: JSONObject? = null
    private var configHash: String? = null

    /**
     * Returns a description of the configuration error, if the configuration is invalid.
     */
    var configurationError: String? = null
        private set

    var clientId: String? = null
        private set

    private var _scope: String? = null
    val scope: String
        get() = _scope!!

    private var _redirectUri: Uri? = null
    val redirectUri: Uri
        get() = _redirectUri!!

    private var _endSessionRedirectUri: Uri? = null
    val endSessionRedirectUri: Uri
        get() = _endSessionRedirectUri!!

    var discoveryUri: Uri? = null
        private set
    var authEndpointUri: Uri? = null
        private set
    var tokenEndpointUri: Uri? = null
        private set
    var endSessionEndpoint: Uri? = null
        private set
    var registrationEndpointUri: Uri? = null
        private set
    var userInfoEndpointUri: Uri? = null
        private set
    var isHttpsRequired: Boolean = false
        private set

    init {
        try {
            readConfiguration()
        } catch (ex: InvalidConfigurationException) {
            configurationError = ex.message
        }
    }

    /**
     * Indicates whether the current configuration is valid.
     */
    val isValid: Boolean
        get() = configurationError == null

    val connectionBuilder: ConnectionBuilder
        get() {
            if (isHttpsRequired) return DefaultConnectionBuilder
            return ConnectionBuilderForTesting
        }

    private val lastKnownConfigHash: String?
        get() = prefs.getString(KEY_LAST_HASH, null)

    /**
     * Indicates whether the configuration has changed from the last known valid state.
     */
    fun hasConfigurationChanged(): Boolean {
        val lastHash = this.lastKnownConfigHash
        return configHash != lastHash
    }

    /**
     * Indicates that the current configuration should be accepted as the "last known valid"
     * configuration.
     */
    fun acceptConfiguration() {
        prefs.edit { putString(KEY_LAST_HASH, configHash) }
    }

    @Throws(InvalidConfigurationException::class)
    private fun readConfiguration() {
        val configSource = resources.openRawResource(R.raw.auth_config).source().buffer()
        val configData = Buffer()

        try {
            configSource.readAll(configData)
            configJson = JSONObject(configData.readString(UTF_8))
        } catch (ex: IOException) {
            throw InvalidConfigurationException(
                "Failed to read configuration: ${ex.message}"
            )
        } catch (ex: JSONException) {
            throw InvalidConfigurationException(
                "Unable to parse configuration: ${ex.message}"
            )
        }

        configHash = configData.sha256().base64()
        clientId = getConfigString("client_id")
        _scope = getRequiredConfigString("authorization_scope")
        _redirectUri = getRequiredConfigUri("redirect_uri")
        _endSessionRedirectUri = getRequiredConfigUri("end_session_redirect_uri")

        if (!isRedirectUriRegistered) {
            throw InvalidConfigurationException(
                ("redirect_uri is not handled by any activity in this app! "
                        + "Ensure that the appAuthRedirectScheme in your build.gradle file "
                        + "is correctly configured, or that an appropriate intent filter "
                        + "exists in your app manifest.")
            )
        }

        if (getConfigString("discovery_uri") == null) {
            authEndpointUri = getRequiredConfigWebUri("authorization_endpoint_uri")
            tokenEndpointUri = getRequiredConfigWebUri("token_endpoint_uri")
            userInfoEndpointUri = getRequiredConfigWebUri("user_info_endpoint_uri")
            endSessionEndpoint = getRequiredConfigUri("end_session_endpoint")

            if (clientId == null) {
                registrationEndpointUri = getRequiredConfigWebUri("registration_endpoint_uri")
            }
        } else {
            discoveryUri = getRequiredConfigWebUri("discovery_uri")
        }

        isHttpsRequired = configJson!!.optBoolean("https_required", true)
    }

    fun getConfigString(propName: String): String? {
        val value = configJson!!.optString(propName).trim { it <= ' ' }
        return value.takeIf { it.isNotEmpty() }
    }

    @Throws(InvalidConfigurationException::class)
    private fun getRequiredConfigString(propName: String): String {
        return getConfigString(propName) ?: throw InvalidConfigurationException(
            "$propName is required but not specified in the configuration"
        )
    }

    @Throws(InvalidConfigurationException::class)
    fun getRequiredConfigUri(propName: String): Uri {
        val uri = try {
            getRequiredConfigString(propName).toUri()
        } catch (ex: Throwable) {
            throw InvalidConfigurationException("$propName could not be parsed", ex)
        }

        if (!uri.isHierarchical || !uri.isAbsolute) throw InvalidConfigurationException(
            "$propName must be hierarchical and absolute"
        )

        if (!uri.encodedUserInfo.isNullOrEmpty()) throw InvalidConfigurationException(
            "$propName must not have user info"
        )

        if (!uri.encodedQuery.isNullOrEmpty()) throw InvalidConfigurationException(
            "$propName must not have query parameters"
        )

        if (!uri.encodedFragment.isNullOrEmpty()) throw InvalidConfigurationException(
            "$propName must not have a fragment"
        )

        return uri
    }

    @Throws(InvalidConfigurationException::class)
    fun getRequiredConfigWebUri(propName: String): Uri {
        val uri = getRequiredConfigUri(propName)
        val scheme = uri.scheme
        if (scheme.isNullOrEmpty() || !("http" == scheme || "https" == scheme)) {
            throw InvalidConfigurationException(
                "$propName must have an http or https scheme"
            )
        }

        return uri
    }

    private val isRedirectUriRegistered: Boolean
        get() {
            // ensure that the redirect URI declared in the configuration is handled by some activity
            // in the app, by querying the package manager speculatively
            val redirectIntent = Intent(Intent.ACTION_VIEW).apply {
                `package` = context.packageName
                data = _redirectUri
                addCategory(Intent.CATEGORY_BROWSABLE)
            }

            return !context.packageManager.queryIntentActivities(redirectIntent, 0).isEmpty()
        }

    class InvalidConfigurationException : Exception {
        internal constructor(reason: String?) : super(reason)
        internal constructor(reason: String?, cause: Throwable?) : super(reason, cause)
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "Configuration"

        private const val PREFS_NAME = "config"
        private const val KEY_LAST_HASH = "lastHash"

        private var sInstance = WeakReference<Configuration?>(null)

        @JvmStatic
        fun getInstance(context: Context): Configuration {
            var config = sInstance.get()
            if (config == null) {
                config = Configuration(context)
                sInstance = WeakReference(config)
            }

            return config
        }
    }
}
