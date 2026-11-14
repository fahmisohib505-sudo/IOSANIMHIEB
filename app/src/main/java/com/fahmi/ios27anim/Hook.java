package com.fahmi.ios27anim;

import android.content.res.Resources;
import android.graphics.RectF;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * IOS27Anim - XOS Launcher 17 (com.transsion.launcher3).
 * Rect/alpha/radius jendela buka-tutup-swipe-up diambil dari tabel keyframe hasil ukur video iOS.
 * Root: setprop debug.ios27.speed 1.0
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PKG = "com.transsion.launcher3";
    private static final String TAG = "IOS27: ";
    private static final String CLS = "com.android.launcher3.animation.TrRectFSpringAnim";

    private static final float VW = 576f, VH = 1248f;
    private static final float DT = 1f / 60f;

    private static final float[][] OPEN = {
        {  47, 105, 137,  195, 0.00f, 1.00f},
        {  20, 180, 262,  490, 0.65f, 0.35f},
        {  16, 190, 322,  661, 1.00f, 0.00f},
        {  14, 171, 387,  806, 1.00f, 0},
        {   6, 161, 439,  913, 1.00f, 0},
        {  10, 137, 473, 1004, 1.00f, 0},
        {   4, 111, 500, 1077, 1.00f, 0},
        {   4, 101, 524, 1125, 1.00f, 0},
        {   0,  90, 544, 1165, 1.00f, 0},
        {   0,  80, 554, 1190, 1.00f, 0},
        {   0,  60, 574, 1210, 1.00f, 0},
        {   0,   0, 576, 1248, 1.00f, 0},
    };
    private static final float[][] CLOSE = {
        {   0,   0, 576, 1248, 1.00f, 0.00f},
        {  26,  48, 546, 1156, 1.00f, 0.00f},
        {  52,  97, 516, 1065, 1.00f, 0.00f},
        {  60, 137, 463,  944, 1.00f, 0.00f},
        {  58, 181, 411,  810, 1.00f, 0.00f},
        {  56, 192, 362,  696, 1.00f, 0.00f},
        {  50, 188, 308,  589, 0.95f, 0.00f},
        {  46, 172, 266,  498, 0.80f, 0.20f},
        {  42, 161, 228,  423, 0.50f, 0.55f},
        {  36, 147, 197,  363, 0.00f, 1.00f},
    };

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

    private static final class St {
        long t0 = System.nanoTime();
        boolean open;
        RectF icon, screen;
        float tOff = 0f, dx = 0f, dy = 0f;
    }

    private static volatile Object CUR;

    private static St state(Object anim) {
        return anim == null ? null : (St) XposedHelpers.getAdditionalInstanceField(anim, "ios27");
    }

    private static void log(String m) {
        XposedBridge.log(TAG + m);
        Log.i("IOS27", m);
    }

    private static float[] sample(float[][] tab, float t) {
        float f = t / DT;
        int n = tab.length;
        if (f <= 0) return tab[0].clone();
        if (f >= n - 1) return tab[n - 1].clone();
        int i = (int) f; float a = f - i;
        float[] o = new float[6];
        for (int k = 0; k < 6; k++) o[k] = tab[i][k] + (tab[i + 1][k] - tab[i][k]) * a;
        return o;
    }

    private static St makeState(Object anim, RectF orig, RectF tgt) {
        St s = new St();
        try { s.open = (Boolean) XposedHelpers.callMethod(anim, "getIsOpenAPP"); } catch (Throwable e) { s.open = true; }
        float sw = Resources.getSystem().getDisplayMetrics().widthPixels;
        float sh = Resources.getSystem().getDisplayMetrics().heightPixels;
        s.screen = new RectF(0, 0, sw, sh);
        if (s.open) { s.icon = new RectF(orig); }
        else        { s.icon = new RectF(tgt); }
        if (!s.open) {
            float want = orig.width() / sw;
            if (want < 0.97f) {
                float bestT = 0, bestD = 9f;
                float end = (CLOSE.length - 1) * DT;
                for (float t = 0; t <= end; t += DT / 4f) {
                    float[] r = sample(CLOSE, t);
                    float d = Math.abs((r[2] - r[0]) / VW - want);
                    if (d < bestD) { bestD = d; bestT = t; }
                }
                s.tOff = bestT;
                float[] r = sample(CLOSE, bestT);
                s.dx = orig.centerX() - (r[0] + r[2]) / 2f / VW * sw;
                s.dy = orig.centerY() - (r[1] + r[3]) / 2f / VH * sh;
            }
        }
        log((s.open ? "OPEN" : "CLOSE") + " orig=" + orig + " target=" + tgt + " tOff=" + s.tOff);
        return s;
    }

    private static float[] frame(St s) {
        float speed = fprop("debug.ios27.speed", 1.0f);
        float[][] tab = s.open ? OPEN : CLOSE;
        float t = (System.nanoTime() - s.t0) / 1e9f * speed + s.tOff;
        float[] r = sample(tab, t);
        float sw = s.screen.width(), sh = s.screen.height();
        float l = r[0] / VW * sw, tp = r[1] / VH * sh, rt = r[2] / VW * sw, b = r[3] / VH * sh;
        if (s.dx != 0f || s.dy != 0f) {
            float end = (tab.length - 1) * DT;
            float u = Math.max(0f, Math.min(1f, (t - s.tOff) / Math.max(1e-3f, end - s.tOff)));
            float k = 1f - u;
            l += s.dx * k; rt += s.dx * k; tp += s.dy * k; b += s.dy * k;
        }
        float w = r[5];
        l  += (s.icon.left   - l)  * w;  tp += (s.icon.top    - tp) * w;
        rt += (s.icon.right  - rt) * w;  b  += (s.icon.bottom - b)  * w;
        float radius = Math.min(0.13f * sw, 0.13f * (rt - l));
        return new float[]{l, tp, rt, b, r[4], radius};
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        log("dimuat di " + lp.packageName);

        try {
            Class<?> c = XposedHelpers.findClass(CLS, lp.classLoader);

            XposedBridge.hookAllMethods(c, "start", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    XposedHelpers.setAdditionalInstanceField(p.thisObject, "ios27", null);
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        CUR = p.thisObject;
                        XposedHelpers.callMethod(p.thisObject, "getCurrentRect");
                    } catch (Throwable e) { log("start: " + e); }
                }
            });

            XposedBridge.hookAllMethods(c, "getCurrentRect", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        RectF orig = (RectF) p.getResult();
                        if (orig == null) return;
                        St s = state(p.thisObject);
                        if (s == null) {
                            RectF tgt = (RectF) XposedHelpers.callMethod(p.thisObject, "getTargetRect");
                            s = makeState(p.thisObject, orig, tgt);
                            XposedHelpers.setAdditionalInstanceField(p.thisObject, "ios27", s);
                        }
                        float[] f = frame(s);
                        p.setResult(new RectF(f[0], f[1], f[2], f[3]));
                    } catch (Throwable e) { log("rect: " + e); }
                }
            });

            XposedBridge.hookAllMethods(c, "getCurrentAlpha", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    St s = state(p.thisObject);
                    if (s != null) p.setResult(frame(s)[4]);
                }
            });

            XposedBridge.hookAllMethods(c, "getCurrentCornerRadius", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    St s = state(p.thisObject);
                    if (s != null) p.setResult(frame(s)[5]);
                }
            });
            log("hook TrRectFSpringAnim OK");
        } catch (Throwable t) { log("gagal hook TrRectFSpringAnim: " + t); }

        String[] sinks = {
            "com.android.launcher3.animation.TrWindowAnimRunner",
            "com.android.quickstep.BaseLauncherSwipeUpHandler$TrSpringAnimationRunner",
        };
        for (String cn : sinks) {
            try {
                Class<?> k = XposedHelpers.findClass(cn, lp.classLoader);
                XposedBridge.hookAllMethods(k, "onUpdate", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args.length != 7 || !(p.args[2] instanceof RectF)) return;
                            St s = state(CUR);
                            if (s == null) return;
                            float[] f = frame(s);
                            p.args[2] = new RectF(f[0], f[1], f[2], f[3]);
                        } catch (Throwable e) { log("onUpdate: " + e); }
                    }
                });
                log("hook " + cn + ".onUpdate OK");
            } catch (Throwable t) { log("gagal hook " + cn + ": " + t); }
        }
    }
}
