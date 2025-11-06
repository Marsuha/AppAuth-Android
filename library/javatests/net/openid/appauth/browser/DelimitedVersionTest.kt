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
package net.openid.appauth.browser

import net.openid.appauth.browser.DelimitedVersion.Companion.parse
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DelimitedVersionTest {
    @Test
    fun testParse_emptyString() {
        assertThat(parse("").toString()).isEqualTo("0")
    }

    @Test
    fun testParse_singleVersion() {
        assertThat(parse("1").toString()).isEqualTo("1")
    }

    @Test
    fun testParse_singleVersionWithPrefix() {
        assertThat(parse("version1").toString()).isEqualTo("1")
    }

    @Test
    fun testParse_singleVersionWithSuffix() {
        assertThat(parse("1a").toString()).isEqualTo("1")
    }

    @Test
    fun testParse_versionWithTrailingZeros() {
        assertThat(parse("1.0.0").toString()).isEqualTo("1")
    }

    @Test
    fun testParse_centerZerosNotIgnored() {
        assertThat(parse("1.0.1.0").toString()).isEqualTo("1.0.1")
    }

    @Test
    fun testParse_versionWithDifferentDelimiters() {
        assertThat(parse("1.2-2202").toString()).isEqualTo("1.2.2202")
    }

    @Test
    fun testParse_withLongVersionSegment() {
        assertThat(parse("v12.16.04-20161014162559").toString())
            .isEqualTo("12.16.4.20161014162559")
    }

    @Test
    fun testEquals_null() {
        assertThat(parse("1.2")).isNotEqualTo(null)
    }

    @Test
    fun testCompare_withSelf() {
        val version = parse("1.2")
        assertThat(version.compareTo(version)).isEqualTo(0)
        assertThat(version).isEqualTo(version)
        assertThat(version.hashCode()).isEqualTo(version.hashCode())
    }

    @Test
    fun testCompare_withEquivalent() {
        val version = parse("1.2")
        val equivalent = parse("1.2.0.0")
        assertThat(version.compareTo(equivalent)).isEqualTo(0)
        assertThat(version).isEqualTo(equivalent)
        assertThat(version.hashCode()).isEqualTo(equivalent.hashCode())
    }

    @Test
    fun testCompare() {
        val version = parse("1.2.1")
        val other = parse("1.3")
        assertThat(version.compareTo(other)).isEqualTo(-1)
        assertThat(other.compareTo(version)).isEqualTo(1)
        assertThat(version).isNotEqualTo(other)
    }
}
