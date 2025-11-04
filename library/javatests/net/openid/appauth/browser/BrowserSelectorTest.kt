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
import android.text.TextUtils
import net.openid.appauth.browser.BrowserSelector.BROWSER_INTENT
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.AutoCloseable
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [16])
class BrowserSelectorTest {
    private var mMockitoCloseable: AutoCloseable? = null

    @Mock
    var mContext: Context? = null

    @Mock
    var mPackageManager: PackageManager? = null

    @Before
    fun setUp() {
        mMockitoCloseable = MockitoAnnotations.openMocks(this)
        Mockito.`when`(mContext!!.packageManager).thenReturn(mPackageManager)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        mMockitoCloseable!!.close()
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
                CHROME.mPackageName,
                CHROME.mSignatureHashes,
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
                CHROME.mPackageName,
                CHROME.mSignatureHashes,
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

        Mockito.`when`(
            mContext!!.packageManager.resolveActivity(BROWSER_INTENT, 0)
        ).thenReturn(CHROME.mResolveInfo)

        val allBrowsers = BrowserSelector.getAllBrowsers(mContext!!)

        Assertions.assertThat(allBrowsers[0].packageName).isEqualTo(CHROME.mPackageName)
        Assertions.assertThat(allBrowsers[0].useCustomTab).isFalse()
        Assertions.assertThat(allBrowsers[1].packageName).isEqualTo(FIREFOX.mPackageName)
        Assertions.assertThat(allBrowsers[1].useCustomTab).isFalse()
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_defaultBrowserNoCustomTabs() {
        // Firefox is set as the users default browser, but the version is not supporting Custom Tabs
        // BrowserSelector.getAllBrowsers will result in a list, where the Firefox browser is the
        // first element and the other browser, in this case Chrome, as the second element in the list.
        setBrowserList(CHROME, FIREFOX)
        setBrowsersWithWarmupSupport(CHROME)

        Mockito.`when`(mContext!!.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX.mResolveInfo)

        val allBrowsers = BrowserSelector.getAllBrowsers(mContext!!)

        Assertions.assertThat(allBrowsers[0].packageName).isEqualTo(FIREFOX.mPackageName)
        Assertions.assertThat(allBrowsers[0].useCustomTab).isFalse()
        Assertions.assertThat(allBrowsers[1].packageName).isEqualTo(CHROME.mPackageName)
        Assertions.assertThat(allBrowsers[1].useCustomTab).isTrue()
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

        Mockito.`when`(mContext!!.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX_CUSTOM_TAB.mResolveInfo)

        val allBrowsers = BrowserSelector.getAllBrowsers(mContext!!)

        Assertions.assertThat(allBrowsers[0].packageName).isEqualTo(FIREFOX_CUSTOM_TAB.mPackageName)
        Assertions.assertThat(allBrowsers[0].useCustomTab).isTrue()
        Assertions.assertThat(allBrowsers[1].packageName).isEqualTo(FIREFOX_CUSTOM_TAB.mPackageName)
        Assertions.assertThat(allBrowsers[1].useCustomTab).isFalse()
        Assertions.assertThat(allBrowsers[2].packageName).isEqualTo(CHROME.mPackageName)
        Assertions.assertThat(allBrowsers[2].useCustomTab).isTrue()
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_selectDefaultBrowserSetNoneSupporting() {
        // Chrome is set as the users default browser, none of the browsers support Custom Tabs
        // BrowserSelector.select will return Chrome as it the default browser.
        setBrowserList(FIREFOX, CHROME)
        setBrowsersWithWarmupSupport(*NO_BROWSERS)

        Mockito.`when`(mContext!!.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(CHROME.mResolveInfo)

        checkSelectedBrowser(CHROME, USE_STANDALONE)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_selectDefaultBrowserNoCustomTabs() {
        // Firefox is set as the users default browser, but the version is not supporting Custom Tabs
        // BrowserSelector.select will return Chrome as it is supporting Custom Tabs.
        setBrowserList(CHROME, FIREFOX)
        setBrowsersWithWarmupSupport(CHROME)

        Mockito.`when`(mContext!!.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX.mResolveInfo)

        checkSelectedBrowser(CHROME, USE_CUSTOM_TAB)
    }

    @Test
    @Throws(PackageManager.NameNotFoundException::class)
    fun testSelect_defaultBrowserCustomTabs() {
        // Firefox is set as the users default browser, supporting Custom Tabs
        // BrowserSelector.select will return Firefox.
        setBrowserList(CHROME, FIREFOX_CUSTOM_TAB)
        setBrowsersWithWarmupSupport(CHROME, FIREFOX_CUSTOM_TAB)
        Mockito.`when`(mContext!!.packageManager.resolveActivity(BROWSER_INTENT, 0))
            .thenReturn(FIREFOX_CUSTOM_TAB.mResolveInfo)

        checkSelectedBrowser(FIREFOX_CUSTOM_TAB, USE_CUSTOM_TAB)
    }

    /**
     * Browsers are expected to be in priority order, such that the default would be first.
     */
    @Suppress("DEPRECATION")
    @Throws(PackageManager.NameNotFoundException::class)
    private fun setBrowserList(vararg browsers: TestBrowser) {
        val resolveInfos = mutableListOf<ResolveInfo>()

        for (browser in browsers) {
            Mockito.`when`(
                mPackageManager!!.getPackageInfo(
                    ArgumentMatchers.eq(browser.mPackageInfo.packageName),
                    ArgumentMatchers.eq(PackageManager.GET_SIGNATURES)
                )
            )
                .thenReturn(browser.mPackageInfo)
            resolveInfos.add(browser.mResolveInfo)
        }

        Mockito.`when`(
            mPackageManager!!.queryIntentActivities(
                BROWSER_INTENT,
                PackageManager.GET_RESOLVED_FILTER
            )
        )
            .thenReturn(resolveInfos)
    }

    private fun setBrowsersWithWarmupSupport(vararg browsers: TestBrowser) {
        for (browser in browsers) {
            Mockito.`when`(
                mPackageManager!!.resolveService(
                    serviceIntentEq(browser.mResolveInfo.activityInfo.packageName)!!,
                    ArgumentMatchers.eq(0)
                )
            )
                .thenReturn(browser.mResolveInfo)
        }
    }

    private fun checkSelectedBrowser(
        expected: TestBrowser?,
        expectCustomTabUse: Boolean,
        browserMatcher: BrowserMatcher = AnyBrowserMatcher
    ) {
        val result = BrowserSelector.select(mContext!!, browserMatcher)
        if (expected == null) {
            Assertions.assertThat(result).isNull()
        } else {
            Assertions.assertThat(result).isNotNull()
            Assertions.assertThat(result!!.packageName).isEqualTo(expected.mPackageName)
            Assertions.assertThat(result.useCustomTab).isEqualTo(expectCustomTabUse)
        }
    }

    private class TestBrowser(
        val mPackageName: String,
        val mPackageInfo: PackageInfo,
        val mResolveInfo: ResolveInfo,
        val mSignatureHashes: Set<String>
    )

    private class TestBrowserBuilder(private val mPackageName: String) {
        private val mSignatures = mutableListOf<ByteArray>()
        private val mActions = mutableListOf<String>()
        private val mCategories = mutableListOf<String>()
        private val mSchemes = mutableListOf<String>()
        private val mAuthorities = mutableListOf<String>()
        private var mVersion: String? = null

        fun withBrowserDefaults(): TestBrowserBuilder {
            return addAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addScheme(SCHEME_HTTP)
                .addScheme(SCHEME_HTTPS)
        }

        fun addAction(action: String): TestBrowserBuilder {
            mActions.add(action)
            return this
        }

        fun addCategory(category: String): TestBrowserBuilder {
            mCategories.add(category)
            return this
        }

        fun addScheme(scheme: String): TestBrowserBuilder {
            mSchemes.add(scheme)
            return this
        }

        fun addAuthority(authority: String): TestBrowserBuilder {
            mAuthorities.add(authority)
            return this
        }

        fun addSignature(signature: String): TestBrowserBuilder {
            mSignatures.add(signature.toByteArray(StandardCharsets.UTF_8))
            return this
        }

        fun setVersion(version: String): TestBrowserBuilder {
            mVersion = version
            return this
        }

        @Suppress("DEPRECATION")
        fun build(): TestBrowser {
            val pi = PackageInfo()
            pi.packageName = mPackageName
            pi.versionName = mVersion
            pi.signatures = arrayOfNulls<Signature>(mSignatures.size)

            for (i in mSignatures.indices) {
                pi.signatures!![i] = Signature(mSignatures[i])
            }

            val signatureHashes = BrowserDescriptor.generateSignatureHashes(pi.signatures!!)

            val ri = ResolveInfo()
            ri.activityInfo = ActivityInfo()
            ri.activityInfo.packageName = mPackageName
            ri.filter = IntentFilter()

            for (action in mActions) {
                ri.filter.addAction(action)
            }

            for (category in mCategories) {
                ri.filter.addCategory(category)
            }

            for (scheme in mSchemes) {
                ri.filter.addDataScheme(scheme)
            }

            for (authority in mAuthorities) {
                ri.filter.addDataAuthority(authority, null)
            }

            return TestBrowser(mPackageName, pi, ri, signatureHashes)
        }
    }

    /**
     * Custom matcher for verifying the intent fired during token request.
     */
    private class ServiceIntentMatcher(private val mPackage: String) : ArgumentMatcher<Intent?> {
        override fun matches(intent: Intent?): Boolean {
            return (intent != null)
                    && (BrowserSelector.ACTION_CUSTOM_TABS_CONNECTION == intent.action)
                    && (TextUtils.equals(mPackage, intent.getPackage()))
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

        private fun serviceIntentEq(pkg: String): Intent? {
            return ArgumentMatchers.argThat(ServiceIntentMatcher(pkg))
        }
    }
}
