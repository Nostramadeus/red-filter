package io.github.nostramadeus.redfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Saved filter values and the one formula that turns them into the overlay colour. */
final class Prefs {
    // Defaults: a deep red, fairly dark look. A white pixel comes out as (148,97,88), a black pixel as (60,9,0).
    static final int DEF_TINT = 24;   // percent, strength of the colour layer
    static final int DEF_GREEN = 39;  // 0 = pure red (255,0,0) ... 160 = orange (255,160,0)
    static final int DEF_DIM = 55;    // percent, strength of the black layer under the colour layer
    static final int MAX_TINT = 80, MAX_GREEN = 160, MAX_DIM = 92;
    /** The screen never keeps less than this share of its own light, so the app stays usable. */
    static final double MIN_PASS = 0.04;

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("filter", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) { return sp(c).getBoolean("on", true); }
    static int tint(Context c) { return sp(c).getInt("tint", DEF_TINT); }
    static int green(Context c) { return sp(c).getInt("green", DEF_GREEN); }
    static int dim(Context c) { return sp(c).getInt("dim", DEF_DIM); }

    static void setOn(Context c, boolean v) { sp(c).edit().putBoolean("on", v).apply(); }
    static void set(Context c, String key, int v) { sp(c).edit().putInt(key, v).apply(); }

    /**
     * Two layers folded into one colour: a black layer (dim) and a colour layer on top of it (tint).
     * Screen result for a pixel P: P * (1-tint) * (1-dim) + colour * tint.
     */
    static int color(Context c) {
        double a1 = tint(c) / 100.0, a2 = dim(c) / 100.0;
        double pass = (1 - a1) * (1 - a2);
        if (pass < MIN_PASS) pass = MIN_PASS;
        double alpha = 1 - pass;
        if (alpha <= 0.001) return Color.TRANSPARENT;
        double r = 255 * a1 / alpha, g = green(c) * a1 / alpha;
        return Color.argb(clamp(alpha * 255), clamp(r), clamp(g), 0);
    }

    private static int clamp(double v) {
        int i = (int) Math.round(v);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
