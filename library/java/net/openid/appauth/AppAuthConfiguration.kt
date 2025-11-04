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
 */
class AppAuthConfiguration private constructor(
    /**
     * Controls which browsers can be used for the authorization flow.
     */
    val browserMatcher: BrowserMatcher,
    /**
     * Creates [java.net.HttpURLConnection] instances for use in token requests and related
     * interactions with the authorization service.
     */
    val connectionBuilder: ConnectionBuilder,
    /**
     * Returns `true` if issuer https validation is disabled, otherwise
     * `false`.
     *
     * @see Builder.setSkipIssuerHttpsCheck
     */
    val skipIssuerHttpsCheck: Boolean
) {
    /**
     * Creates [AppAuthConfiguration] instances.
     */
    class Builder {
        private var mBrowserMatcher: BrowserMatcher = AnyBrowserMatcher
        private var mConnectionBuilder: ConnectionBuilder = DefaultConnectionBuilder
        private var mSkipIssuerHttpsCheck = false
        private val mSkipNonceVerification = false

        /**
         * Specify the browser matcher to use, which controls the browsers that can be used
         * for authorization.
         */
        fun setBrowserMatcher(browserMatcher: BrowserMatcher): Builder {
            mBrowserMatcher = browserMatcher
            return this
        }

        /**
         * Specify the connection builder to use, which creates [java.net.HttpURLConnection]
         * instances for use in direct communication with the authorization service.
         */
        fun setConnectionBuilder(connectionBuilder: ConnectionBuilder): Builder {
            mConnectionBuilder = connectionBuilder
            return this
        }

        /**
         * Disables https validation for the issuer identifier.
         *
         *
         * NOTE: Disabling issuer https validation implies the app is running against an
         * insecure environment. Enabling this option is only recommended for testing purposes.
         */
        fun setSkipIssuerHttpsCheck(skipIssuerHttpsCheck: Boolean): Builder {
            mSkipIssuerHttpsCheck = skipIssuerHttpsCheck
            return this
        }

        /**
         * Creates the instance from the configured properties.
         */
        fun build(): AppAuthConfiguration {
            return AppAuthConfiguration(
                mBrowserMatcher,
                mConnectionBuilder,
                mSkipIssuerHttpsCheck
            )
        }
    }

    companion object {
        /**
         * The default configuration that is used if no configuration is explicitly specified
         * when constructing an [AuthorizationService].
         */
        val DEFAULT: AppAuthConfiguration = Builder().build()
    }
}
