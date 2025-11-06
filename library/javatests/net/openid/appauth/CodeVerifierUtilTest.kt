/*
 * Copyright 2017 The AppAuth for Android Authors. All Rights Reserved.
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

import net.openid.appauth.CodeVerifierUtil.checkCodeVerifier
import net.openid.appauth.CodeVerifierUtil.generateRandomCodeVerifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CodeVerifierUtilTest {
    @Test
    fun checkCodeVerifier_tooShort_throwsException() {
        val codeVerifier = createString(CodeVerifierUtil.MIN_CODE_VERIFIER_LENGTH - 1)
        try {
            checkCodeVerifier(codeVerifier)
            Assert.fail("expected exception not thrown")
        } catch (ex: IllegalArgumentException) {
            assertThat(ex.message)
                .isEqualTo("codeVerifier length is shorter than allowed by the PKCE specification")
        }
    }

    @Test
    fun checkCodeVerifier_tooLong_throwsException() {
        val codeVerifier = createString(CodeVerifierUtil.MAX_CODE_VERIFIER_LENGTH + 1)
        try {
            checkCodeVerifier(codeVerifier)
            Assert.fail("expected exception not thrown")
        } catch (ex: IllegalArgumentException) {
            assertThat(ex.message)
                .isEqualTo("codeVerifier length is longer than allowed by the PKCE specification")
        }
    }

    @Test
    fun generateRandomCodeVerifier_tooLittleEntropy_throwsException() {
        try {
            generateRandomCodeVerifier(
                SecureRandom(),
                CodeVerifierUtil.MIN_CODE_VERIFIER_ENTROPY - 1
            )
            Assert.fail("expected exception not thrown")
        } catch (ex: IllegalArgumentException) {
            assertThat(ex.message)
                .isEqualTo("entropyBytes is less than the minimum permitted")
        }
    }

    @Test
    fun generateRandomCodeVerifier_tooMuchEntropy_throwsException() {
        try {
            generateRandomCodeVerifier(
                SecureRandom(),
                CodeVerifierUtil.MAX_CODE_VERIFIER_ENTROPY + 1
            )
            Assert.fail("expected exception not thrown")
        } catch (ex: IllegalArgumentException) {
            assertThat(ex.message)
                .isEqualTo("entropyBytes is greater than the maximum permitted")
        }
    }

    private fun createString(length: Int): String {
        val strChars = CharArray(length)
        for (i in strChars.indices) {
            strChars[i] = 'a'
        }
        return String(strChars)
    }
}
