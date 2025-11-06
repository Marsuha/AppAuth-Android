package net.openid.appauth.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.google.common.truth.Truth
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomTabManagerTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var client: CustomTabsClient

    @Captor
    lateinit var connectIntentCaptor: ArgumentCaptor<Intent>

    @Captor
    lateinit var connectionCaptor: ArgumentCaptor<CustomTabsServiceConnection>

    private lateinit var manager: CustomTabManager

    @Before
    fun setUp() {
        manager = CustomTabManager(context)
    }

    @Test
    fun testBind() = runTest {
        startBind(true)
        provideClient()

        // the mock client should now be available on the manager
        assertThat(manager.getClient()).isEqualTo(client)
    }

    @Test
    fun testBind_browserDoesNotSupportCustomTabs() = runTest {
        startBind(false)
        assertThat(manager.getClient()).isEqualTo(null)
    }

    @Suppress("DEPRECATION")
    @Test
    fun testCreateSession() = runTest {
        // Arrange: Setup mocks and test data
        startBind(true)
        provideClient()

        val mockCallbacks = mock<CustomTabsCallback>()
        val mockSession = mock<CustomTabsSession>()

        // Use 'whenever' for more idiomatic stubbing
        whenever(client.newSession(mockCallbacks)) doReturn mockSession

        val primaryUri = Uri.parse("https://idp.example.com")
        val otherPossibleUri1 = Uri.parse("https://another.example.com")
        val otherPossibleUri2 = Uri.parse("https://yetanother.example.com")

        // Act: Call the method under test
        val session = manager.createSession(
            mockCallbacks,
            primaryUri,
            otherPossibleUri1,
            otherPossibleUri2
        )

        // Assert: Verify the results
        Truth.assertThat(session).isEqualTo(mockSession)

        // Group verifications for clarity and use an idiomatic argument captor
        val otherUrisCaptor = argumentCaptor<List<Bundle>>()

        verify(mockSession).mayLaunchUrl(
            eq(primaryUri),
            eq(null), // Verifying the second argument is explicitly null
            otherUrisCaptor.capture()
        )

        // Assert on the captured argument
        val capturedBundles = otherUrisCaptor.firstValue
        Truth.assertThat(capturedBundles).hasSize(2)
        assertThat(capturedBundles[0].get(CustomTabsService.KEY_URL)).isEqualTo(otherPossibleUri1)
        assertThat(capturedBundles[1].get(CustomTabsService.KEY_URL)).isEqualTo(otherPossibleUri2)
    }

    @Test
    fun testCreateSession_browserDoesNotSupportCustomTabs() = runTest {
        startBind(false)
        assertThat(manager.createSession(null)).isNull()
    }

    @Test
    fun testCreateSession_creationFails() = runTest {
        startBind(true)
        provideClient()

        // Emulate session creation failure - annoyingly the contract for this is just to return
        // null, rather than an exception with some useful context.
        val mockCallbacks = mock<CustomTabsCallback>()
        whenever(client.newSession(mockCallbacks)) doReturn null
        assertThat(manager.createSession(mockCallbacks)).isNull()
    }

    @Test
    fun testDispose() {
        startBind(true)
    }

    private fun startBind(succeed: Boolean) {
        whenever(
            context.bindService(
                connectIntentCaptor.capture(),
                connectionCaptor.capture(),
                any<Int>()
            )
        ) doReturn succeed

        manager.bind(BROWSER_PACKAGE_NAME)

        // check the service connection is made to the specified package
        val intent = connectIntentCaptor.value
        assertThat(intent.`package`).isEqualTo(BROWSER_PACKAGE_NAME)
    }

    private fun provideClient() {
        val conn = connectionCaptor.value
        conn.onCustomTabsServiceConnected(
            ComponentName(BROWSER_PACKAGE_NAME, "$BROWSER_PACKAGE_NAME.CustomTabsService"),
            client
        )
    }

    companion object {
        private const val BROWSER_PACKAGE_NAME = "com.example.browser"
    }
}
