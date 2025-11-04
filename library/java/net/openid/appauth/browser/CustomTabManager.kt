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
package net.openid.appauth.browser

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.openid.appauth.internal.Logger
import net.openid.appauth.internal.toCustomTabUriBundle
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the lifecycle and interactions with a Custom Tabs service using Kotlin Coroutines.
 *
 * @param context The application context used to bind to the Custom Tabs service.
 */
class CustomTabManager(context: Context) {
    private val contextRef = WeakReference(context)
    private val context get() = contextRef.get()
    private var clientDeferred = CompletableDeferred<CustomTabsClient>()
    private var connection: CustomTabsServiceConnection? = null

    /**
     * Binds to the Custom Tabs service for the specified browser package.
     *
     * @param browserPackage The package name of the browser to bind to.
     */
    fun bind(browserPackage: String) {
        if (connection != null) return

        connection = object : CustomTabsServiceConnection() {
            override fun onServiceDisconnected(componentName: ComponentName?) {
                Logger.debug("CustomTabsService is disconnected")
                clientDeferred.cancel()
                clientDeferred = CompletableDeferred()
            }

            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                customTabsClient: CustomTabsClient
            ) {
                Logger.debug("CustomTabsService is connected")
                customTabsClient.warmup(0)
                clientDeferred.complete(customTabsClient)
            }
        }

        this@CustomTabManager.context?.let {
            if (!CustomTabsClient.bindCustomTabsService(it, browserPackage, connection!!)) {
                Logger.info("Unable to bind custom tabs service")
            }
        } ?: Logger.info("Unable to bind custom tabs service")
    }

    /**
     * Creates a builder for a Custom Tabs intent.
     *
     * @param possibleUris Optional URIs to prefetch.
     * @return A builder for creating a Custom Tabs intent.
     */
    suspend fun createTabBuilder(vararg possibleUris: Uri?): CustomTabsIntent.Builder =
        CustomTabsIntent.Builder(createSession(null, *possibleUris))

    /**
     * Unbinds from the Custom Tabs service and releases resources.
     */
    fun dispose() {
        connection?.let { this@CustomTabManager.context?.unbindService(it) }
        clientDeferred.cancel()
        Logger.debug("CustomTabsService is disconnected")
    }

    /**
     * Creates a Custom Tabs session for preloading and launching URLs.
     *
     * @param callbacks Optional callbacks for session events.
     * @param possibleUris Optional URIs to prefetch.
     * @return A CustomTabsSession instance, or null if creation failed.
     */
    suspend fun createSession(
        callbacks: CustomTabsCallback?,
        vararg possibleUris: Uri?
    ): CustomTabsSession? = withContext(Dispatchers.IO) {
        getClient()?.let { client ->
            val session = client.newSession(callbacks) ?: run {
                Logger.warn("Failed to create custom tabs session through custom tabs client")
                return@withContext null
            }

            if (possibleUris.isNotEmpty()) {
                session.mayLaunchUrl(
                    possibleUris[0],
                    null,
                    possibleUris.toCustomTabUriBundle(1)
                )
            }

            session
        }
    }

    /**
     * Gets the CustomTabsClient instance, waiting if necessary.
     *
     * @return The CustomTabsClient instance, or null if unavailable.
     */
    @VisibleForTesting
    suspend fun getClient(): CustomTabsClient? =
        withTimeoutOrNull(CLIENT_WAIT_TIME.seconds) { clientDeferred.await() }

    companion object {
        private const val CLIENT_WAIT_TIME = 1L
    }
}