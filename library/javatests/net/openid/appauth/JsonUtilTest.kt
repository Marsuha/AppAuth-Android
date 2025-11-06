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

import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JsonUtilTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var json: JSONObject

    private lateinit var realJson: JSONObject

    @Before
    fun setUp() {
        realJson = JSONObject()
    }

    @Test
    @Throws(Exception::class)
    fun testPut() {
        json.put(TEST_KEY, TEST_STRING)
        verify(json).put(TEST_KEY, TEST_STRING)
    }

    @Test
    @Throws(Exception::class)
    fun testPutArray() {
        json.put(TEST_KEY, TEST_ARRAY)
        verify(json).put(TEST_KEY, TEST_ARRAY)
    }

    @Test
    @Throws(Exception::class)
    fun testPutJsonObject() {
        realJson.put(TEST_KEY, TEST_JSON)
        assertTrue(realJson.has(TEST_KEY))
        assertEquals(TEST_JSON, realJson.get(TEST_KEY))
    }

    @Test
    @Throws(Exception::class)
    fun testPutIfNotNullString() {
        json.putIfNotNull(TEST_KEY, TEST_STRING)
        verify(json).put(TEST_KEY, TEST_STRING)
    }

    @Test
    @Throws(Exception::class)
    fun testPutIfNotNullString_null() {
        json.putIfNotNull(TEST_KEY, null as String?)
        verify(json, never()).put(
            eq(TEST_KEY),
            any<String>()
        )
    }

    @Test(expected = RuntimeException::class)
    @Throws(Exception::class)
    fun testPutIfNotNullString_JsonException() {
        whenever(json.put(TEST_KEY, TEST_STRING))
            .thenThrow(JSONException(null as String?))

        json.putIfNotNull(TEST_KEY, TEST_STRING)
    }

    @Test
    @Throws(Exception::class)
    fun testPutIfNotNullUri() {
        realJson.putIfNotNull(TEST_KEY, TEST_URI.toString())
        assertTrue(realJson.has(TEST_KEY))
        assertEquals(TEST_URI_STRING, realJson.getString(TEST_KEY))
    }

    @Test
    @Throws(Exception::class)
    fun testPutIfNotNullUri_null() {
        realJson.putIfNotNull(TEST_KEY, null)
        assertFalse(realJson.has(TEST_KEY))
    }

    @Test(expected = RuntimeException::class)
    @Throws(Exception::class)
    fun testPutIfNotNullUri_JsonException() {
        whenever(json.put(TEST_KEY, TEST_URI_STRING))
            .thenThrow(JSONException(null as String?))

        json.putIfNotNull(TEST_KEY, TEST_URI_STRING)
    }

    @Test
    @Throws(Exception::class)
    fun testPutIfNotNullLong_null() {
        json.putIfNotNull(TEST_KEY, null as Long?)
        verify(json, never()).put(eq(TEST_KEY), any<Long>())
    }

    @Test
    @Throws(Exception::class)
    fun testPutIfNotNullJson() {
        json.putIfNotNull(TEST_KEY, TEST_JSON)
        verify(json).put(TEST_KEY, TEST_JSON)
    }

    @Test
    @Throws(Exception::class)
    fun testPutIfNotNullJson_null() {
        json.putIfNotNull(TEST_KEY, null as JSONObject?)
        verify(json, never()).put(eq(TEST_KEY), any<JSONObject>())
    }

    @Test(expected = RuntimeException::class)
    @Throws(Exception::class)
    fun testPutIfNotNullJson_JsonException() {
        whenever(json.put(TEST_KEY, TEST_JSON))
            .thenThrow(JSONException(null as String?))

        json.putIfNotNull(TEST_KEY, TEST_JSON)
    }

    @Test
    @Throws(Exception::class)
    fun testGetString() {
        whenever(json.has(TEST_KEY)).thenReturn(true)
        whenever(json.getString(TEST_KEY)).thenReturn(TEST_STRING)
        assertEquals(TEST_STRING, json.getString(TEST_KEY))
    }

    @Test(expected = JSONException::class)
    @Throws(Exception::class)
    fun testGetStringList_missing() {
        whenever(json.has(TEST_KEY)).thenReturn(false)
        json.getStringList(TEST_KEY)
    }

    @Test
    @Throws(Exception::class)
    fun testGetStringMap() {
        val mapObj = JSONObject().apply {
            put("a", "1")
            put("b", "2")
            put("c", "3")
        }

        realJson.put(TEST_KEY, mapObj)
        val map = realJson.getStringMap(TEST_KEY)
        assertEquals(mapObj.length().toLong(), map.entries.size.toLong())
        assertTrue(map.containsKey("a"))
        assertTrue(map.containsKey("b"))
        assertTrue(map.containsKey("c"))
        assertEquals("1", map["a"])
        assertEquals("2", map["b"])
        assertEquals("3", map["c"])
    }

    companion object {
        private const val TEST_KEY = "key"
        private const val TEST_STRING = "value"
        private const val TEST_LONG: Long = 123
        private const val TEST_URI_STRING = "https://openid.net/"
        private val TEST_URI: Uri? = Uri.parse(TEST_URI_STRING)
        private val TEST_JSON = JSONObject()
        private val TEST_ARRAY = JSONArray()

        init {
            try {
                TEST_JSON.put("a", "b")
            } catch (_: JSONException) {
                throw IllegalStateException("unable to configure test objects")
            }
        }
    }
}
