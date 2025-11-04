package net.openid.appauth.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper


internal fun Context.isActivity(): Boolean {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) {
            return true
        }
        context = context.baseContext
    }
    return false
}
