package io.github.nostramadeus.redfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * The always-there notification: tap it (or "Adjust") for the small slider window, "Turn off / Turn on" for the switch.
 * Shown again after every change, so its text follows the state.
 */
final class Note {
    static final String CHANNEL = "filter";
    static final int ID = 1;

    private Note() {}

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    static void show(Context ctx) {
        Context c = ctx.getApplicationContext();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Filter controls", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        if (!allowed(c)) return;
        boolean on = Prefs.on(c);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(c, 2,
                new Intent(c, Popup.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags);
        PendingIntent toggle = PendingIntent.getBroadcast(c, 3,
                new Intent(c, Heal.class).setAction(Heal.TOGGLE), flags);
        // The filter should be on but the phone has closed the service: say so, and make one tap lead to the fix.
        boolean stopped = on && FilterService.instance == null;
        PendingIntent fix = PendingIntent.getActivity(c, 4,
                new Intent(c, Main.class).putExtra(Main.FIX, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), flags);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(c.getResources().getIdentifier("ic_tile", "drawable", c.getPackageName()))
                .setContentTitle(stopped ? "Red Filter stopped" : on ? "Red Filter is on" : "Red Filter is off")
                .setContentText(stopped ? "The phone closed it. Tap to switch it back on."
                        : "Darkness " + Prefs.dim(c) + "%, colour " + Prefs.tint(c) + "%. Tap to adjust.")
                .setContentIntent(stopped ? fix : open)
                .setOngoing(!stopped)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(stopped
                        ? new Notification.Action.Builder(null, "Switch back on", fix).build()
                        : new Notification.Action.Builder(null, on ? "Turn off" : "Turn on", toggle).build())
                .addAction(new Notification.Action.Builder(null, "Adjust", open).build())
                .build();
        try {
            nm.notify(ID, n);
        } catch (RuntimeException ignored) { }
    }
}
