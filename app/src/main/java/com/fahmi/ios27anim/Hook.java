package com.fahmi.ios27anim;

import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * IOS27Anim v8 - Peluncur XOS (com.transsion.launcher3). TANPA skew bawaan launcher.
 *  - Spring buka/tutup dari video iOS.
 *  - Trapesium buatan sendiri (VertexSet sendiri) sesuai baris ikon:
 *      atas  : bawah lebih lebar | tengah: hampir lurus | bawah & dock: atas lebih lebar
 *  - Bila mesh gagal/dimatikan: shear v6.
 * Root (tanpa build ulang):
 *   setprop debug.ios27.mesh 0|1     (default 1)
 *   setprop debug.ios27.taper 0.20   (kekuatan trapesium)
 *   setprop debug.ios27.vmode 0|1    (0 = offset piksel, 1 = koordinat absolut)
 *   setprop debug.ios27.tilt 0.16    (shear cadangan saat mesh=0)
 *   setprop debug.ios27.open_k/open_z/close_k/close_z
 * Log: su -c "logcat -d -s IOS27"
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PKG = "com.transsion.launcher3";
    private static final String TAG = "IOS27: ";
    private static final String VS = "com.android.launcher3.animation.TrVertexDeformationAnim$VertexSet";

    private static volatile long ACTIVE_UNTIL = 0L;
    private static volatile float REGION = 0f;      // -1 atas ... +1 dock
    private static volatile boolean REGION_SET = false;
    private static volatile boolean GUARD = false;
    private static int vlogCount = 0;

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
    private static void log(String m) { XposedBridge.log(TAG + m); Log.i("IOS27", m); }

    private static void setPt(Object vs, String f, float x, float y) {
        try {
            PointF p = (PointF) XposedHelpers.getObjectField(vs, f);
            p.set(x, y);
        } catch (Throwable e) { log("setPt " + f + ": " + e); }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        log("dimuat di " + lp.packageName);
        final ClassLoader cl = lp.classLoader;

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
                    REGION_SET = false;
                    vlogCount = 0;
                }
            });
            log("hook TrRectFSpringAnim OK");
        } catch (Throwable t) { log("gagal hook TrRectFSpringAnim: " + t); }

        try {
            Class<?> sp = XposedHelpers.findClass("com.android.quickstep.util.SurfaceTransaction$SurfaceProperties", cl);
            XposedBridge.hookAllMethods(sp, "setMatrix", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (GUARD) return;
                        if (p.args.length < 1 || !(p.args[0] instanceof Matrix)) return;
                        if (android.os.SystemClock.uptimeMillis() > ACTIVE_UNTIL) return;
                        Matrix m = (Matrix) p.args[0];
                        float[] v = new float[9];
                        m.getValues(v);
                        float sx = v[Matrix.MSCALE_X], sy = v[Matrix.MSCALE_Y];
                        if (sx >= 0.995f || sx <= 0.02f) return;
                        float sw = Resources.getSystem().getDisplayMetrics().widthPixels;
                        float sh = Resources.getSystem().getDisplayMetrics().heightPixels;
                        float left = v[Matrix.MTRANS_X], top = v[Matrix.MTRANS_Y];
                        float w = sx * sw, h = sy * sh;
                        float cy = top + h / 2f;

                        if (!REGION_SET) {   // wilayah dari posisi awal (dekat ikon)
                            REGION = Math.max(-1f, Math.min(1f, (cy / sh - 0.5f) * 2f));
                            REGION_SET = true;
                            log("region=" + REGION + " cy/sh=" + (cy / sh));
                        }
                        if (!"1".equals(prop("debug.ios27.mesh", "1"))) return;

                        float taper = fprop("debug.ios27.taper", 0.20f);
                        float inset = taper * (1f - sx) * w * Math.abs(REGION);
                        float topIn = REGION < 0 ? inset : 0f;
                        float botIn = REGION > 0 ? inset : 0f;
                        boolean abs = "1".equals(prop("debug.ios27.vmode", "0"));
                        float ox = abs ? left : 0f, oy = abs ? top : 0f;
                        float mid = (topIn + botIn) / 2f;

                        Object vs = XposedHelpers.newInstance(XposedHelpers.findClass(VS, cl));
                        setPt(vs, "mTopLeft",     ox + topIn,        oy);
                        setPt(vs, "mTopRight",    ox + w - topIn,    oy);
                        setPt(vs, "mMiddleLeft",  ox + mid,          oy + h / 2f);
                        setPt(vs, "mMiddleRight", ox + w - mid,      oy + h / 2f);
                        setPt(vs, "mBottomLeft",  ox + botIn,        oy + h);
                        setPt(vs, "mBottomRight", ox + w - botIn,    oy + h);
                        if (vlogCount++ < 3) log("mesh vs=" + vs + " sx=" + sx);
                        GUARD = true;
                        try { XposedHelpers.callMethod(p.thisObject, "setLayerEffect", vs); }
                        finally { GUARD = false; }
                    } catch (Throwable e) { GUARD = false; log("mesh: " + e); }
                }
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {   // shear cadangan bila mesh mati
                        if (!"0".equals(prop("debug.ios27.mesh", "1"))) return;
                        if (android.os.SystemClock.uptimeMillis() > ACTIVE_UNTIL) return;
                        float tilt = fprop("debug.ios27.tilt", 0.16f);
                        if (tilt <= 0f || p.args.length < 1 || !(p.args[0] instanceof Matrix)) return;
                        Matrix m = (Matrix) p.args[0];
                        float[] v = new float[9];
                        m.getValues(v);
                        float sx = v[Matrix.MSCALE_X];
                        if (sx >= 0.995f || sx <= 0.02f) return;
                        float sw = Resources.getSystem().getDisplayMetrics().widthPixels;
                        float sh = Resources.getSystem().getDisplayMetrics().heightPixels;
                        float cx = v[Matrix.MTRANS_X] + sx * sw / 2f;
                        float cy = v[Matrix.MTRANS_Y] + v[Matrix.MSCALE_Y] * sh / 2f;
                        Matrix m2 = new Matrix(m);
                        m2.postSkew(tilt * (1f - sx), 0f, cx, cy);
                        p.args[0] = m2;
                    } catch (Throwable e) { log("shear: " + e); }
                }
            });
            log("hook setMatrix OK");
        } catch (Throwable t) { log("gagal hook setMatrix: " + t); }
    }
}
