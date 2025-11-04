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
class BrowserDenyListTest {
    @Test
    fun testMatches_emptyDenyList() {
        val denyList = BrowserDenyList()
        assertThat(denyList.matches(Chrome.customTab("46"))).isTrue()
        assertThat(denyList.matches(Firefox.standaloneBrowser("10"))).isTrue()
        assertThat(denyList.matches(SBrowser.standaloneBrowser("11"))).isTrue()
    }

    @Test
    fun testMatches_singleBrowser() {
        val denyList = BrowserDenyList(VersionedBrowserMatcher.FIREFOX_BROWSER)
        assertThat(denyList.matches(Chrome.customTab("46"))).isTrue()
        assertThat(denyList.matches(Firefox.standaloneBrowser("10"))).isFalse()
        assertThat(denyList.matches(SBrowser.standaloneBrowser("11"))).isTrue()
    }

    @Test
    fun testMatches_customTabs() {
        val denyList = BrowserDenyList(
            VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
            VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB
        )

        assertThat(denyList.matches(Chrome.standaloneBrowser("46"))).isTrue()
        assertThat(denyList.matches(Chrome.customTab("46"))).isFalse()
        assertThat(denyList.matches(SBrowser.standaloneBrowser("11"))).isTrue()
        assertThat(denyList.matches(SBrowser.customTab("11"))).isFalse()
    }
}
