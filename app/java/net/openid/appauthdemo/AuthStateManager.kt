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
package net.openid.appauthdemo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.AnyThread
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.RegistrationResponse
import net.openid.appauth.TokenResponse

/**
 * An example persistence mechanism for an [AuthState] instance.
 * This stores the instance in a shared preferences file, and provides thread-safe access and
 * mutation.
 */
object AuthStateManager {
    private const val TAG = "AuthStateManager"
    private const val STORE_NAME = "AuthState"
    private const val KEY_STATE = "state"
    private lateinit var prefs: SharedPreferences
    private val mutex = Mutex()
    private var currentAuthState: AuthState? = null

    fun init(context: Context): AuthStateManager {
        prefs = context.applicationContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
        return this
    }

    @AnyThread
    suspend fun getCurrent(): AuthState {
        currentAuthState?.let { return it }

        return mutex.withLock {
            // check again in case another coroutine initialized it while we were waiting
            currentAuthState?.let { return@withLock it }
            val state = withContext(Dispatchers.IO) { readState() }
            currentAuthState = state
            state
        }
    }

    @AnyThread
    suspend fun replace(state: AuthState) = mutex.withLock {
        withContext(Dispatchers.IO) { writeState(state) }
        currentAuthState = state
        state
    }

    private suspend fun updateState(action: (AuthState) -> Unit): AuthState {
        val currentState = getCurrent()
        action(currentState)
        return replace(currentState)
    }

    @AnyThread
    suspend fun updateAfterAuthorization(
        response: AuthorizationResponse?,
        ex: AuthorizationException?
    ): AuthState = updateState { it.update(response, ex) }

    @AnyThread
    suspend fun updateAfterTokenResponse(
        response: TokenResponse?,
        ex: AuthorizationException?
    ): AuthState = updateState { it.update(response, ex) }

    @AnyThread
    suspend fun updateAfterRegistration(
        response: RegistrationResponse?,
        ex: AuthorizationException?
    ): AuthState {
        if (ex != null) return getCurrent()
        return updateState { it.update(response) }
    }

    private fun readState(): AuthState {
        val currentState = prefs.getString(KEY_STATE, null) ?: return AuthState()

        return try {
            AuthState.fromJsonString(currentState)
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Failed to deserialize stored auth state - discarding")
            AuthState()
        }
    }

    private fun writeState(state: AuthState?) {
        prefs.edit(commit = true) {
            if (state == null) {
                remove(KEY_STATE)
            } else {
                putString(KEY_STATE, state.asJsonString)
            }
        }
    }
}