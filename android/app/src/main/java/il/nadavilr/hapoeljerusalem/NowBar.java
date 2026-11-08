package il.nadavilr.hapoeljerusalem;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * The live score card as an Android Live Update (Android 16+, when Live Updates are allowed for the app): shown in the
 * Now Bar / status bar chip and on top of the notification shade. A Live Update can't have a custom layout (no
 * RemoteViews), so it uses Android's standard template (BigTextStyle), arranged like the app's own card:
 *
 *   88 +3 – 85                       [crests]   ← title: the score, home team on the right (+N like the app); the
 *   ליגת ווינר, מחזור 5                           large icon: both crests, home top right and away bottom left
 *   רבע 3 • 05:12                               ← the competition and stage / round, the quarter and clock
 *   the latest line of the play-by-play
 *   status bar chip: the score
 *
 * Why not the other templates: ProgressStyle has an image at each end of its row, but always draws the bar between them
 * (Android forces its colours to contrast with the background); MetricStyle shows no images at all.
 *
 * Android 15 and older, Live Updates turned off for the app (Settings › Notifications), or any failure: the app's own
 * card (LiveGame.card), as before. The app compiles against an older SDK, so the newer classes are reached by
 * reflection.
 */
final class NowBar {

    private static final String TAG = "HpjNowBar";
    private static final String RLM = "\u200F", LRI = "\u2066", PDI = "\u2069";
    /** Notification.FLAG_PROMOTED_ONGOING: set by Android when it actually shows the notification as a Live Update. */
    static final int FLAG_PROMOTED = 262144;
    private static final int RED = Color.parseColor("#D21919");

    private NowBar() {
    }

    /** Android 16+ and Live Updates allowed for the app. */
    static boolean available(Context c) {
        if (Build.VERSION.SDK_INT < 36) return false;
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            Object ok = NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(nm);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Whether the posted live card is now shown as a Live Update (in the Now Bar). */
    static boolean promoted(Context c, int id) {
        try {
            for (StatusBarNotification s : c.getSystemService(NotificationManager.class).getActiveNotifications()) {
                if (s.getId() == id) return (s.getNotification().flags & FLAG_PROMOTED) != 0;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** The live card as a Live Update, or null (then the app's own card is used). */
    static Notification card(Context c, LiveGame g, Bitmap homeCrest, Bitmap awayCrest, boolean fin, PendingIntent del) {
        if (Build.VERSION.SDK_INT < 36 || !available(c)) return null;
        try {
            // Title: the score, home on the right; +N next to the score that changed (on its side facing the middle)
            String ph = fin || g.popHome < 1 || g.popHome > 3 ? "" : " " + LRI + "+" + g.popHome + PDI;
            String pa = fin || g.popAway < 1 || g.popAway > 3 ? "" : LRI + "+" + g.popAway + PDI + " ";
            String score = RLM + g.hs + ph + " – " + pa + g.as + RLM;
            String st = fin ? RLM + "סיום" + RLM : g.statusLine().isEmpty() ? RLM + "לייב" + RLM : g.statusLine();
            String text = st + (fin || g.play.isEmpty() ? "" : "\n" + RLM + g.play + RLM);

            Notification.Builder b = new Notification.Builder(c, LiveScoreService.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_hpj)
                .setColor(RED)
                .setContentTitle(score)
                .setContentText(st)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(LiveGame.open(c, g.t))
                .setOngoing(!fin)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setWhen(g.t > 0 ? g.t : System.currentTimeMillis())
                .setVisibility(Notification.VISIBILITY_PUBLIC);
            if (!g.comp.isEmpty()) b.setSubText(RLM + g.comp + RLM);
            Bitmap pair = pair(homeCrest, awayCrest);
            if (pair != null) b.setLargeIcon(Icon.createWithBitmap(pair));
            if (fin) b.setAutoCancel(true).setTimeoutAfter(30 * 60_000L);
            else b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
            if (del != null) b.setDeleteIntent(del);
            if (!fin) {
                // Ask Android to show it as a Live Update (Now Bar); the status bar chip shows the score, home on the right
                Bundle x = new Bundle();
                x.putBoolean("android.requestPromotedOngoing", true); // Notification.EXTRA_REQUEST_PROMOTED_ONGOING
                b.addExtras(x);
                try {
                    Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true);
                } catch (Throwable ignored) {
                }
                try {
                    Notification.Builder.class.getMethod("setShortCriticalText", String.class).invoke(b, g.as + "-" + g.hs);
                } catch (Throwable ignored) {
                }
            }
            return b.build();
        } catch (Throwable e) {
            Log.w(TAG, "Live Update not available, using the app's card", e);
            return null;
        }
    }

    /**
     * Both crests in one square image (the notification's large icon, 48dp): the home crest at the top right and the
     * away crest at the bottom left, overlapping a little – home on the right, as in the app's card.
     */
    static Bitmap pair(Bitmap home, Bitmap away) {
        if (home == null && away == null) return null;
        final int size = 192, crest = 108, off = size - crest;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (home == null || away == null) {
            Bitmap one = home != null ? home : away;
            cv.drawBitmap(one, null, new Rect(16, 16, size - 16, size - 16), p);
            return out;
        }
        cv.drawBitmap(away, null, new Rect(0, off, crest, size), p);
        cv.drawBitmap(home, null, new Rect(off, 0, size, crest), p);
        return out;
    }
}
