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
import net.openid.appauth.browser.VersionRange.Companion.between
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VersionedBrowserMatcherTest {
    private lateinit var browserMatcher: VersionedBrowserMatcher

    @Before
    fun setUp() {
        browserMatcher = VersionedBrowserMatcher(
            Chrome.PACKAGE_NAME,
            Chrome.SIGNATURE_SET,
            false,
            between("1.2.3", "2")
        )
    }

    @Test
    fun testMatches() {
        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    Chrome.SIGNATURE_SET,
                    "1.5.0",
                    false
                )
            )
        ).isTrue()

        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    Chrome.SIGNATURE_SET,
                    "1.2.3",
                    false
                )
            )
        ).isTrue()

        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    Chrome.SIGNATURE_SET,
                    "2.0",
                    false
                )
            )
        ).isTrue()
    }

    @Test
    fun testMatches_differentPackageName() {
        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    "com.android.not_chrome",
                    Chrome.SIGNATURE_SET,
                    "1.5.0",
                    false
                )
            )
        ).isFalse()
    }

    @Test
    fun testMatches_differentSignature() {
        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    mutableSetOf("DIFFERENT_HASH"),
                    "1.5.0",
                    false
                )
            )
        ).isFalse()
    }

    @Test
    fun testMatches_additionalSignatures() {
        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    setOf(*Chrome.SIGNATURE_SET.toTypedArray(), "ANOTHER_SIGNATURE"),
                    "1.5.0",
                    false
                )
            )
        ).isFalse()
    }

    @Test
    fun testMatches_differentCustomTabMode() {
        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    Chrome.SIGNATURE_SET,
                    "1.5.0",
                    true
                )
            )
        ).isFalse()
    }

    @Test
    fun testMatches_belowVersionRange() {
        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    Chrome.SIGNATURE_SET,
                    "1.2.2",
                    false
                )
            )
        ).isFalse()
    }

    @Test
    fun testMatches_aboveVersionRange() {
        assertThat(
            browserMatcher.matches(
                BrowserDescriptor(
                    Chrome.PACKAGE_NAME,
                    Chrome.SIGNATURE_SET,
                    "2.0.1",
                    false
                )
            )
        ).isFalse()
    }
}
