package il.nadavilr.hapoeljerusalem;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * The live score card as an Android Live Update (Android 16+, when Live Updates are allowed for the app): shown in the
 * Now Bar / status bar chip and on top of the notification shade. A Live Update can't have a custom layout (no
 * RemoteViews), so it uses Android's progress template, arranged like the app's own card:
 *
 *   88 +3 – 85                                         ← title: the score, home team on the right (+N like the app)
 *   ליגת ווינר, מחזור 5                                  ← the competition and stage / round
 *   רבע 3 • 05:12                                        ← quarter and clock
 *   the latest line of the play-by-play
 *   [home crest] ████ red ████ ▌██ grey ██ [away crest]   ← the crests at the two ends (home on the right); the bar
 *                                                           between them is split by the points (Hapoel red)
 *   status bar chip: the score
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
    private static final int RED = Color.parseColor("#D21919"), GREY = Color.parseColor("#9A9A9A");

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
            Class<?> styleC = Class.forName("android.app.Notification$ProgressStyle");
            Class<?> segC = Class.forName("android.app.Notification$ProgressStyle$Segment");
            Object style = styleC.getConstructor().newInstance();
            Method addSeg = styleC.getMethod("addProgressSegment", segC);
            Method color = segC.getMethod("setColor", int.class);
            // The bar between the crests, split by the points: home first (on the right in Hebrew), Hapoel in red
            boolean jerHome = g.homeName.contains(LiveGame.TEAM) || (g.meHome && !g.awayName.contains(LiveGame.TEAM));
            int total = g.hs + g.as;
            if (total <= 0) {
                addSeg.invoke(style, color.invoke(segC.getConstructor(int.class).newInstance(1), GREY));
            } else {
                if (g.hs > 0) addSeg.invoke(style, color.invoke(segC.getConstructor(int.class).newInstance(g.hs), jerHome ? RED : GREY));
                if (g.as > 0) addSeg.invoke(style, color.invoke(segC.getConstructor(int.class).newInstance(g.as), jerHome ? GREY : RED));
            }
            styleC.getMethod("setStyledByProgress", boolean.class).invoke(style, false);
            styleC.getMethod("setProgress", int.class).invoke(style, Math.max(1, total));
            if (homeCrest != null) styleC.getMethod("setProgressStartIcon", Icon.class).invoke(style, Icon.createWithBitmap(homeCrest));
            if (awayCrest != null) styleC.getMethod("setProgressEndIcon", Icon.class).invoke(style, Icon.createWithBitmap(awayCrest));

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
                .setContentText(text)
                .setStyle((Notification.Style) style)
                .setContentIntent(LiveGame.open(c, g.t))
                .setOngoing(!fin)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setWhen(g.t > 0 ? g.t : System.currentTimeMillis())
                .setVisibility(Notification.VISIBILITY_PUBLIC);
            if (!g.comp.isEmpty()) b.setSubText(RLM + g.comp + RLM);
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
}
