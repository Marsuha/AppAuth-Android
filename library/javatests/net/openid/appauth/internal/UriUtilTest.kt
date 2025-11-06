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
package net.openid.appauth.internal

import android.net.Uri
import android.net.UrlQuerySanitizer
import androidx.browser.customtabs.CustomTabsService
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UriUtilTest {
    private lateinit var sanitizer: UrlQuerySanitizer

    @Before
    fun setUp() {
        sanitizer = UrlQuerySanitizer()
        sanitizer.allowUnregisteredParamaters = true
        sanitizer.unregisteredParameterValueSanitizer = UrlQuerySanitizer.getUrlAndSpaceLegal()
    }

    @Test
    fun testFormUrlEncode() {
        val parameters = mapOf(
            "test1" to "value1",
            "test2" to "value2"
        )

        val query = parameters.formUrlEncode()
        sanitizer.parseQuery(query)

        parameters.forEach {
            Assertions.assertThat(sanitizer.getValue(it.key)).isEqualTo(it.value)
        }
    }

    @Test
    fun testFormUrlEncode_withSpaceSeparatedValueForParameter() {
        val parameters = mapOf(
            "test1" to "value1",
            "test2" to "value2 value3"
        )

        val query = parameters.formUrlEncode()

        Assertions.assertThat(query).contains("value2+value3")
        sanitizer.parseQuery(query)

        parameters.forEach {
            Assertions.assertThat(sanitizer.getValue(it.key)).isEqualTo(it.value)
        }
    }

    @Test
    fun testFormUrlEncode_withNull() {
        Assertions.assertThat(null.formUrlEncode()).isEqualTo("")
    }

    @Test
    fun testFormUrlEncode_withEmpty() {
        Assertions.assertThat(emptyMap<String, String>().formUrlEncode()).isEqualTo("")
    }

    @Test
    fun testToCustomTabUri() {
        val exampleUri = Uri.parse("https://www.example.com")
        val anotherExampleUri = Uri.parse("https://another.example.com")

        val bundles = arrayOf(exampleUri, anotherExampleUri).toCustomTabUriBundle()

        Assertions.assertThat(bundles).hasSize(2)
        Assertions.assertThat(bundles[0].keySet()).contains(CustomTabsService.KEY_URL)
        Assertions.assertThat(bundles[0].get(CustomTabsService.KEY_URL)).isEqualTo(exampleUri)
        Assertions.assertThat(bundles[1].keySet()).contains(CustomTabsService.KEY_URL)
        Assertions.assertThat(bundles[1].get(CustomTabsService.KEY_URL))
            .isEqualTo(anotherExampleUri)
    }

    @Test
    fun testToCustomTabUri_startIndex() {
        val anotherExampleUri = Uri.parse("https://another.example.com")

        val bundles = arrayOf(
            Uri.parse("https://www.example.com"),
            anotherExampleUri
        ).toCustomTabUriBundle(1)

        Assertions.assertThat(bundles).hasSize(1)
        Assertions.assertThat(bundles[0].keySet()).contains(CustomTabsService.KEY_URL)
        Assertions.assertThat(bundles[0].get(CustomTabsService.KEY_URL))
            .isEqualTo(anotherExampleUri)
    }

    @Test
    fun testToCustomTabUriBundle_emptyArray() {
        Assertions.assertThat(arrayOf<Uri?>(null).toCustomTabUriBundle(0))
            .isEmpty()
    }

    @Test
    fun testToCustomTabUriBundle_startIndexOutsideArray() {
        val bundles = arrayOf(
            Uri.parse("https://www.example.com"),
            Uri.parse("https://another.example.com")
        ).toCustomTabUriBundle(2)

        Assertions.assertThat(bundles).hasSize(0)
    }
}
