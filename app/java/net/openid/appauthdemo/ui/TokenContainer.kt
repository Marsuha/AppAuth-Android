/*
 * Copyright 2024 The AppAuth for Android Authors. All Rights Reserved.
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
package net.openid.appauthdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.openid.appauthdemo.R
import net.openid.appauthdemo.ui.theme.AppAuthDemoTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.text.format

@Composable
fun TokenContainer(
    modifier: Modifier = Modifier,
    uiState: MainState.Authenticated,
    onRefreshAccessToken: () -> Unit,
    onFetchUserInfo: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(id = R.string.auth_granted),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.titleLarge
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (uiState.canRefresh) {
                Button(onClick = onRefreshAccessToken) {
                    Text(
                        text = stringResource(id = R.string.refresh_token),
                        maxLines = 1
                    )
                }
            }

            if (uiState.canFetchUserInfo) {
                Button(onClick = onFetchUserInfo) {
                    Text(
                        text = stringResource(id = R.string.view_profile),
                        maxLines = 1
                    )
                }
            }

            Button(onClick = onSignOut) {
                Text(
                    text = stringResource(id = R.string.sign_out),
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = if (uiState.refreshToken != null) stringResource(R.string.refresh_token_returned)
            else stringResource(R.string.no_refresh_token_returned)
        )

        Text(
            text = if (uiState.idToken != null) stringResource(R.string.id_token_returned)
            else stringResource(R.string.no_id_token_returned)
        )

        val accessTokenInfo = when {
            uiState.accessToken == null -> stringResource(R.string.no_access_token_returned)
            uiState.accessTokenExpirationTime == null -> stringResource(R.string.no_access_token_expiry)
            uiState.accessTokenExpirationTime < System.currentTimeMillis() -> stringResource(R.string.access_token_expired)
            else -> {
                val template = stringResource(R.string.access_token_expires_at)

                String.format(
                    template,
                    formatMillisToDateTimeString(uiState.accessTokenExpirationTime)
                )
            }
        }
        Text(text = accessTokenInfo)

        uiState.userInfo?.let {
            UserInfoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                userInfo = it
            )
        }
    }
}

@Composable
fun UserInfoCard(
    modifier: Modifier = Modifier,
    userInfo: JsonObject
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pictureUrl = userInfo["picture"]?.jsonPrimitive?.content
                AsyncImage(
                    model = pictureUrl,
                    contentDescription = stringResource(id = R.string.userinfo_profile_content_description),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = CircleShape
                        ),
                    placeholder = painterResource(id = R.drawable.unknown_user_48dp)
                )

                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = userInfo["name"]?.jsonPrimitive?.content ?: "???",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            val prettyJson = Json { prettyPrint = true }
            Text(
                text = prettyJson.encodeToString(JsonObject.serializer(), userInfo),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenAuthenticatedPreview() {
    AppAuthDemoTheme {
        TokenContainer(
            uiState = MainState.Authenticated(
                accessToken = "accessToken",
                accessTokenExpirationTime = 0L,
                idToken = "idToken",
                refreshToken = "refreshToken",
                canRefresh = true,
                canFetchUserInfo = true,
                userInfo = Json.parseToJsonElement("""{"name": "test"}""") as JsonObject
            ),
            onRefreshAccessToken = {},
            onFetchUserInfo = {},
            onSignOut = {},
        )
    }
}

private fun formatMillisToDateTimeString(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss ZZ", Locale.getDefault())
    val instant = Instant.ofEpochMilli(timestamp)
    return instant.atZone(ZoneId.systemDefault()).format(formatter)
}