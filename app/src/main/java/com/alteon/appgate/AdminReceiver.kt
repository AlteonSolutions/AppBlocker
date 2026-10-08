package com.alteon.appgate

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Active device admin prevents uninstalling the app until admin is turned off in Settings. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)
}
