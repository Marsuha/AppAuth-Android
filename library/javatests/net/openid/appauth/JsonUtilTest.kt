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
import androidx.core.net.toUri
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JsonUtilTest {
    @Test
    fun emptyJsonObject_returnsJsonObjectWithNoElements() {
        // When
        val result = emptyJsonObject()

        // Then
        assertEquals(0, result.size)
        assertEquals("{}", result.toString())
    }

    @Test
    fun `null string returns JsonNull`() {
        val result = null.toJsonElement()
        assertTrue(result is JsonNull)
    }

    @Test
    fun `empty string returns JsonPrimitive with empty string`() {
        val result = "".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals("", result.jsonPrimitive.content)
    }

    @Test
    fun `string with whitespace returns JsonPrimitive with whitespace`() {
        val result = "  \t\n  ".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals("  \t\n  ", result.jsonPrimitive.content)
    }

    @Test
    fun `string true returns JsonPrimitive boolean true`() {
        val result1 = "true".toJsonElement()
        val result2 = "TRUE".toJsonElement()
        assertTrue(result1 is JsonPrimitive)
        assertTrue(result2 is JsonPrimitive)
        assertEquals(true, result1.jsonPrimitive.boolean)
        assertEquals(true, result2.jsonPrimitive.boolean)
        assertEquals(result1.jsonPrimitive.boolean, result2.jsonPrimitive.boolean)
    }

    @Test
    fun `string false returns JsonPrimitive boolean false`() {
        val result1 = "false".toJsonElement()
        val result2 = "FALSE".toJsonElement()
        assertTrue(result1 is JsonPrimitive)
        assertTrue(result2 is JsonPrimitive)
        assertEquals(false, result1.jsonPrimitive.boolean)
        assertEquals(false, result2.jsonPrimitive.boolean)
        assertEquals(result1.jsonPrimitive.boolean, result2.jsonPrimitive.boolean)
    }

    @Test
    fun `valid integer string returns JsonPrimitive long`() {
        val result = "123".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals(123L, result.jsonPrimitive.long)
    }

    @Test
    fun `valid negative integer string returns JsonPrimitive long`() {
        val result = "-456".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals(-456L, result.jsonPrimitive.long)
    }

    @Test
    fun `valid decimal string returns JsonPrimitive double`() {
        val result = "3.14".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals(3.14, result.jsonPrimitive.double, 0.001)
    }

    @Test
    fun `exponential notation string returns JsonPrimitive double`() {
        val result = "2.5e3".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals(2500.0, result.jsonPrimitive.double, 0.001)
    }

    @Test
    fun `large number string beyond Long range returns JsonPrimitive double`() {
        val largeNumber = "9999999999999999999"
        val result = largeNumber.toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertTrue(result.jsonPrimitive.double == 1.0E19)
    }

    @Test
    fun `valid JSON object string returns JsonObject`() {
        val json = """{"key": "value", "number": 42}"""
        val result = json.toJsonElement()
        assertTrue(result is JsonObject)
        assertEquals("value", result.jsonObject["key"]?.jsonPrimitive?.content)
        assertEquals(42L, result.jsonObject["number"]?.jsonPrimitive?.long)
    }

    @Test
    fun `valid JSON array string returns JsonArray`() {
        val json = """[1, 2, 3, "four"]"""
        val result = json.toJsonElement()
        assertTrue(result is JsonArray)
        assertEquals(4, result.jsonArray.size)
        assertEquals(1L, result.jsonArray[0].jsonPrimitive.long)
        assertEquals("four", result.jsonArray[3].jsonPrimitive.content)
    }

    @Test
    fun `malformed JSON string returns JsonPrimitive with original string`() {
        val malformed = """{"key": "value", "missing": }"""
        val result = malformed.toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals(malformed, result.jsonPrimitive.content)
    }

    @Test
    fun `string with mixed content returns JsonPrimitive with original string`() {
        val result = "hello123".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals("hello123", result.jsonPrimitive.content)
    }

    @Test
    fun `string with special characters returns JsonPrimitive with original string`() {
        val result = "!@#$%^&*()".toJsonElement()
        assertTrue(result is JsonPrimitive)
        assertEquals("!@#$%^&*()", result.jsonPrimitive.content)
    }

    @Test
    fun `null string in quotes returns JsonNull`() {
        val result = "null".toJsonElement()
        assertTrue(result is JsonNull)
        assertEquals("null", result.jsonNull.content)
    }

    // === Tests for List<String>.toJsonArray() ===

    @Test
    fun `empty String list returns empty JsonArray`() {
        // Given
        val stringList = emptyList<String>()

        // When
        val result = stringList.toJsonArray()

        // Then
        assertEquals(0, result.size)
        assertEquals("[]", result.toString())
    }

    @Test
    fun `nonEmpty String list returns JsonArray with correct string values`() {
        // Given
        val stringList = listOf("first", "second", "third")

        // When
        val result = stringList.toJsonArray()

        // Then
        assertEquals(3, result.size)
        assertEquals("first", result[0].jsonPrimitive.content)
        assertEquals("second", result[1].jsonPrimitive.content)
        assertEquals("third", result[2].jsonPrimitive.content)
        assertEquals("""["first","second","third"]""", result.toString())
    }

    @Test
    fun `String list with empty strings returns JsonArray with empty strings`() {
        // Given
        val stringList = listOf("", "value", "")

        // When
        val result = stringList.toJsonArray()

        // Then
        assertEquals(3, result.size)
        assertEquals("", result[0].jsonPrimitive.content)
        assertEquals("value", result[1].jsonPrimitive.content)
        assertEquals("", result[2].jsonPrimitive.content)
        assertEquals("""["","value",""]""", result.toString())
    }

    @Test
    fun `String list with nulls is not applicable since list STRING cannot contain nulls`() {
        // Kotlin ensures non-nullability; no test needed for null elements
        // but validate that the function processes only non-nulls
        val stringList: List<String?> = listOf("a", null, "c")
        val filteredNonNull = stringList.filterNotNull()
        val expected = buildJsonArray { add("a"); add("c") }

        val result = filteredNonNull.toJsonArray()

        assertEquals(expected.toString(), result.toString())
    }

    // === Tests for List<Uri>.toJsonArray() ===

    @Test
    fun `empty Uri list returns empty JsonArray`() {
        // Given
        val uriList = emptyList<Uri>()

        // When
        val result = uriList.toJsonArray()

        // Then
        assertEquals(0, result.size)
        assertEquals("[]", result.toString())
    }

    @Test
    fun `nonEmpty Uri list returns JsonArray with string field URIs`() {
        // Given
        val uri1 = "https://example.com".toUri()
        val uri2 = "mailto:test@example.com".toUri()
        val uri3 = "custom-scheme://host/path?query=value".toUri()
        val uriList = listOf(uri1, uri2, uri3)

        // When
        val result = uriList.toJsonArray()

        // Then
        assertEquals(3, result.size)
        assertEquals("https://example.com", result[0].jsonPrimitive.content)
        assertEquals("mailto:test@example.com", result[1].jsonPrimitive.content)
        assertEquals("custom-scheme://host/path?query=value", result[2].jsonPrimitive.content)
        assertEquals(
            """["https://example.com","mailto:test@example.com","custom-scheme://host/path?query=value"]""",
            result.toString()
        )
    }

    @Test
    fun `Uri list with single element returns JsonArray with one string`() {
        // Given
        val uri = "https://single.com".toUri()
        val uriList = listOf(uri)

        // When
        val result = uriList.toJsonArray()

        // Then
        assertEquals(1, result.size)
        assertEquals("https://single.com", result[0].jsonPrimitive.content)
    }

    @Test
    fun `Uri list conversion is consistent and repeatable`() {
        // Given
        val uriList = listOf("a://1".toUri(), "b://2".toUri())

        // When
        val result1 = uriList.toJsonArray()
        val result2 = uriList.toJsonArray()

        // Then
        assertEquals(result1.toString(), result2.toString())
        assertEquals("""["a://1","b://2"]""", result1.toString())
    }

}
