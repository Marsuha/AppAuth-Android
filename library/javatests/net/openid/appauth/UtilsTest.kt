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
package net.openid.appauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [16])
class UtilsTest {
    @Test
    fun testCloseQuietly_close() {
        val `in`: InputStream = object : ByteArrayInputStream(TEST_STRING.toByteArray()) {
            @Throws(IOException::class)
            override fun close() {
                sIsClosed = true
                super.close()
            }
        }

        `in`.closeQuietly()
        assertTrue(sIsClosed)
    }

    @Test
    @Throws(Exception::class)
    fun testCloseQuietly_closed() {
        val `in`: InputStream = ByteArrayInputStream(TEST_STRING.toByteArray())
        `in`.close()
        `in`.closeQuietly()
    }

    @Test
    @Throws(Exception::class)
    fun testCloseQuietly_throw() {
        val `in` = Mockito.mock(InputStream::class.java)
        doThrow(IOException()).whenever(`in`).close()
        `in`.closeQuietly()
    }

    @Test
    @Throws(Exception::class)
    fun testReadInputStream() {
        val `in`: InputStream = ByteArrayInputStream(TEST_STRING.toByteArray())
        assertEquals(TEST_STRING, `in`.readString())
    }

    companion object {
        private const val TEST_STRING = "test_string\nwith a new line"
        private var sIsClosed = false
    }
}
