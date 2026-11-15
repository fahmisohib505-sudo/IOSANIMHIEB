package com.fahmi.ios27anim;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * IOS27Anim v4 - Peluncur XOS (com.transsion.launcher3).
 * Hanya mengubah DINAMIKA spring launcher (semua lapisan tetap sinkron).
 *   BUKA : kekakuan 1100, redaman 1.10
 *   TUTUP: kekakuan 280,  redaman 0.87
 * Root: setprop debug.ios27.open_k / open_z / close_k / close_z / on
 * Log: su -c "logcat -d -s IOS27"
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PKG = "com.transsion.launcher3";
    private static final String TAG = "IOS27: ";
    private static final String CLS = "com.android.launcher3.animation.TrRectFSpringAnim";

    private static String prop(String k, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method g = sp.getMethod("get", String.class, String.class);
            return (String) g.invoke(null, k, def);
        } catch (Throwable t) { return def; }
    }
    private static float fprop(String k, float def) {
        try { return Float.parseFloat(prop(k, String.valueOf(def))); } catch (Throwable t) { return def; }
    }
    private static void log(String m) {
        XposedBridge.log(TAG + m);
        Log.i("IOS27", m);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        log("dimuat di " + lp.packageName);
        try {
            Class<?> c = XposedHelpers.findClass(CLS, lp.classLoader);

            XposedBridge.hookAllMethods(c, "setSpringParams", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 3 || !(p.args[1] instanceof Float) || !(p.args[2] instanceof Float)) return;
                        float a = (Float) p.args[1], b = (Float) p.args[2];
                        boolean open = true;
                        try { open = (Boolean) XposedHelpers.callMethod(p.thisObject, "getIsOpenAPP"); } catch (Throwable ignored) {}
                        log((open ? "OPEN" : "CLOSE") + " setSpringParams prop=" + p.args[0] + " a=" + a + " b=" + b);
                        if (!"1".equals(prop("debug.ios27.on", "1"))) return;
                        float k = open ? fprop("debug.ios27.open_k", 1100f) : fprop("debug.ios27.close_k", 280f);
                        float z = open ? fprop("debug.ios27.open_z", 1.10f) : fprop("debug.ios27.close_z", 0.87f);
                        if (a <= 2f && b > 2f) { p.args[1] = z; p.args[2] = k; }
                        else if (b <= 2f && a > 2f) { p.args[1] = k; p.args[2] = z; }
                    } catch (Throwable e) { log("setSpringParams: " + e); }
                }
            });

            XposedBridge.hookAllMethods(c, "setSpringForceConfig", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    log("setSpringForceConfig " + (p.args.length > 0 ? p.args[0] : ""));
                }
            });
            log("hook TrRectFSpringAnim OK");
        } catch (Throwable t) { log("gagal hook: " + t); }
    }
}
