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
package net.openid.appauth.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabsService

/**
 * Utility class to obtain the browser package name to be used for
 * [net.openid.appauth.AuthorizationService.performAuthorizationRequest] calls. It prioritizes browsers which support
 * [custom tabs](https://developer.chrome.com/multidevice/android/customtabs). To mitigate
 * man-in-the-middle attacks by malicious apps pretending to be browsers for the specific URI we
 * query, only those which are registered as a handler for _all_ HTTP and HTTPS URIs will be
 * used.
 */
object BrowserSelector {
    private const val SCHEME_HTTP = "http"
    private const val SCHEME_HTTPS = "https"

    /**
     * The service we expect to find on a web browser that indicates it supports custom tabs.
     */
    @VisibleForTesting
    const val ACTION_CUSTOM_TABS_CONNECTION = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION

    /**
     * Intent for querying installed web browsers as seen at
     * https://cs.android.com/android/platform/superproject/+/master:packages/modules/Permission/PermissionController/src/com/android/permissioncontroller/role/model/BrowserRoleBehavior.java
     */
    @JvmField
    @VisibleForTesting
    val BROWSER_INTENT: Intent = Intent(Intent.ACTION_VIEW).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        data = Uri.fromParts(SCHEME_HTTP, "", null)
    }

    /**
     * Retrieves the full list of browsers installed on the device. Two entries will exist
     * for each browser that supports custom tabs, with the [BrowserDescriptor.useCustomTab]
     * flag set to `true` in one and `false` in the other. The list is in the
     * order returned by the package manager, so indirectly reflects the user's preferences
     * (i.e. their default browser, if set, should be the first entry in the list).
     */
    @JvmStatic
    @SuppressLint("PackageManagerGetSignatures")
    fun getAllBrowsers(context: Context): List<BrowserDescriptor> {
        val pm = context.packageManager
        val browsers = mutableListOf<BrowserDescriptor>()

        val defaultBrowser = pm.resolveActivity(BROWSER_INTENT, 0)
            ?.activityInfo?.packageName

        val queryFlag = if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            PackageManager.GET_RESOLVED_FILTER or PackageManager.MATCH_ALL
        } else {
            PackageManager.GET_RESOLVED_FILTER
        }

        // When requesting all matching activities for an intent from the package manager,
        // the user's preferred browser is not guaranteed to be at the head of this list.
        // Therefore, the preferred browser must be separately determined and the resultant
        // list of browsers reordered to restored this desired property.
        pm.queryIntentActivities(BROWSER_INTENT, queryFlag).forEach { info ->
            // ignore handlers which are not browsers
            if (isFullBrowser(info)) {
                try {
                    val browser = info.activityInfo.packageName
                    var defaultIndex = if (browser == defaultBrowser) 0 else browsers.size

                    @Suppress("DEPRECATION")
                    val packageInfo = pm.getPackageInfo(browser, PackageManager.GET_SIGNATURES)

                    if (hasWarmupService(pm, browser)) {
                        browsers.add(defaultIndex, BrowserDescriptor(packageInfo, true))
                        defaultIndex++
                    }

                    browsers.add(defaultIndex, BrowserDescriptor(packageInfo, false))
                } catch (_: PackageManager.NameNotFoundException) {
                    // a descriptor cannot be generated without the package info
                }
            }
        }

        return browsers
    }

    /**
     * Searches through all browsers for the best match based on the supplied browser matcher.
     * Custom tab supporting browsers are preferred, if the matcher permits them, and browsers
     * are evaluated in the order returned by the package manager, which should indirectly match
     * the user's preferences.
     *
     * @param context [Context] to use for accessing [PackageManager].
     * @return The package name recommended to use for connecting to custom tabs related components.
     */
    @JvmStatic
    @SuppressLint("PackageManagerGetSignatures")
    fun select(context: Context, browserMatcher: BrowserMatcher): BrowserDescriptor? {
        val allBrowsers = getAllBrowsers(context)
        return allBrowsers.firstOrNull { browserMatcher.matches(it) && it.useCustomTab }
            ?: allBrowsers.firstOrNull { browserMatcher.matches(it) }
    }

    /**
     * Checks if the specified package supports the Custom Tabs warmup service.
     *
     * @param pm The [PackageManager] instance used to query the service.
     * @param packageName The name of the package to check for the warmup service.
     * @return `true` if the package provides the Custom Tabs warmup service, `false` otherwise.
     */
    private fun hasWarmupService(pm: PackageManager, packageName: String): Boolean {
        val serviceIntent = Intent(ACTION_CUSTOM_TABS_CONNECTION).setPackage(packageName)
        return (pm.resolveService(serviceIntent, 0) != null)
    }

    /**
     * Determines if the given ResolveInfo represents a full browser.
     *
     * A full browser must:
     * - Support the ACTION_VIEW intent.
     * - Include the CATEGORY_BROWSABLE category.
     * - Not restrict itself to specific authorities.
     * - Support both HTTP and HTTPS schemes.
     *
     * @param resolveInfo The ResolveInfo object to evaluate.
     * @return `true` if the ResolveInfo represents a full browser, `false` otherwise.
     */
    private fun isFullBrowser(resolveInfo: ResolveInfo): Boolean {
        // The filter must match ACTION_VIEW, CATEGORY_BROWSABLE, and at least one scheme.
        val filter = resolveInfo.filter ?: return false

        if (!filter.hasAction(Intent.ACTION_VIEW)
            || !filter.hasCategory(Intent.CATEGORY_BROWSABLE)
        ) return false

        // The filter must not be restricted to any particular set of authorities.
        if (filter.authoritiesIterator() != null) return false

        // The filter must support both HTTP and HTTPS.
        var supportsHttp = false
        var supportsHttps = false

        filter.schemesIterator()?.forEach {
            supportsHttp = supportsHttp || (SCHEME_HTTP == it)
            supportsHttps = supportsHttps || (SCHEME_HTTPS == it)
            if (supportsHttp && supportsHttps) return true
        } ?: return false

        // At least one of HTTP or HTTPS is not supported.
        return false
    }
}
