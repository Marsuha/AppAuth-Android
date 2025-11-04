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

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [16])
class RedirectUriReceiverActivityTest {
    @Test
    fun testForwardsRedirectToManagementActivity() {
        val redirectUri = Uri.parse("https://www.example.com/oauth2redirect")
        val redirectIntent = Intent(
            ApplicationProvider.getApplicationContext<Application>(),
            RedirectUriReceiverActivity::class.java
        ).apply { data = redirectUri }

        val scenario = ActivityScenario.launch<RedirectUriReceiverActivity>(redirectIntent)

        scenario.onActivity { activity ->
            assertWithMessage("Activity should be finishing after forwarding the intent")
                .that(activity.isFinishing).isTrue()
        }

        val shadowApplication = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        val nextIntent = shadowApplication.nextStartedActivity

        assertWithMessage("An activity should have been started").that(nextIntent).isNotNull()
        assertWithMessage("The forwarded intent should have the correct data URI")
            .that(nextIntent.data)
            .isEqualTo(redirectUri)
    }
}
