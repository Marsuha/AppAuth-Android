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
package net.openid.appauth.internal

import android.util.Log
import net.openid.appauth.internal.Logger.Companion.debug
import net.openid.appauth.internal.Logger.Companion.debugWithStack
import net.openid.appauth.internal.Logger.Companion.error
import net.openid.appauth.internal.Logger.Companion.info
import net.openid.appauth.internal.Logger.Companion.instance
import net.openid.appauth.internal.Logger.Companion.verbose
import net.openid.appauth.internal.Logger.Companion.warn
import net.openid.appauth.internal.Logger.LogWrapper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LoggerTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var mockLockWrap: LogWrapper

    @Test
    @Throws(Exception::class)
    fun testVerbose_whenVerboseLevel() {
        configureLog(Log.VERBOSE)
        verbose("Test")
        verify(mockLockWrap).println(Log.VERBOSE, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testVerbose_whenDebugLevel() {
        configureLog(Log.DEBUG)
        verbose("Test")
        verify(mockLockWrap, never()).println(
            any<Int>(),
            any<String>(),
            any<String>()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDebug_whenVerboseLevel() {
        configureLog(Log.VERBOSE)
        debug("Test")
        verify(mockLockWrap).println(Log.DEBUG, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testDebug_withMessageParams() {
        configureLog(Log.VERBOSE)
        debug("Test %s %d", "extra", INT)
        verify(mockLockWrap)
            .println(Log.DEBUG, Logger.LOG_TAG, "Test extra 100")
    }

    @Test
    @Throws(Exception::class)
    fun testDebugWithStack() {
        configureLog(Log.VERBOSE)
        debugWithStack(Exception(), "Bad things happened in %s", "MyClass")
        verify(mockLockWrap).println(
            Log.DEBUG, Logger.LOG_TAG,
            "Bad things happened in MyClass\nSTACK"
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDebug_whenDebugLevel() {
        configureLog(Log.DEBUG)
        debug("Test")
        verify(mockLockWrap).println(Log.DEBUG, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testDebug_whenInfoLevel() {
        configureLog(Log.INFO)
        debug("Test")
        verify(mockLockWrap, Mockito.never()).println(
            any<Int>(),
            any<String>(),
            any<String>()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testInfo_whenDebugLevel() {
        configureLog(Log.DEBUG)
        info("Test")
        verify(mockLockWrap).println(Log.INFO, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testInfo_whenInfoLevel() {
        configureLog(Log.INFO)
        info("Test")
        verify(mockLockWrap).println(Log.INFO, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testInfo_whenWarnLevel() {
        configureLog(Log.WARN)
        info("Test")
        verify(mockLockWrap, Mockito.never()).println(
            any<Int>(),
            any<String>(),
            any<String>()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testWarn_whenInfoLevel() {
        configureLog(Log.INFO)
        warn("Test")
        verify(mockLockWrap).println(Log.WARN, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testWarn_whenWarnLevel() {
        configureLog(Log.WARN)
        warn("Test")
        verify(mockLockWrap).println(Log.WARN, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testWarn_whenErrorLevel() {
        configureLog(Log.ERROR)
        warn("Test")
        verify(mockLockWrap, Mockito.never()).println(
            any<Int>(),
            any<String>(),
            any<String>()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testError_whenWarnLevel() {
        configureLog(Log.WARN)
        error("Test")
        verify(mockLockWrap).println(Log.ERROR, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testError_whenErrorLevel() {
        configureLog(Log.ERROR)
        error("Test")
        verify(mockLockWrap).println(Log.ERROR, Logger.LOG_TAG, "Test")
    }

    @Test
    @Throws(Exception::class)
    fun testError_whenAssertLevel() {
        configureLog(Log.ASSERT)
        error("Test")
        verify(mockLockWrap, never()).println(
            any<Int>(),
            any<String>(),
            any<String>()
        )
    }

    private fun configureLog(minLevel: Int) {
        (Log.VERBOSE..Log.ASSERT).forEach {
            whenever(mockLockWrap.isLoggable(Logger.LOG_TAG, it))
                .thenReturn(it >= minLevel)
        }

        whenever(mockLockWrap.getStackTraceString(any<Throwable>())) doReturn "STACK"
        instance = Logger(mockLockWrap)
    }

    companion object {
        private const val INT = 100
    }
}
