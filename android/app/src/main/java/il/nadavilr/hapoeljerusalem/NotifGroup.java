package il.nadavilr.hapoeljerusalem;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.graphics.Color;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * All of the app's notifications are one group in the notification shade (the live score card, the in-game
 * notifications and the app's own reminders / updates, which use the same group key). The live score card is on top
 * of the group (sort key "0", the others "1"). Android shows a group only with a summary notification, so this keeps
 * a silent summary while the group has notifications and removes it when it has none.
 */
final class NotifGroup {

    static final String KEY = "hpj-all";
    static final int SUMMARY_ID = 63400;

    private NotifGroup() {
    }

    /** Posts or removes the group's summary, by how many of the app's notifications are now in the group. */
    static synchronized void refresh(Context c) {
        if (Build.VERSION.SDK_INT < 24) return; // no notification groups in the shade before Android 7
        int children = 0;
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            for (StatusBarNotification sbn : nm.getActiveNotifications()) {
                if (sbn.getId() == SUMMARY_ID) continue;
                if (KEY.equals(sbn.getNotification().getGroup())) children++;
            }
        } catch (Exception e) {
            return;
        }
        NotificationManagerCompat nmc = NotificationManagerCompat.from(c);
        if (children == 0) {
            nmc.cancel(SUMMARY_ID);
            return;
        }
        LiveScoreService.ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, LiveScoreService.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hpj)
            .setColor(Color.parseColor("#D21919"))
            .setContentTitle(LiveGame.TEAM)
            .setContentIntent(pi)
            .setGroup(KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN) // the summary itself never makes a sound
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSortKey("0")
            .setAutoCancel(true);
        try {
            nmc.notify(SUMMARY_ID, b.build());
        } catch (SecurityException ignored) {
        }
    }
}
