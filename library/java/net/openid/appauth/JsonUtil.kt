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
@file:Suppress("unused")

package net.openid.appauth

import android.net.Uri
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import androidx.core.net.toUri

/**
 * Creates a metadata value abstraction with the given key and default value.
 */
abstract class Field<T>(
    /**
     * The metadata key within the discovery document.
     */
    @JvmField val key: String,
    /**
     * The default value for this metadata entry, as defined by the OpenID Connect
     * specification.
     */
    val defaultValue: T?
) {
    /**
     * Converts the string representation of the value to the correct type.
     */
    abstract fun convert(value: String): T
}

/**
 * Creates a metadata value abstraction with the given key and default URI value.
 */
class UriField @JvmOverloads constructor(
    key: String,
    defaultValue: Uri? = null
) : Field<Uri>(key, defaultValue) {
    override fun convert(value: String): Uri {
        return value.toUri()
    }
}

/**
 * Creates a metadata abstraction with the given key and string default value.
 */
class StringField @JvmOverloads constructor(
    key: String,
    defaultValue: String? = null
) : Field<String>(key, defaultValue) {
    override fun convert(value: String): String {
        return value
    }
}

/**
 * Creates a metadata abstraction with the given key and default boolean value.
 */
class BooleanField(
    key: String,
    defaultValue: Boolean
) : Field<Boolean>(key, defaultValue) {
    override fun convert(value: String): Boolean {
        return value.toBoolean()
    }
}

abstract class ListField<T>(
    @JvmField val key: String,
    val defaultValue: List<T>?
) {
    abstract fun convert(value: String): T
}

class StringListField @JvmOverloads constructor(
    key: String,
    defaultValue: List<String>? = null
) : ListField<String>(key, defaultValue) {
    override fun convert(value: String): String {
        return value
    }
}

@Suppress("UNCHECKED_CAST")
@VisibleForTesting
fun JSONObject.putIfNotNull(field: String, value: Any?) {
    if (value == null) return

    try {
        when (value) {
            is Collection<*> -> put(field, JSONArray(value))

            is Map<*, *> -> {
                val map = value as Map<String, Any?>
                val valueObj = JSONObject()
                map.forEach { valueObj.putIfNotNull(it.key, it.value) }
                put(field, valueObj)
            }

            else -> put(field, value)
        }
    } catch (ex: JSONException) {
        throw IllegalStateException("JSONException thrown in violation of contract", ex)
    }
}

fun JSONObject.getStringListIfDefined(field: String): List<String>? {
    if (!has(field)) return null
    return getJSONArray(field).toStringList()
}

fun JSONObject.getStringIfDefined(field: String): String? {
    if (!has(field)) return null
    return getString(field)
}

fun JSONObject.getUriIfDefined(field: String): Uri? {
    if (!has(field)) return null
    return getUri(field)
}

@Throws(JSONException::class)
fun JSONObject.getUri(field: String) = getString(field).toUri()

@Throws(JSONException::class)
fun JSONObject.getLongIfDefined(field: String): Long? {
    if (!has(field) || isNull(field)) return null

    return try {
        getLong(field)
    } catch (_: JSONException) {
        null
    }
}

@Throws(JSONException::class)
fun JSONObject.getStringList(field: String): List<String> {
    if (!has(field)) {
        throw JSONException("field \"$field\" not found in json object")
    }

    return getJSONArray(field).toStringList()
}

@Throws(JSONException::class)
fun JSONObject.getUriList(field: String): List<Uri> {
    if (!has(field)) {
        throw JSONException("field \"$field\" not found in json object")
    }

    return getJSONArray(field).toUriList()
}

@Throws(JSONException::class)
fun JSONObject.getStringMap(field: String): Map<String, String> {
    if (!has(field)) return emptyMap()
    val json = getJSONObject(field)

    return json.keys().asSequence()
        .associateWith { json.getString(it) }
}

@Throws(JSONException::class)
fun JSONObject.getJsonObjectIfDefined(field: String): JSONObject? {
    if (!has(field)) return null

    val value = optJSONObject(field)
    return value ?: throw JSONException("field \"$field\" is mapped to a null value")
}

@Throws(JSONException::class)
fun JSONObject.toMap(): Map<String, Any> {
    return keys().asSequence().associateWith {
        when (val value = get(it)) {
            is JSONArray -> value.toList()
            is JSONObject -> value.toMap()
            else -> value
        }
    }
}

@Throws(JSONException::class)
fun JSONArray.toStringList(): List<String> {
    return List(length()) {
        get(it).toString()
    }
}

@Throws(JSONException::class)
fun JSONArray.toList(): List<Any> {
    return List(length()) {
        when (val value = get(it)) {
            is JSONArray -> value.toList()
            is JSONObject -> value.toMap()
            else -> value
        }
    }
}

@Throws(JSONException::class)
fun JSONArray.toUriList(): List<Uri> {
    return List(length()) {
        get(it).toString().toUri()
    }
}

fun Iterable<*>.toJsonArray(): JSONArray {
    val jsonArray = JSONArray()
    forEach { jsonArray.put(it.toString()) }
    return jsonArray
}

fun Map<String, String>.toJsonObject(): JSONObject {
    val json = JSONObject()
    forEach { json.put(it.key, it.value) }
    return json
}

operator fun <T> JSONObject.get(field: Field<T>): T? {
    try {
        if (!has(field.key)) return field.defaultValue
        return field.convert(getString(field.key))
    } catch (e: JSONException) {
        // all appropriate steps are taken above to avoid a JSONException. If it is still
        // thrown, indicating an implementation change, throw an exception
        throw IllegalStateException("unexpected JSONException", e)
    }
}


operator fun <T> JSONObject.get(field: ListField<T>): List<T>? {
    try {
        if (!has(field.key)) return field.defaultValue
        val value = get(field.key)

        check(value is JSONArray) {
            ("${field.key} does not contain the expected JSON array")
        }

        val arrayValue: JSONArray = value

        return List(arrayValue.length()) {
            field.convert(arrayValue.getString(it))
        }
    } catch (e: JSONException) {
        // all appropriate steps are taken above to avoid a JSONException. If it is still
        // thrown, indicating an implementation change, throw an excpetion
        throw IllegalStateException("unexpected JSONException", e)
    }
}
