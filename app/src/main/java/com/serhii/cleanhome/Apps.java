package com.serhii.cleanhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Список установленных приложений и избранное (то, что на главном экране). */
final class Apps {
    private Apps() {}

    static final class AppInfo {
        String pkg;
        String label;
        Drawable banner; // широкий TV-баннер, если есть
        Drawable icon;
    }

    private static final String PREFS = "home";
    private static final String KEY_FAVS = "favs";

    /** Что положить на главный при первом запуске, если установлено. */
    private static final String[] DEFAULTS = {
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.hbo.hbonow",
            "com.spotify.tv.android",
            "com.plexapp.android",
    };

    static List<AppInfo> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppInfo> map = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || map.containsKey(pkg)) continue;
                AppInfo a = new AppInfo();
                a.pkg = pkg;
                a.label = ri.loadLabel(pm).toString();
                a.banner = ri.activityInfo.loadBanner(pm);
                if (a.banner == null) a.banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                a.icon = ri.loadIcon(pm);
                map.put(pkg, a);
            }
        }
        List<AppInfo> list = new ArrayList<>(map.values());
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return list;
    }

    static void launch(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (li == null) li = pm.getLaunchIntentForPackage(pkg);
        if (li == null) {
            Toast.makeText(c, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show();
            return;
        }
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(li);
    }

    // ---------- избранное ----------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<String> favPackages(Context c, List<AppInfo> all) {
        SharedPreferences p = prefs(c);
        List<String> out = new ArrayList<>();
        if (!p.contains(KEY_FAVS)) {
            // первый запуск: популярные стриминги + всё, где в названии есть "rezka"
            for (String d : DEFAULTS) {
                for (AppInfo a : all) if (a.pkg.equals(d)) out.add(d);
            }
            for (AppInfo a : all) {
                String s = (a.label + " " + a.pkg).toLowerCase(Locale.ROOT);
                if (s.contains("rezka") && !out.contains(a.pkg)) out.add(a.pkg);
            }
            save(c, out);
            return out;
        }
        String raw = p.getString(KEY_FAVS, "");
        if (raw.isEmpty()) return out;
        for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    static List<AppInfo> favorites(Context c) {
        List<AppInfo> all = loadAll(c);
        List<AppInfo> out = new ArrayList<>();
        for (String pkg : favPackages(c, all)) {
            for (AppInfo a : all) if (a.pkg.equals(pkg)) { out.add(a); break; }
        }
        return out;
    }

    static boolean isFav(Context c, String pkg) {
        return favPackages(c, loadAll(c)).contains(pkg);
    }

    /** Возвращает true, если приложение теперь в избранном. */
    static boolean toggleFav(Context c, String pkg) {
        List<String> favs = favPackages(c, loadAll(c));
        boolean added;
        if (favs.contains(pkg)) { favs.remove(pkg); added = false; }
        else { favs.add(pkg); added = true; }
        save(c, favs);
        return added;
    }

    private static void save(Context c, List<String> favs) {
        prefs(c).edit().putString(KEY_FAVS, String.join(",", favs)).apply();
    }
}
