package com.serhii.cleanhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Телевизор сбрасывает службы спецвозможностей при перезагрузке.
 * Включаем KeyService обратно сами — при загрузке ТВ и при каждом
 * открытии главного экрана. Нужно разрешение, выдаётся один раз через ADB:
 *   adb shell pm grant com.serhii.cleanhome android.permission.WRITE_SECURE_SETTINGS
 */
public class ServiceGuard extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        ensure(c);
    }

    static void ensure(Context c) {
        try {
            String me = new ComponentName(c, KeyService.class).flattenToString();
            String cur = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur != null && cur.contains(me)) return;
            String next = (cur == null || cur.isEmpty()) ? me : cur + ":" + me;
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(c.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception ignored) {
            // разрешение не выдано — ничего не делаем
        }
    }
}
