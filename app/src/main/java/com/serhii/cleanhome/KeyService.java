package com.serhii.cleanhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Перехват кнопок пульта во всей системе.
 * Кнопка входов на пульте Xiaomi шлёт KEY_RECORD (Android: MEDIA_RECORD),
 * открываем по ней окно выбора HDMI из PatchWall.
 * Включается через ADB, см. README.
 */
public class KeyService extends AccessibilityService {

    static boolean isInputKey(int code) {
        return code == KeyEvent.KEYCODE_MEDIA_RECORD || code == KeyEvent.KEYCODE_TV_INPUT;
    }

    /**
     * Кнопки пульта → приложения. Коды смотреть: adb logcat -s CleanHomeKeys
     * Пульт Xiaomi: Prime = BUTTON_7, кнопка входов = TV_INPUT, scan 416 = BUTTON_6.
     */
    static String appForKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_7: return "com.falcofemoralis.hdrezkaapp"; // кнопка Prime (scan 207)
            default: return null;
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // журнал для настройки кнопок: adb logcat -s CleanHomeKeys
            android.util.Log.i("CleanHomeKeys", "code=" + code + " " + KeyEvent.keyCodeToString(code)
                    + " scan=" + event.getScanCode());
        }
        if (isInputKey(code)) {
            if (event.getAction() == KeyEvent.ACTION_UP) openInputs(this);
            return true; // глотаем и DOWN, и UP, чтобы система не делала своё
        }
        String pkg = appForKey(code);
        if (pkg != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) Apps.launch(this, pkg);
            return true;
        }
        return false;
    }

    static void openInputs(android.content.Context c) {
        Intent i = new Intent("com.mitv.tvhome.atv.app.tv.INPUTSOURCE_POPUP");
        i.setPackage("com.mitv.tvhome.atv");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    /**
     * Если система или пульт открыли главный экран PatchWall (или старый
     * лаунчер Google) — сразу возвращаем «Чистый экран». Окно выбора HDMI
     * (InputSourcePopupActivity) из того же пакета не трогаем.
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null || cls == null) return;
        String p = pkg.toString(), c = cls.toString();
        boolean foreignHome =
                (p.equals("com.mitv.tvhome.atv")
                        && (c.endsWith("PatchWallActivity") || c.endsWith("HomeIntentActivity")))
                || p.equals("com.google.android.tvlauncher");
        if (!foreignHome) return;
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try { startActivity(home); } catch (Exception ignored) { }
    }
    /** Служба поднялась сразу после загрузки ТВ — показываем наш экран поверх PatchWall. */
    @Override
    protected void onServiceConnected() {
        if (android.os.SystemClock.elapsedRealtime() < 5 * 60 * 1000L) {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { startActivity(home); } catch (Exception ignored) { }
        }
    }

    @Override public void onInterrupt() { }
}
