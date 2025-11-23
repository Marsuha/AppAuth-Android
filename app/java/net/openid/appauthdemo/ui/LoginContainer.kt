/*
 * Copyright 2023 The AppAuth for Android Authors. All Rights Reserved.
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.openid.appauth.browser.AnyBrowserMatcher
import net.openid.appauthdemo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginContainer(
    modifier: Modifier = Modifier,
    uiState: MainState.Unauthenticated,
    onLoginHintChanged: (String) -> Unit,
    onStartAuth: () -> Unit,
    onBrowserSelected: (BrowserInfo?) -> Unit,
    onPendingIntentModeChanged: (Boolean) -> Unit,
) {
    Column(modifier = modifier) {
        var loginHintValue by remember { mutableStateOf(TextFieldValue(uiState.loginHint)) }
        var expanded by remember { mutableStateOf(false) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onStartAuth) {
                Text(text = stringResource(id = R.string.start_authorization))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.auth_options),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = loginHintValue,
                onValueChange = {
                    loginHintValue = it
                    onLoginHintChanged(it.text)
                },
                label = { Text(stringResource(id = R.string.login_hint_value)) },
                supportingText = { Text(text = stringResource(id = R.string.account_id_description)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                val selectedBrowser = if (uiState.selectedBrowserMatcher is AnyBrowserMatcher) {
                    null
                } else {
                    uiState.browsers.first { uiState.selectedBrowserMatcher.matches(it.descriptor) }
                }

                OutlinedTextField(
                    value = selectedBrowser?.customTabLabel
                        ?: stringResource(R.string.browser_appauth_default_label),
                    onValueChange = {},
                    label = { Text(text = stringResource(id = R.string.browser_selector_label)) },
                    readOnly = true,
                    leadingIcon = {
                        selectedBrowser?.let {
                            AsyncImage(
                                model = it.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        } ?: Image(
                            painter = painterResource(R.drawable.appauth_96dp),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.browser_appauth_default_label)) },
                        leadingIcon = {
                            Image(
                                painter = painterResource(R.drawable.appauth_96dp),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = {
                            onBrowserSelected(null)
                            expanded = false
                        }
                    )

                    uiState.browsers.forEach { browserInfo ->
                        DropdownMenuItem(
                            text = { Text(text = browserInfo.customTabLabel) },
                            leadingIcon = {
                                AsyncImage(
                                    model = browserInfo.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                onBrowserSelected(browserInfo)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPendingIntentModeChanged(!uiState.isPendingIntentMode) }
                    .padding(vertical = 16.dp)
            ) {
                Checkbox(
                    checked = uiState.isPendingIntentMode,
                    onCheckedChange = { onPendingIntentModeChanged(it) }
                )
                Text(text = stringResource(id = R.string.use_pending_intents))
            }

            Text(
                text = stringResource(id = R.string.auth_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            val authEndpointStr = if (uiState.authEndpoint.isNotEmpty()) {
                "Discovered auth endpoint: \n${uiState.authEndpoint}"
            } else {
                "Static auth endpoint: \n${uiState.authEndpoint}"
            }

            Text(authEndpointStr, modifier = Modifier.fillMaxWidth())

            val clientIdStr = if (uiState.isDynamicClientId) {
                "Dynamic client ID: \n${uiState.clientId}"
            } else {
                "Static client ID: \n${uiState.clientId}"
            }
            Text(clientIdStr, modifier = Modifier.fillMaxWidth())
        }
    }
}
