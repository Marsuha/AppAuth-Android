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

import net.openid.appauth.AsciiStringListUtil.iterableToString
import net.openid.appauth.AsciiStringListUtil.stringToSet
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [16])
class AsciiStringListUtilTest {
    @Test
    @Throws(Exception::class)
    fun testScopeIterableToString() {
        assertEquals(SCOPE_STRING, iterableToString(SCOPES))
    }

    @Test
    @Throws(Exception::class)
    fun testScopeStringToSet() {
        val result = stringToSet("email profile email openid")
        Assert.assertNotNull(result)
        assertEquals(SCOPES.size.toLong(), result!!.size.toLong())
        Assert.assertTrue(result.contains("email"))
        Assert.assertTrue(result.contains("profile"))
        Assert.assertTrue(result.contains("openid"))
    }

    companion object {
        private val SCOPES = listOf("email", "profile", "openid")
        private const val SCOPE_STRING = "email profile openid"
    }
}
