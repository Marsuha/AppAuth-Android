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
package net.openid.appauth.internal

import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Convenience wrapper around [Log], which evaluates the current log level of
 * the logging tag once and uses this to determine whether logging should proceed. This minimizes
 * the number of native calls made as part of logging.
 */
@Suppress("unused")
class Logger @VisibleForTesting internal constructor(private val log: LogWrapper) {
    private val logLevel: Int

    init {
        // determine the active logging level
        var level = Log.ASSERT

        while (level >= Log.VERBOSE && log.isLoggable(LOG_TAG, level)) {
            level--
        }

        logLevel = level + 1
    }

    fun log(level: Int, tr: Throwable?, message: String, vararg messageParams: Any?) {
        if (logLevel > level) return

        var formattedMessage = if (messageParams.isEmpty()) {
            message
        } else {
            String.format(message, *messageParams)
        }

        tr?.let { formattedMessage += "\n${log.getStackTraceString(it)}" }
        log.println(level, LOG_TAG, formattedMessage)
    }

    /**
     * The core interface of [Log], converted into instance methods so as to
     * allow easier mock testing.
     */
    @VisibleForTesting
    interface LogWrapper {
        fun println(level: Int, tag: String, message: String)

        fun isLoggable(tag: String, level: Int): Boolean

        fun getStackTraceString(tr: Throwable?): String
    }

    /**
     * Default [LogWrapper] implementation, using [Log] static methods.
     */
    private object AndroidLogWrapper : LogWrapper {
        override fun println(level: Int, tag: String, message: String) {
            Log.println(level, tag, message)
        }

        override fun isLoggable(tag: String, level: Int): Boolean {
            return Log.isLoggable(tag, level)
        }

        override fun getStackTraceString(tr: Throwable?): String {
            return Log.getStackTraceString(tr)
        }
    }

    companion object {
        @VisibleForTesting
        const val LOG_TAG: String = "AppAuth"

        private var _instance: Logger? = null

        @JvmStatic
        @get:Synchronized
        @set:Synchronized
        @set:VisibleForTesting
        var instance: Logger
            get() {
                if (_instance == null) _instance = Logger(AndroidLogWrapper)
                return _instance!!
            }
            set(logger) {
                _instance = logger
            }

        @JvmStatic
        fun verbose(message: String, vararg messageParams: Any?) {
            instance.log(Log.VERBOSE, null, message, *messageParams)
        }

        fun verboseWithStack(tr: Throwable?, message: String, vararg messageParams: Any?) {
            instance.log(Log.VERBOSE, tr, message, *messageParams)
        }

        @JvmStatic
        fun debug(message: String, vararg messageParams: Any?) {
            instance.log(Log.DEBUG, null, message, *messageParams)
        }

        @JvmStatic
        fun debugWithStack(tr: Throwable?, message: String, vararg messageParams: Any?) {
            instance.log(Log.DEBUG, tr, message, *messageParams)
        }

        @JvmStatic
        fun info(message: String, vararg messageParams: Any?) {
            instance.log(Log.INFO, null, message, *messageParams)
        }

        fun infoWithStack(tr: Throwable?, message: String, vararg messageParams: Any?) {
            instance.log(Log.INFO, tr, message, *messageParams)
        }

        @JvmStatic
        fun warn(message: String, vararg messageParams: Any?) {
            instance.log(Log.WARN, null, message, *messageParams)
        }

        fun warnWithStack(tr: Throwable?, message: String, vararg messageParams: Any?) {
            instance.log(Log.WARN, tr, message, *messageParams)
        }

        @JvmStatic
        fun error(message: String, vararg messageParams: Any?) {
            instance.log(Log.ERROR, null, message, *messageParams)
        }

        fun errorWithStack(tr: Throwable?, message: String, vararg messageParams: Any?) {
            instance.log(Log.ERROR, tr, message, *messageParams)
        }
    }
}
