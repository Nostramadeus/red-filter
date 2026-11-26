package io.github.nostramadeus.redfilter;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * The filter itself: one full-screen colour layer of window type ACCESSIBILITY_OVERLAY.
 * That type sits above every app, the status bar, the notification shade and the lock screen,
 * lets touches through, and has no opacity limit. The service reads no screen content.
 */
public class FilterService extends AccessibilityService {
    /** Non-null while the system has this service connected. */
    static volatile FilterService instance;

    private View view;
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { apply(); }
    };

    @Override protected void onServiceConnected() {
        instance = this;
        addView();
        apply();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(screenOn, f);
        Heal.schedule(this);
    }

    private void addView() {
        if (view != null) return;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        // A square bigger than the longest screen side: covers both rotations, the bars and the camera cutout.
        int side = Math.max(p.x, p.y) + 400;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                side, side,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = -200;
        lp.y = -200;
        lp.setTitle("RedFilter");
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setFitInsetsTypes(0);
        } else {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        View v = new View(this);
        try {
            wm.addView(v, lp);
            view = v;
        } catch (RuntimeException e) {
            view = null;
        }
    }

    /** Repaints the layer from the saved values. Safe to call from anywhere on the main thread. */
    void apply() {
        if (view == null) addView();
        if (view == null) return;
        if (Prefs.on(this)) {
            view.setBackgroundColor(Prefs.color(this));
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
        Note.show(this);
    }

    /** Repaints if the service is connected; returns false when it is not. */
    static boolean refresh() {
        FilterService s = instance;
        if (s == null) return false;
        s.apply();
        return true;
    }

    private void removeView() {
        if (view == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(view);
        } catch (RuntimeException ignored) { }
        view = null;
    }

    @Override public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        boolean wasMine = instance == this;
        if (wasMine) instance = null;
        try { unregisterReceiver(screenOn); } catch (RuntimeException ignored) { }
        removeView();
        if (wasMine) {
            try { Note.show(this); } catch (RuntimeException ignored) { }   // the notification now says "stopped"
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
