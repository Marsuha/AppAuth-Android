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
package net.openid.appauth

import net.openid.appauth.NoClientAuthentication.getRequestHeaders
import net.openid.appauth.NoClientAuthentication.getRequestParameters
import net.openid.appauth.TestValues.TEST_CLIENT_ID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoClientAuthenticationTest {
    @Test
    fun testGetRequestHeaders() {
        assertThat(getRequestHeaders(TEST_CLIENT_ID)).isNull()
    }

    @Test
    fun testGetRequestParameters() {
        assertThat(getRequestParameters(TEST_CLIENT_ID))
            .containsExactly(MapEntry.entry(TokenRequest.PARAM_CLIENT_ID, TEST_CLIENT_ID))
    }
}
