package il.nadavilr.hapoeljerusalem;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * The live score card as an Android Live Update (Android 17+): shown in the Now Bar / status bar chip and on top of
 * the notification shade. A Live Update can't have a custom layout (no RemoteViews), so it uses Android 17's
 * MetricStyle, laid out like the app's own card:
 *
 *   home score (home name)  |  clock (quarter)  |  away score (away name)      +2 / +3 next to the score that changed
 *   title: competition – stage / round      text: the latest line of the play-by-play
 *   status bar chip: the score
 *
 * When Live Updates are off for the app (Settings › Notifications › Live updates) or on Android 16 and older, the
 * app's own card (LiveGame.card) is used, as before. The app compiles against an older SDK, so the Android 17 classes
 * are reached by reflection; any failure falls back to the app's card.
 */
final class NowBar {

    private static final String TAG = "HpjNowBar";
    private static final String RLM = "‏";
    /** Notification.FLAG_PROMOTED_ONGOING: set by Android when it actually shows the notification as a Live Update. */
    static final int FLAG_PROMOTED = 262144;

    private NowBar() {
    }

    /** Android 17+ and Live Updates allowed for the app. */
    static boolean available(Context c) {
        if (Build.VERSION.SDK_INT < 37) return false;
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

    /** Up to 10 characters (the label limit): "הפועל ירושלים" → "ירושלים", "דרטונה טורטונה" → "דרטונה". */
    static String label(String n) {
        n = n == null ? "" : n.trim();
        if (n.length() <= 10) return n;
        String s = n.replaceFirst("^(הפועל|מכבי|בני|עירוני|אליצור|הכח)\\s+", "");
        if (s.length() <= 10) return s;
        StringBuilder b = new StringBuilder();
        for (String w : s.split("\\s+")) {
            if (b.length() + (b.length() > 0 ? 1 : 0) + w.length() > 10) break;
            if (b.length() > 0) b.append(' ');
            b.append(w);
        }
        return b.length() > 0 ? b.toString() : s.substring(0, 10);
    }

    /** The live card as a Live Update, or null (then the app's own card is used). */
    static Notification card(Context c, LiveGame g, boolean fin, PendingIntent del) {
        if (Build.VERSION.SDK_INT < 37 || !available(c)) return null;
        try {
            Class<?> styleC = Class.forName("android.app.Notification$MetricStyle");
            Class<?> metricC = Class.forName("android.app.Notification$Metric");
            Class<?> valueC = Class.forName("android.app.Notification$Metric$MetricValue");
            Constructor<?> metric = metricC.getConstructor(valueC, CharSequence.class);
            Constructor<?> fixedInt = Class.forName("android.app.Notification$Metric$FixedInt").getConstructor(int.class, CharSequence.class);
            Constructor<?> fixedText = Class.forName("android.app.Notification$Metric$FixedText").getConstructor(CharSequence.class);
            Object style = styleC.getConstructor().newInstance();
            Method add = styleC.getMethod("addMetric", metricC);

            // Middle: the clock, with the quarter as its label ("05:12" / "רבע 3"); at a break – the status itself
            String st = fin ? "סיום" : g.status, mid = fin || g.clock.isEmpty() ? (st.isEmpty() ? "לייב" : st) : g.clock;
            String midLabel = !fin && !g.clock.isEmpty() && !st.isEmpty() ? st : "מצב המשחק";
            // Home on the right: in Hebrew the metrics row reads right to left, so the home team is the first metric
            add.invoke(style, metric.newInstance(fixedInt.newInstance(g.hs, pop(fin ? 0 : g.popHome)), label(g.homeName)));
            add.invoke(style, metric.newInstance(fixedText.newInstance(mid), label(midLabel)));
            add.invoke(style, metric.newInstance(fixedInt.newInstance(g.as, pop(fin ? 0 : g.popAway)), label(g.awayName)));
            styleC.getMethod("setCriticalMetric", int.class).invoke(style, -1); // the chip shows the score (below)

            String title = g.comp.isEmpty() ? LiveGame.TEAM : g.comp;
            Notification.Builder b = new Notification.Builder(c, LiveScoreService.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_hpj)
                .setColor(Color.parseColor("#D21919"))
                .setContentTitle(RLM + (fin ? "סיום · " : "") + title + RLM)
                .setContentText(fin || g.play.isEmpty() ? g.scoreWithNames() : RLM + g.play + RLM)
                .setStyle((Notification.Style) style)
                .setContentIntent(LiveGame.open(c, g.t))
                .setOngoing(!fin)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setWhen(g.t > 0 ? g.t : System.currentTimeMillis())
                .setVisibility(Notification.VISIBILITY_PUBLIC);
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

    private static CharSequence pop(int pts) {
        return pts >= 1 && pts <= 3 ? "+" + pts : null;
    }
}
