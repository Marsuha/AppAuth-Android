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

import net.openid.appauth.browser.Browsers.Chrome.customTab
import net.openid.appauth.browser.Browsers.Chrome.standaloneBrowser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserDescriptorTest {
    @Test
    fun testEquals_toNull() {
        assertThat(standaloneBrowser("45")).isNotEqualTo(null)
    }

    @Test
    fun testEquals_toSelf() {
        val browser = standaloneBrowser("45")
        assertThat(browser).isEqualTo(browser)
    }

    @Test
    fun testEquals_toEquivalent() {
        val a = standaloneBrowser("45")
        val b = standaloneBrowser("45")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun testEquals_differentVersion() {
        val a = standaloneBrowser("45")
        val b = standaloneBrowser("46")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun testEquals_differentCustomTabSetting() {
        val a = standaloneBrowser("45")
        val b = customTab("45")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun testEquals_differentSignatures() {
        val a = standaloneBrowser("45")
        val b = BrowserDescriptor(
            a.packageName,
            setOf("DIFFERENT_SIGNATURE"),
            a.version,
            a.useCustomTab
        )
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun testEquals_differentPackageNames() {
        val a = standaloneBrowser("45")
        val b = BrowserDescriptor(
            Browsers.Firefox.PACKAGE_NAME,
            a.signatureHashes,
            a.version,
            a.useCustomTab
        )
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun testHashCode_equivalent() {
        val a = standaloneBrowser("45")
        val b = standaloneBrowser("45")

        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun testHashCode_notEquivalent() {
        val a = standaloneBrowser("45")
        val b = standaloneBrowser("46")

        assertThat(a.hashCode()).isNotEqualTo(b.hashCode())
    }
}
