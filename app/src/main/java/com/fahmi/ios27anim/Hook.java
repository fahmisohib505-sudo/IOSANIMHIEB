package com.fahmi.ios27anim;

import android.graphics.RectF;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * IOS27Anim - XOS Launcher 17 (com.transsion.XOSLauncher).
 * Rect/alpha/radius jendela buka-tutup diambil dari tabel keyframe hasil ukur video iOS
 * (layar referensi 576x1248, 60 fps), bukan dari spring launcher.
 *
 * Root:  setprop debug.ios27.speed 1.0   (1.0 = persis video)
 *        setprop debug.ios27.log 1
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String PKG = "com.transsion.XOSLauncher";
    private static final String TAG = "IOS27: ";
    private static final String CLS = "com.android.launcher3.animation.TrRectFSpringAnim";

    private static final float VW = 576f, VH = 1248f;
    private static final float DT = 1f / 60f;

    // {left, top, right, bottom (px video), alpha, bobot menuju rect ikon}
    private static final float[][] OPEN = {
        {  47, 105, 137,  195, 0.00f, 1.00f}, // f340 ikon
        {  20, 180, 262,  490, 0.65f, 0.35f}, // f341
        {  16, 190, 322,  661, 1.00f, 0.00f}, // f342
        {  14, 171, 387,  806, 1.00f, 0},     // f343
        {   6, 161, 439,  913, 1.00f, 0},     // f344
        {  10, 137, 473, 1004, 1.00f, 0},     // f345
        {   4, 111, 500, 1077, 1.00f, 0},     // f346
        {   4, 101, 524, 1125, 1.00f, 0},     // f347
        {   0,  90, 544, 1165, 1.00f, 0},     // f348
        {   0,  80, 554, 1190, 1.00f, 0},     // f349
        {   0,  60, 574, 1210, 1.00f, 0},     // f350
        {   0,   0, 576, 1248, 1.00f, 0},     // f351 layar penuh
    };
    private static final float[][] CLOSE = {
        {   0,   0, 576, 1248, 1.00f, 0.00f}, // f374 layar penuh
        {  26,  48, 546, 1156, 1.00f, 0.00f}, // f375 (interpolasi)
        {  52,  97, 516, 1065, 1.00f, 0.00f}, // f376
        {  60, 137, 463,  944, 1.00f, 0.00f}, // f377
        {  58, 181, 411,  810, 1.00f, 0.00f}, // f378
        {  56, 192, 362,  696, 1.00f, 0.00f}, // f379
        {  50, 188, 308,  589, 0.95f, 0.00f}, // f380
        {  46, 172, 266,  498, 0.80f, 0.20f}, // f381
        {  42, 161, 228,  423, 0.50f, 0.55f}, // f382
        {  36, 147, 197,  363, 0.00f, 1.00f}, // f383 -> rect ikon
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
    }

    private static St state(Object anim) {
        return (St) XposedHelpers.getAdditionalInstanceField(anim, "ios27");
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

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        XposedBridge.log(TAG + "dimuat di " + lp.packageName);
        try {
            Class<?> c = XposedHelpers.findClass(CLS, lp.classLoader);

            XposedBridge.hookAllMethods(c, "start", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    XposedHelpers.setAdditionalInstanceField(p.thisObject, "ios27", null);
                }
            });

            XposedBridge.hookAllMethods(c, "getCurrentRect", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        RectF orig = (RectF) p.getResult();
                        if (orig == null) return;
                        St s = state(p.thisObject);
                        if (s == null) {
                            s = new St();
                            try { s.open = (Boolean) XposedHelpers.callMethod(p.thisObject, "getIsOpenAPP"); }
                            catch (Throwable e) { s.open = true; }
                            RectF tgt = (RectF) XposedHelpers.callMethod(p.thisObject, "getTargetRect");
                            if (s.open) { s.icon = new RectF(orig); s.screen = new RectF(tgt); }
                            else        { s.screen = new RectF(orig); s.icon = new RectF(tgt); }
                            XposedHelpers.setAdditionalInstanceField(p.thisObject, "ios27", s);
                            if ("1".equals(prop("debug.ios27.log", "0")))
                                XposedBridge.log(TAG + (s.open ? "OPEN" : "CLOSE") + " icon=" + s.icon + " screen=" + s.screen);
                        }
                        float t = (System.nanoTime() - s.t0) / 1e9f * fprop("debug.ios27.speed", 1.0f);
                        float[] r = sample(s.open ? OPEN : CLOSE, t);
                        float sw = s.screen.width(), sh = s.screen.height();
                        float l = s.screen.left + r[0] / VW * sw, tp = s.screen.top + r[1] / VH * sh;
                        float rt = s.screen.left + r[2] / VW * sw, b = s.screen.top + r[3] / VH * sh;
                        float w = r[5];
                        l  += (s.icon.left   - l)  * w;  tp += (s.icon.top    - tp) * w;
                        rt += (s.icon.right  - rt) * w;  b  += (s.icon.bottom - b)  * w;
                        p.setResult(new RectF(l, tp, rt, b));
                    } catch (Throwable e) { XposedBridge.log(TAG + "rect: " + e); }
                }
            });

            XposedBridge.hookAllMethods(c, "getCurrentAlpha", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    St s = state(p.thisObject);
                    if (s == null) return;
                    float t = (System.nanoTime() - s.t0) / 1e9f * fprop("debug.ios27.speed", 1.0f);
                    p.setResult(sample(s.open ? OPEN : CLOSE, t)[4]);
                }
            });

            XposedBridge.hookAllMethods(c, "getCurrentCornerRadius", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    St s = state(p.thisObject);
                    if (s == null) return;
                    float t = (System.nanoTime() - s.t0) / 1e9f * fprop("debug.ios27.speed", 1.0f);
                    float[] r = sample(s.open ? OPEN : CLOSE, t);
                    float wpx = (r[2] - r[0]) / VW * s.screen.width();
                    float maxR = 0.13f * s.screen.width();
                    p.setResult(Math.min(maxR, 0.13f * wpx));
                }
            });
            XposedBridge.log(TAG + "hook TrRectFSpringAnim OK");
        } catch (Throwable t) { XposedBridge.log(TAG + "gagal hook: " + t); }
    }
}
