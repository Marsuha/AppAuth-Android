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

import android.content.Intent
import android.net.Uri
import androidx.test.ext.truth.content.IntentSubject.assertThat
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RedirectUriReceiverActivityTest {

    @Test
    fun testForwardsRedirectToManagementActivity() {
        val redirectUri = Uri.parse("https://www.example.com/oauth2redirect")
        val redirectIntent = Intent().apply { data = redirectUri }

        val redirectController = buildActivity(
            RedirectUriReceiverActivity::class.java,
            redirectIntent
        ).create()

        val redirectActivity = redirectController.get() as RedirectUriReceiverActivity

        val nextIntent: Intent? = shadowOf(redirectActivity).nextStartedActivity
        assertThat(nextIntent).hasData(redirectUri)
        assertThat(redirectActivity.isFinishing).isTrue()
    }
}
