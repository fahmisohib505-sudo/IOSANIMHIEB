package com.fahmi.ios27anim;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * IOS27Anim v5 - Peluncur XOS (com.transsion.launcher3).
 * 1) Menyalakan animasi SKEW/vertex-deformation bawaan launcher.
 * 2) Spring buka/tutup disetel dari ukuran video iOS.
 * Root: setprop debug.ios27.skew 0|1 ; debug.ios27.on 0|1 ; open_k/open_z/close_k/close_z
 * Log: su -c "logcat -d -s IOS27"
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PKG = "com.transsion.launcher3";
    private static final String TAG = "IOS27: ";

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

    private static void forceTrue(final ClassLoader cl, String cls, final String method) {
        try {
            Class<?> c = XposedHelpers.findClass(cls, cl);
            XposedBridge.hookAllMethods(c, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!"1".equals(prop("debug.ios27.skew", "1"))) return;
                        Object r = p.getResult();
                        if (r instanceof Boolean && !((Boolean) r)) {
                            log(method + " asli=false -> true");
                            p.setResult(Boolean.TRUE);
                        }
                    } catch (Throwable e) { log(method + ": " + e); }
                }
            });
            log("hook " + cls + "." + method + " OK");
        } catch (Throwable t) { log("gagal hook " + cls + "." + method + ": " + t); }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        log("dimuat di " + lp.packageName);
        ClassLoader cl = lp.classLoader;

        forceTrue(cl, "com.android.launcher3.animation.WindowAnimUtil", "skewMethodEnable");
        forceTrue(cl, "com.android.launcher3.util.Partner", "shouldUseSkewAnim");
        forceTrue(cl, "com.android.quickstep.LauncherSwipeHandlerV2", "shouldUseSkewSwipeHomeAnimation");

        try {
            Class<?> c = XposedHelpers.findClass("com.android.launcher3.animation.TrRectFSpringAnim", cl);
            XposedBridge.hookAllMethods(c, "setSpringParams", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 3 || !(p.args[1] instanceof Float) || !(p.args[2] instanceof Float)) return;
                        float a = (Float) p.args[1], b = (Float) p.args[2];
                        boolean open = true;
                        try { open = (Boolean) XposedHelpers.callMethod(p.thisObject, "getIsOpenAPP"); } catch (Throwable ignored) {}
                        log((open ? "OPEN" : "CLOSE") + " setSpringParams a=" + a + " b=" + b);
                        if (!"1".equals(prop("debug.ios27.on", "1"))) return;
                        float k = open ? fprop("debug.ios27.open_k", 1100f) : fprop("debug.ios27.close_k", 280f);
                        float z = open ? fprop("debug.ios27.open_z", 1.10f) : fprop("debug.ios27.close_z", 0.87f);
                        if (a <= 2f && b > 2f) { p.args[1] = z; p.args[2] = k; }
                        else if (b <= 2f && a > 2f) { p.args[1] = k; p.args[2] = z; }
                    } catch (Throwable e) { log("setSpringParams: " + e); }
                }
            });
            log("hook TrRectFSpringAnim OK");
        } catch (Throwable t) { log("gagal hook TrRectFSpringAnim: " + t); }

        try {
            Class<?> v = XposedHelpers.findClass("com.android.launcher3.animation.TrVertexDeformationAnim", cl);
            XposedBridge.hookAllMethods(v, "start", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { log("TrVertexDeformationAnim.start"); }
            });
        } catch (Throwable t) { log("vertex: " + t); }
    }
}
