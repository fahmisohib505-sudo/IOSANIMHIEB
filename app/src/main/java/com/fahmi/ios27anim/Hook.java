package com.fahmi.ios27anim;

import android.content.res.Resources;
import android.graphics.Matrix;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * IOS27Anim v6 - Peluncur XOS (com.transsion.launcher3).
 * 1) Spring buka/tutup dari video iOS (v4): BUKA k=1100 z=1.10 ; TUTUP k=280 z=0.87
 * 2) Kemiringan ala iOS: shear pada matriks jendela selama animasi.
 * Root: setprop debug.ios27.tilt 0.16 (0=mati) ; tiltdir 1|-1 ; on 0|1
 * Log: su -c "logcat -d -s IOS27"
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PKG = "com.transsion.launcher3";
    private static final String TAG = "IOS27: ";

    private static volatile long ACTIVE_UNTIL = 0L;

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
        ClassLoader cl = lp.classLoader;

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

            XposedBridge.hookAllMethods(c, "start", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    ACTIVE_UNTIL = android.os.SystemClock.uptimeMillis() + 900L;
                }
            });
            log("hook TrRectFSpringAnim OK");
        } catch (Throwable t) { log("gagal hook TrRectFSpringAnim: " + t); }

        try {
            Class<?> sp = XposedHelpers.findClass("com.android.quickstep.util.SurfaceTransaction$SurfaceProperties", cl);
            XposedBridge.hookAllMethods(sp, "setMatrix", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 1 || !(p.args[0] instanceof Matrix)) return;
                        if (android.os.SystemClock.uptimeMillis() > ACTIVE_UNTIL) return;
                        float tilt = fprop("debug.ios27.tilt", 0.16f);
                        if (tilt <= 0f) return;
                        Matrix m = (Matrix) p.args[0];
                        float[] v = new float[9];
                        m.getValues(v);
                        float sx = v[Matrix.MSCALE_X];
                        if (sx >= 0.995f || sx <= 0.02f) return;
                        float sw = Resources.getSystem().getDisplayMetrics().widthPixels;
                        float sh = Resources.getSystem().getDisplayMetrics().heightPixels;
                        float sy = v[Matrix.MSCALE_Y];
                        float cx = v[Matrix.MTRANS_X] + sx * sw / 2f;
                        float cy = v[Matrix.MTRANS_Y] + sy * sh / 2f;
                        float dir = fprop("debug.ios27.tiltdir", 1f) >= 0 ? 1f : -1f;
                        float kx = dir * tilt * (1f - sx);
                        Matrix m2 = new Matrix(m);
                        m2.postSkew(kx, 0f, cx, cy);
                        p.args[0] = m2;
                    } catch (Throwable e) { log("setMatrix: " + e); }
                }
            });
            log("hook SurfaceProperties.setMatrix OK");
        } catch (Throwable t) { log("gagal hook setMatrix: " + t); }
    }
}
