package net.openid.appauth.browser

import net.openid.appauth.browser.Browsers.Chrome.standaloneBrowser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for [ExactBrowserMatcher].
 */
class ExactBrowserMatcherTest {
    @Test
    fun testMatches_same() {
        assertThat(MATCHER.matches(CHROME_55)).isTrue()
    }

    @Test
    fun testMatches_equal() {
        assertThat(MATCHER.matches(standaloneBrowser("55"))).isTrue()
    }

    @Test
    fun testMatches_differentVersion() {
        assertThat(MATCHER.matches(standaloneBrowser("54"))).isFalse()
    }

    @Test
    fun testMatches_differentKey() {
        val badChrome55Standalone = BrowserDescriptor(
            Browsers.Chrome.PACKAGE_NAME,
            setOf("BADHASH"),
            "55",
            false
        )

        assertThat(MATCHER.matches(badChrome55Standalone)).isFalse()
    }

    @Test
    fun testMatches_differentBrowser() {
        assertThat(MATCHER.matches(standaloneBrowser("50"))).isFalse()
    }

    companion object {
        private val CHROME_55 = standaloneBrowser("55")
        private val MATCHER: BrowserMatcher = ExactBrowserMatcher(CHROME_55)
    }
}
