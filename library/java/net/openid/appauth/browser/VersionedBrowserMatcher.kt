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
@file:Suppress("unused")

package net.openid.appauth.browser

/**
 * Matches a browser based on its package name, set of signatures, version and whether it is
 * being used as a custom tab. This can be used as part of a browser allowList or denyList.
 *
 * Creates a browser matcher that requires an exact match on package name, set of signature
 * hashes, custom tab usage mode, and a version range.: BrowserDescriptor {
            return
 */
class VersionedBrowserMatcher(
    private val packageName: String,
    private val signatureHashes: Set<String>,
    private val usingCustomTab: Boolean,
    private val versionRange: VersionRange
) : BrowserMatcher {
    /**
     * Matches a browser based on its package name, set of signatures, version and whether it is
     * being used as a custom tab. This can be used as part of a browser allowList or denyList.
     *
     * Creates a browser matcher that requires an exact match on package name, single signature
     * hash, custom tab usage mode, and a version range.
     */
    constructor(
        packageName: String,
        signatureHash: String,
        usingCustomTab: Boolean,
        versionRange: VersionRange
    ) : this(
        packageName,
        setOf(signatureHash),
        usingCustomTab,
        versionRange
    )

    override fun matches(descriptor: BrowserDescriptor): Boolean {
        return packageName == descriptor.packageName
                && usingCustomTab == descriptor.useCustomTab && versionRange.matches(descriptor.version)
                && signatureHashes == descriptor.signatureHashes
    }

    companion object {
        /**
         * Matches any version of Chrome for use as a custom tab.
         */
        @JvmField
        val CHROME_CUSTOM_TAB: VersionedBrowserMatcher = VersionedBrowserMatcher(
            Browsers.Chrome.PACKAGE_NAME,
            Browsers.Chrome.SIGNATURE_SET,
            true,
            VersionRange.atLeast(Browsers.Chrome.MINIMUM_VERSION_FOR_CUSTOM_TAB)
        )

        /**
         * Matches any version of Google Chrome for use as a standalone browser.
         */
        @JvmField
        val CHROME_BROWSER: VersionedBrowserMatcher = VersionedBrowserMatcher(
            Browsers.Chrome.PACKAGE_NAME,
            Browsers.Chrome.SIGNATURE_SET,
            false,
            VersionRange.ANY_VERSION
        )

        /**
         * Matches any version of Firefox for use as a custom tab.
         */
        @JvmField
        val FIREFOX_CUSTOM_TAB: VersionedBrowserMatcher = VersionedBrowserMatcher(
            Browsers.Firefox.PACKAGE_NAME,
            Browsers.Firefox.SIGNATURE_SET,
            true,
            VersionRange.atLeast(Browsers.Firefox.MINIMUM_VERSION_FOR_CUSTOM_TAB)
        )

        /**
         * Matches any version of Mozilla Firefox.
         */
        @JvmField
        val FIREFOX_BROWSER: VersionedBrowserMatcher = VersionedBrowserMatcher(
            Browsers.Firefox.PACKAGE_NAME,
            Browsers.Firefox.SIGNATURE_SET,
            false,
            VersionRange.ANY_VERSION
        )

        /**
         * Matches any version of SBrowser for use as a standalone browser.
         */
        @JvmField
        val SAMSUNG_BROWSER: VersionedBrowserMatcher = VersionedBrowserMatcher(
            Browsers.SBrowser.PACKAGE_NAME,
            Browsers.SBrowser.SIGNATURE_SET,
            false,
            VersionRange.ANY_VERSION
        )

        /**
         * Matches any version of SBrowser for use as a custom tab.
         */
        @JvmField
        val SAMSUNG_CUSTOM_TAB: VersionedBrowserMatcher = VersionedBrowserMatcher(
            Browsers.SBrowser.PACKAGE_NAME,
            Browsers.SBrowser.SIGNATURE_SET,
            true,
            VersionRange.atLeast(Browsers.SBrowser.MINIMUM_VERSION_FOR_CUSTOM_TAB)
        )
    }
}
