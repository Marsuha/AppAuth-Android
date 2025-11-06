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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import net.openid.appauth.browser.BrowserSelector.BROWSER_INTENT
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserSelectorTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        whenever(context.packageManager).thenReturn(packageManager)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_warmUpSupportOnFirstMatch() {
        setBrowserList(CHROME, FIREFOX, DOLPHIN)
        setBrowsersWithWarmupSupport(CHROME, FIREFOX)
        checkSelectedBrowser(CHROME, USE_CUSTOM_TAB)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_warmUpSupportOnAlternateBrowser() {
        setBrowserList(DOLPHIN, FIREFOX)
        setBrowsersWithWarmupSupport(FIREFOX)
        checkSelectedBrowser(FIREFOX, USE_CUSTOM_TAB)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_warmUpSupportOnAlternateBrowsers() {
        setBrowserList(DOLPHIN, CHROME, FIREFOX)
        setBrowsersWithWarmupSupport(CHROME, FIREFOX)
        checkSelectedBrowser(CHROME, USE_CUSTOM_TAB)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_noWarmUpSupportOnAnyBrowser() {
        setBrowserList(CHROME, DOLPHIN)
        setBrowsersWithWarmupSupport(*NO_BROWSERS)
        checkSelectedBrowser(CHROME, USE_STANDALONE)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_noBrowsers() {
        setBrowserList(*NO_BROWSERS)
        setBrowsersWithWarmupSupport(*NO_BROWSERS)
        checkSelectedBrowser(null, false)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_ignoreAuthorityRestrictedBrowsers() {
        val authorityRestrictedBrowser =
            TestBrowserBuilder("com.badguy.proxy")
                .withBrowserDefaults()
                .addAuthority("www.example.com")
                .build()

        setBrowserList(authorityRestrictedBrowser, CHROME)
        setBrowsersWithWarmupSupport(authorityRestrictedBrowser, CHROME)
        checkSelectedBrowser(CHROME, USE_CUSTOM_TAB)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_ignoreBrowsersWithoutBrowseableCategory() {
        val misconfiguredBrowser =
            TestBrowserBuilder("com.broken.browser")
                .addAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addScheme(SCHEME_HTTP)
                .addScheme(SCHEME_HTTPS)
                .build()

        setBrowserList(misconfiguredBrowser, CHROME)
        setBrowsersWithWarmupSupport(misconfiguredBrowser, CHROME)
        checkSelectedBrowser(CHROME, USE_CUSTOM_TAB)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_ignoreBrowsersWithoutHttpsSupport() {
        val noHttpsBrowser =
            TestBrowserBuilder("com.broken.browser")
                .addAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addScheme(SCHEME_HTTP)
                .build()

        setBrowserList(DOLPHIN, noHttpsBrowser)
        setBrowsersWithWarmupSupport(noHttpsBrowser)
        checkSelectedBrowser(DOLPHIN, USE_STANDALONE)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_matcherPrefersStandaloneChrome() {
        // in this scenario, the user has firefox as their default but the app insists on using
        // chrome via a browser allowList.
        setBrowserList(FIREFOX, CHROME, DOLPHIN)
        setBrowsersWithWarmupSupport(FIREFOX, CHROME)
        checkSelectedBrowser(
            CHROME,
            USE_STANDALONE,
            VersionedBrowserMatcher(
                CHROME.packageName,
                CHROME.signatureHashes,
                USE_STANDALONE,
                VersionRange.ANY_VERSION
            )
        )
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_noMatchingBrowser() {
        setBrowserList(FIREFOX, DOLPHIN)
        setBrowsersWithWarmupSupport(*NO_BROWSERS)

        checkSelectedBrowser(
            null,
            USE_STANDALONE,
            VersionedBrowserMatcher(
                CHROME.packageName,
                CHROME.signatureHashes,
                USE_STANDALONE,
                VersionRange.ANY_VERSION
            )
        )
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_defaultBrowserSetNoneSupporting() {
        // Chrome is set as the users default browser, but the version is not supporting Custom Tabs
        // BrowserSelector.getAllBrowsers will result in a list, where the Dolphin browser is the
        // first element and the other browser, in this case Firefox, as the second element in the list.
        setBrowserList(FIREFOX, CHROME)
        setBrowsersWithWarmupSupport(*NO_BROWSERS)

        whenever(context.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(CHROME.resolveInfo)

        val allBrowsers = BrowserSelector.getAllBrowsers(context)

        assertThat(allBrowsers[0].packageName).isEqualTo(CHROME.packageName)
        assertThat(allBrowsers[0].useCustomTab).isFalse()
        assertThat(allBrowsers[1].packageName).isEqualTo(FIREFOX.packageName)
        assertThat(allBrowsers[1].useCustomTab).isFalse()
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_defaultBrowserNoCustomTabs() {
        // Firefox is set as the users default browser, but the version is not supporting Custom Tabs
        // BrowserSelector.getAllBrowsers will result in a list, where the Firefox browser is the
        // first element and the other browser, in this case Chrome, as the second element in the list.
        setBrowserList(CHROME, FIREFOX)
        setBrowsersWithWarmupSupport(CHROME)

        whenever(context.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX.resolveInfo)

        val allBrowsers = BrowserSelector.getAllBrowsers(context)

        assertThat(allBrowsers[0].packageName).isEqualTo(FIREFOX.packageName)
        assertThat(allBrowsers[0].useCustomTab).isFalse()
        assertThat(allBrowsers[1].packageName).isEqualTo(CHROME.packageName)
        assertThat(allBrowsers[1].useCustomTab).isTrue()
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_selectDefaultBrowserCustomTabs() {
        // Firefox is set as the users default browser, supporting Custom Tabs
        // BrowserSelector.getAllBrowsers will result in a list, where the Firefox browser is the
        // first element two elements in the list and the other browser, in this case Chrome,
        // as the third element in the list.
        setBrowserList(CHROME, FIREFOX_CUSTOM_TAB)
        setBrowsersWithWarmupSupport(CHROME, FIREFOX_CUSTOM_TAB)

        whenever(context.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX_CUSTOM_TAB.resolveInfo)

        val allBrowsers = BrowserSelector.getAllBrowsers(context)

        assertThat(allBrowsers[0].packageName).isEqualTo(FIREFOX_CUSTOM_TAB.packageName)
        assertThat(allBrowsers[0].useCustomTab).isTrue()
        assertThat(allBrowsers[1].packageName).isEqualTo(FIREFOX_CUSTOM_TAB.packageName)
        assertThat(allBrowsers[1].useCustomTab).isFalse()
        assertThat(allBrowsers[2].packageName).isEqualTo(CHROME.packageName)
        assertThat(allBrowsers[2].useCustomTab).isTrue()
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_selectDefaultBrowserSetNoneSupporting() {
        // Chrome is set as the users default browser, none of the browsers support Custom Tabs
        // BrowserSelector.select will return Chrome as it the default browser.
        setBrowserList(FIREFOX, CHROME)
        setBrowsersWithWarmupSupport(*NO_BROWSERS)

        whenever(context.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(CHROME.resolveInfo)

        checkSelectedBrowser(CHROME, USE_STANDALONE)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_selectDefaultBrowserNoCustomTabs() {
        // Firefox is set as the users default browser, but the version is not supporting Custom Tabs
        // BrowserSelector.select will return Chrome as it is supporting Custom Tabs.
        setBrowserList(CHROME, FIREFOX)
        setBrowsersWithWarmupSupport(CHROME)

        whenever(context.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX.resolveInfo)

        checkSelectedBrowser(CHROME, USE_CUSTOM_TAB)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_defaultBrowserCustomTabs() {
        // Firefox is set as the users default browser, supporting Custom Tabs
        // BrowserSelector.select will return Firefox.
        setBrowserList(CHROME, FIREFOX_CUSTOM_TAB)
        setBrowsersWithWarmupSupport(CHROME, FIREFOX_CUSTOM_TAB)
        whenever(context.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX_CUSTOM_TAB.resolveInfo)

        checkSelectedBrowser(FIREFOX_CUSTOM_TAB, USE_CUSTOM_TAB)
    }

    /**
     * Browsers are expected to be in priority order, such that the default would be first.
     */
    @Throws(PackageManager.NameNotFoundException::class)
    private fun setBrowserList(vararg browsers: TestBrowser) {
        val resolveInfos = browsers.map {
            whenever(
                packageManager.getPackageInfo(
                    eq(it.packageInfo.packageName),
                    eq(PackageManager.GET_SIGNING_CERTIFICATES)
                )
            ).thenReturn(it.packageInfo)
            it.resolveInfo
        }

        whenever(
            packageManager.queryIntentActivities(
                BROWSER_INTENT,
                PackageManager.GET_RESOLVED_FILTER or PackageManager.MATCH_ALL
            )
        ).thenReturn(resolveInfos)
    }

    private fun setBrowsersWithWarmupSupport(vararg browsers: TestBrowser) {
        browsers.forEach {
            whenever(
                packageManager.resolveService(
                    serviceIntentEq(it.resolveInfo.activityInfo.packageName),
                    eq(0)
                )
            ).thenReturn(it.resolveInfo)
        }
    }

    private fun checkSelectedBrowser(
        expected: TestBrowser?,
        expectCustomTabUse: Boolean,
        browserMatcher: BrowserMatcher = AnyBrowserMatcher
    ) {
        val result = BrowserSelector.select(context, browserMatcher)

        expected?.let {
            assertThat(result).isNotNull()
            assertThat(result!!.packageName).isEqualTo(it.packageName)
            assertThat(result.useCustomTab).isEqualTo(expectCustomTabUse)
        } ?: assertThat(result).isNull()
    }

    private class TestBrowser(
        val packageName: String,
        val packageInfo: PackageInfo,
        val resolveInfo: ResolveInfo,
        val signatureHashes: Set<String>
    )

    private class TestBrowserBuilder(private val packageName: String) {
        private val signatures = mutableListOf<ByteArray>()
        private val actions = mutableListOf<String>()
        private val categories = mutableListOf<String>()
        private val schemes = mutableListOf<String>()
        private val authorities = mutableListOf<String>()
        private var version: String? = null

        fun withBrowserDefaults(): TestBrowserBuilder {
            return addAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addScheme(SCHEME_HTTP)
                .addScheme(SCHEME_HTTPS)
        }

        fun addAction(action: String): TestBrowserBuilder {
            actions.add(action)
            return this
        }

        fun addCategory(category: String): TestBrowserBuilder {
            categories.add(category)
            return this
        }

        fun addScheme(scheme: String): TestBrowserBuilder {
            schemes.add(scheme)
            return this
        }

        fun addAuthority(authority: String): TestBrowserBuilder {
            authorities.add(authority)
            return this
        }

        fun addSignature(signature: String): TestBrowserBuilder {
            signatures.add(signature.toByteArray(StandardCharsets.UTF_8))
            return this
        }

        fun setVersion(version: String): TestBrowserBuilder {
            this.version = version
            return this
        }

        @Suppress("DEPRECATION")
        fun build(): TestBrowser {
            val pi = PackageInfo()
            pi.packageName = packageName
            pi.versionName = version
            val signatureArray = signatures.map { Signature(it) }.toTypedArray()
            pi.signatures = signatureArray

            val mockedSigningInfo: SigningInfo = mock()
            whenever(mockedSigningInfo.signingCertificateHistory).thenReturn(signatureArray)
            whenever(mockedSigningInfo.hasMultipleSigners()).thenReturn(false)
            whenever(mockedSigningInfo.apkContentsSigners).thenReturn(signatureArray)
            pi.signingInfo = mockedSigningInfo

            val signatureHashes = BrowserDescriptor.generateSignatureHashes(pi.signatures!!)

            val ri = ResolveInfo()
            ri.activityInfo = ActivityInfo().also { it.packageName = packageName }
            ri.filter = IntentFilter().apply {
                actions.forEach { this.addAction(it) }
                categories.forEach { this.addCategory(it) }
                schemes.forEach { this.addDataScheme(it) }
                authorities.forEach { this.addDataAuthority(it, null) }
            }

            return TestBrowser(packageName, pi, ri, signatureHashes)
        }
    }

    /**
     * Custom matcher for verifying the intent fired during token request.
     */
    private class ServiceIntentMatcher(private val `package`: String) : ArgumentMatcher<Intent> {
        override fun matches(intent: Intent?): Boolean {
            return intent != null && (BrowserSelector.ACTION_CUSTOM_TABS_CONNECTION == intent.action)
                    && (`package` == intent.`package`)
        }
    }

    companion object {
        private const val SCHEME_HTTP = "http"
        private const val SCHEME_HTTPS = "https"

        private const val USE_CUSTOM_TAB = true
        private const val USE_STANDALONE = false

        private val CHROME: TestBrowser = TestBrowserBuilder("com.android.chrome")
            .withBrowserDefaults()
            .setVersion("50")
            .addSignature("ChromeSignature")
            .build()

        private val FIREFOX: TestBrowser = TestBrowserBuilder("org.mozilla.firefox")
            .withBrowserDefaults()
            .setVersion("10")
            .addSignature("FirefoxSignature")
            .build()

        private val FIREFOX_CUSTOM_TAB: TestBrowser = TestBrowserBuilder("org.mozilla.firefox")
            .withBrowserDefaults()
            .setVersion("57")
            .addSignature("FirefoxSignature")
            .build()

        private val DOLPHIN: TestBrowser = TestBrowserBuilder("mobi.mgeek.TunnyBrowser")
            .withBrowserDefaults()
            .setVersion("1.4.1")
            .addSignature("DolphinSignature")
            .build()

        private val NO_BROWSERS = emptyArray<TestBrowser>()

        private fun serviceIntentEq(pkg: String): Intent {
            return argThat(ServiceIntentMatcher(pkg))
        }
    }
}
