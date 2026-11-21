package io.github.nostramadeus.redfilter;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The controls: on/off switch and three sliders. Every change shows on the screen at once.
 * Main = the full screen (app icon). Popup = the same controls as a small window (notification).
 */
public class Main extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView status;
    private Switch on;
    private SeekBar tint, green, dim;
    private TextView tintLabel, greenLabel, dimLabel;
    private boolean loading;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            showStatus();
            h.postDelayed(this, 1000);
        }
    };

    /** True in the small window: no title, no setup buttons, less padding. */
    protected boolean popup() { return false; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final boolean small = popup();
        int pad = dp(20);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, small ? dp(14) : dp(48), pad, small ? dp(10) : pad);

        if (!small) col.addView(text("Red Filter", 26));
        status = text("", small ? 13 : 15);
        status.setPadding(0, small ? 0 : dp(6), 0, small ? dp(4) : dp(18));
        col.addView(status);

        on = new Switch(this);
        on.setText("Filter on");
        on.setTextSize(TypedValue.COMPLEX_UNIT_SP, small ? 18 : 20);
        on.setPadding(0, dp(8), 0, small ? dp(10) : dp(20));
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) {
                if (loading) return;
                Prefs.setOn(Main.this, checked);
                push();
            }
        });
        col.addView(on);

        int labelSize = small ? 16 : 18;
        dimLabel = text("", labelSize);
        col.addView(dimLabel);
        dim = bar(col, Prefs.MAX_DIM, "dim", small);
        tintLabel = text("", labelSize);
        col.addView(tintLabel);
        tint = bar(col, Prefs.MAX_TINT, "tint", small);
        greenLabel = text("", labelSize);
        col.addView(greenLabel);
        green = bar(col, Prefs.MAX_GREEN, "green", small);

        Button reset = new Button(this);
        reset.setText("Back to the default look");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.set(Main.this, "tint", Prefs.DEF_TINT);
                Prefs.set(Main.this, "green", Prefs.DEF_GREEN);
                Prefs.set(Main.this, "dim", Prefs.DEF_DIM);
                load();
                push();
            }
        });
        col.addView(reset);

        if (!small && Build.VERSION.SDK_INT >= 33) {
            Button tile = new Button(this);
            tile.setText("Add on/off tile to quick settings");
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { addTile(); }
            });
            col.addView(tile);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
        if (small) {
            // a window at the bottom, nearly as wide as the screen; the page behind stays visible and is not dimmed
            setFinishOnTouchOutside(true);
            getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f), ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setGravity(Gravity.BOTTOM);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        load();
        // Android 13+: the notification needs this permission; the system asks once
        if (!Note.allowed(this)) requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        Note.show(this);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
        Heal.schedule(this);
        Note.show(this);
        h.postDelayed(new Runnable() {
            @Override public void run() { Heal.check(Main.this); }
        }, 400);
        h.post(poll);
    }

    @Override protected void onPause() {
        h.removeCallbacks(poll);
        super.onPause();
    }

    private void load() {
        loading = true;
        on.setChecked(Prefs.on(this));
        tint.setProgress(Prefs.tint(this));
        green.setProgress(Prefs.green(this));
        dim.setProgress(Prefs.dim(this));
        loading = false;
        labels();
        showStatus();
    }

    private void labels() {
        dimLabel.setText("Darkness: " + Prefs.dim(this) + "%");
        tintLabel.setText("Colour strength: " + Prefs.tint(this) + "%");
        int g = Prefs.green(this);
        greenLabel.setText("Colour: " + (g < 30 ? "pure red" : g < 80 ? "deep red" : g < 125 ? "red-orange" : "orange")
                + " (" + g + ")");
    }

    private void showStatus() {
        if (FilterService.instance != null) {
            status.setText(Prefs.on(this) ? "Running." : "Service running, filter switched off.");
        } else if (!Heal.canWrite(this)) {
            status.setText("Not running. Switch it on: Settings > Accessibility > Downloaded apps > Red Filter.");
        } else {
            status.setText("Not running. Restarting it now...");
        }
    }

    /** Repaint the overlay with the saved values; reconnect the service if it is gone. */
    private void push() {
        labels();
        if (!FilterService.refresh()) { Heal.check(this); Note.show(this); }
        showStatus();
    }

    private SeekBar bar(LinearLayout col, int max, final String key, boolean small) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setPadding(dp(12), dp(14), dp(12), small ? dp(18) : dp(26));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (loading || !fromUser) return;
                Prefs.set(Main.this, key, value);
                push();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        col.addView(s);
        return s;
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT < 33) return;
        StatusBarManager sbm = (StatusBarManager) getSystemService(StatusBarManager.class);
        sbm.requestAddTileService(new ComponentName(this, Tile.class), "Red Filter",
                Icon.createWithResource(this, getResources().getIdentifier("ic_tile", "drawable", getPackageName())),
                getMainExecutor(),
                new java.util.function.Consumer<Integer>() {
                    @Override public void accept(Integer result) { }
                });
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
