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
package net.openid.appauth.browser

import net.openid.appauth.browser.Browsers.Chrome
import net.openid.appauth.browser.Browsers.Firefox
import net.openid.appauth.browser.Browsers.SBrowser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [16])
class BrowserAllowListTest {
    @Test
    fun testMatches_emptyAllowList() {
        val allowList = BrowserAllowList()
        assertThat(allowList.matches(Chrome.customTab("46"))).isFalse()
        assertThat(allowList.matches(Firefox.standaloneBrowser("10"))).isFalse()
        assertThat(allowList.matches(Firefox.customTab("57"))).isFalse()
        assertThat(allowList.matches(SBrowser.standaloneBrowser("11"))).isFalse()
    }

    @Test
    fun testMatches_chromeBrowserOnly() {
        val allowList = BrowserAllowList(VersionedBrowserMatcher.CHROME_BROWSER)
        assertThat(allowList.matches(Chrome.standaloneBrowser("46"))).isTrue()
        assertThat(allowList.matches(Chrome.customTab("46"))).isFalse()
        assertThat(allowList.matches(Firefox.standaloneBrowser("10"))).isFalse()
        assertThat(allowList.matches(Firefox.customTab("57"))).isFalse()
    }

    @Test
    fun testMatches_chromeCustomTabOrBrowser() {
        val allowList = BrowserAllowList(
            VersionedBrowserMatcher.CHROME_BROWSER,
            VersionedBrowserMatcher.CHROME_CUSTOM_TAB
        )
        assertThat(allowList.matches(Chrome.standaloneBrowser("46"))).isTrue()
        assertThat(allowList.matches(Chrome.customTab("46"))).isTrue()
        assertThat(allowList.matches(Firefox.standaloneBrowser("10"))).isFalse()
        assertThat(allowList.matches(Firefox.customTab("57"))).isFalse()
    }

    @Test
    fun testMatches_firefoxOrSamsung() {
        val allowList = BrowserAllowList(
            VersionedBrowserMatcher.FIREFOX_BROWSER,
            VersionedBrowserMatcher.FIREFOX_CUSTOM_TAB,
            VersionedBrowserMatcher.SAMSUNG_BROWSER,
            VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB
        )

        assertThat(allowList.matches(Chrome.standaloneBrowser("46"))).isFalse()
        assertThat(allowList.matches(Chrome.customTab("46"))).isFalse()
        assertThat(allowList.matches(Firefox.standaloneBrowser("10"))).isTrue()
        assertThat(allowList.matches(Firefox.customTab("56"))).isFalse()
        assertThat(allowList.matches(Firefox.customTab("57"))).isTrue()
        assertThat(allowList.matches(SBrowser.standaloneBrowser("10"))).isTrue()
        assertThat(allowList.matches(SBrowser.customTab("4.0"))).isTrue()
        assertThat(allowList.matches(SBrowser.customTab("3.9"))).isFalse()
    }
}
