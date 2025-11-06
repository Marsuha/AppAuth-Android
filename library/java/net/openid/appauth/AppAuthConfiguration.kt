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

import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauth.browser.BrowserMatcher
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder

/**
 * Defines configuration properties that control the behavior of the AppAuth library, independent
 * of the OAuth2 specific details that are described.
 *
 * @param browserMatcher Controls which browsers can be used for the authorization flow.
 * @param connectionBuilder Creates [java.net.HttpURLConnection] instances for use in token requests and related
 * interactions with the authorization service.
 * @param skipIssuerHttpsCheck Returns `true` if issuer https validation is disabled, otherwise
 * `false`.
 */
data class AppAuthConfiguration(
    /**
     * Controls which browsers can be used for the authorization flow.
     */
    val browserMatcher: BrowserMatcher = AnyBrowserMatcher,
    /**
     * Creates [java.net.HttpURLConnection] instances for use in token requests and related
     * interactions with the authorization service.
     */
    val connectionBuilder: ConnectionBuilder = DefaultConnectionBuilder,
    /**
     * Returns `true` if issuer https validation is disabled, otherwise
     * `false`.
     */
    val skipIssuerHttpsCheck: Boolean = false
)

@DslMarker
annotation class AppAuthConfigurationDsl

@AppAuthConfigurationDsl
class AppAuthConfigurationBuilder {
    var browserMatcher: BrowserMatcher = AnyBrowserMatcher
    var connectionBuilder: ConnectionBuilder = DefaultConnectionBuilder
    var skipIssuerHttpsCheck: Boolean = false

    fun build(): AppAuthConfiguration {
        return AppAuthConfiguration(
            browserMatcher,
            connectionBuilder,
            skipIssuerHttpsCheck
        )
    }
}

/**
 * Creates an [AppAuthConfiguration] instance using a DSL-style builder.
 *
 * This function provides a convenient, type-safe way to construct an `AppAuthConfiguration` object.
 *
 * Example usage:
 * ```
 * val config = appAuthConfiguration {
 *     browserMatcher = VersionedBrowserMatcher.CHROME_CUSTOM_TAB
 *     connectionBuilder = CustomConnectionBuilder()
 * }
 * ```
 * @param block A lambda with the [AppAuthConfigurationBuilder] as its receiver, used to configure the properties.
 * @return A configured, immutable [AppAuthConfiguration] instance.
 */
fun appAuthConfiguration(block: AppAuthConfigurationBuilder.() -> Unit): AppAuthConfiguration {
    return AppAuthConfigurationBuilder().apply(block).build()
}
