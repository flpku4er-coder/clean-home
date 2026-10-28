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

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) openInputs(this);
        return true; // глотаем и DOWN, и UP, чтобы система не делала своё
    }

    static void openInputs(android.content.Context c) {
        Intent i = new Intent("com.mitv.tvhome.atv.app.tv.INPUTSOURCE_POPUP");
        i.setPackage("com.mitv.tvhome.atv");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
