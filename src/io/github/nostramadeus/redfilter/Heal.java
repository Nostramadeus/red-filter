package io.github.nostramadeus.redfilter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Keeps the filter service connected. Some phones kill the app and then leave the accessibility
 * service listed as "on" but not running. The cure is to take the service out of the system's list and put
 * it back, which makes the system connect it again. That needs WRITE_SECURE_SETTINGS (granted once over USB).
 * Runs when the app is opened, when the tile is used, after a restart, and every 15 minutes.
 */
public class Heal extends BroadcastReceiver {
    static final String KEY = "enabled_accessibility_services";
    /** Sent by the notification button "Turn off / Turn on". */
    static final String TOGGLE = "io.github.nostramadeus.redfilter.TOGGLE";
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static boolean busy;

    @Override public void onReceive(final Context c, Intent i) {
        if (TOGGLE.equals(i.getAction())) {
            Prefs.setOn(c, !Prefs.on(c));
            if (!FilterService.refresh()) check(c);
            Note.show(c);
            return;
        }
        schedule(c);
        if (FilterService.instance != null) return;
        final PendingResult r = goAsync();
        // A just-started process may still be getting its service connected: look after a short wait.
        H.postDelayed(new Runnable() {
            @Override public void run() {
                check(c);
                H.postDelayed(new Runnable() {
                    @Override public void run() {
                        Note.show(c);   // still not connected: the notification now says "stopped"
                        r.finish();
                    }
                }, 2500);
            }
        }, 2000);
    }

    /**
     * Opens the system page that holds this app's accessibility switch.
     * Falls back to the general accessibility page on a phone that has no page per app.
     */
    static void openSettings(Context c) {
        try {
            c.startActivity(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, me(c))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (RuntimeException ignored) { }
        try {
            c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) { }
    }

    static boolean canWrite(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    static String me(Context c) {
        return new ComponentName(c, FilterService.class).flattenToString();
    }

    /** True when this app's service is in the system's "enabled" list (it may still not be running). */
    static boolean listed(Context c) {
        String cur = Settings.Secure.getString(c.getContentResolver(), KEY);
        if (cur == null) return false;
        String me = me(c);
        for (String s : cur.split(":")) if (s.equals(me)) return true;
        return false;
    }

    /** Reconnects the service when it is not running. Does nothing when it is, or when the permission is missing. */
    static void check(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (FilterService.instance != null || busy || !canWrite(c)) return;
        busy = true;
        final ContentResolver cr = c.getContentResolver();
        final String me = me(c);
        String cur = Settings.Secure.getString(cr, KEY);
        final StringBuilder others = new StringBuilder();
        if (cur != null) {
            for (String s : cur.split(":")) {
                if (s.length() == 0 || s.equals(me)) continue;
                if (others.length() > 0) others.append(':');
                others.append(s);
            }
        }
        try {
            Settings.Secure.putString(cr, KEY, others.toString());
        } catch (RuntimeException e) {
            busy = false;
            return;
        }
        H.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    Settings.Secure.putString(cr, KEY, others.length() > 0 ? others + ":" + me : me);
                    Settings.Secure.putInt(cr, "accessibility_enabled", 1);
                } catch (RuntimeException ignored) { }
                H.postDelayed(new Runnable() {
                    @Override public void run() { busy = false; }
                }, 3000);
            }
        }, 700);
    }

    /** One repeating wake-up every 15 minutes; setting it again replaces the old one. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, Heal.class).setAction("io.github.nostramadeus.redfilter.TICK");
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi);
    }
}
