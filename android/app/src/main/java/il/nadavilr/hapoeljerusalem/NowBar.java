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
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * The live score card as an Android Live Update, when Live Updates are allowed for the app: shown in the Now Bar /
 * status bar chip and on top of the notification shade. A Live Update can't have a custom layout (no RemoteViews), so:
 *
 * Android 17+ – MetricStyle, a row of three columns like the app's card (home team on the right):
 *
 *     competition (title)                                         [both crests – the large icon]
 *     away name       quarter          home name                  ← labels
 *     72 +2           04:40            77                         ← values; +N next to the score that changed
 *
 *   The order follows the phone's layout direction, so the home team is on the right also on a phone set to English
 *   (there the row is laid out left to right); every label is aligned to the same side as its value.
 *
 * Android 16 – the standard template: the score as the title, the competition / quarter / play below, both crests as
 * the large icon. (Its progress template has an image at each end of a row, but always draws a bar between them.)
 *
 * Status bar chip: the score. Android 15 and older, Live Updates turned off for the app (Settings › Notifications), or
 * any failure: the app's own card (LiveGame.card). The app compiles against an older SDK, so the newer APIs are
 * reached by reflection.
 */
final class NowBar {

    private static final String TAG = "HpjNowBar";
    private static final String RLM = "‏", LRM = "‎", LRI = "⁦", RLI = "⁧", PDI = "⁩";
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
            String title = g.comp.isEmpty() ? LiveGame.TEAM : g.comp;
            String st = fin ? RLM + "סיום" + RLM : g.statusLine().isEmpty() ? RLM + "לייב" + RLM : g.statusLine();
            Notification.Builder b = new Notification.Builder(c, LiveScoreService.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_hpj)
                .setColor(RED)
                .setContentIntent(LiveGame.open(c, g.t))
                .setOngoing(!fin)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setWhen(g.t > 0 ? g.t : System.currentTimeMillis())
                .setVisibility(Notification.VISIBILITY_PUBLIC);
            Bitmap pair = pair(homeCrest, awayCrest);
            if (pair != null) b.setLargeIcon(Icon.createWithBitmap(pair));

            Notification.Style metrics = Build.VERSION.SDK_INT >= 37 ? metrics(c, g, fin) : null;
            if (metrics != null) {
                b.setStyle(metrics)
                    .setContentTitle(RLM + (fin ? "סיום · " : "") + title + RLM)
                    .setContentText(fin || g.play.isEmpty() ? g.scoreWithNames() : RLM + g.play + RLM);
            } else {
                // Android 16: the score as the title (home on the right; +N next to the score that changed)
                String ph = fin || g.popHome < 1 || g.popHome > 3 ? "" : " " + LRI + "+" + g.popHome + PDI;
                String pa = fin || g.popAway < 1 || g.popAway > 3 ? "" : LRI + "+" + g.popAway + PDI + " ";
                String text = st + (fin || g.play.isEmpty() ? "" : "\n" + RLM + g.play + RLM);
                b.setContentTitle(RLM + g.hs + ph + " – " + pa + g.as + RLM)
                    .setContentText(st)
                    .setSubText(RLM + title + RLM)
                    .setStyle(new Notification.BigTextStyle().bigText(text));
            }
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
     * Android 17's MetricStyle: home score | clock (quarter) | away score, with the team names / quarter as labels.
     * Null if not available (then the standard template is used).
     */
    private static Notification.Style metrics(Context c, LiveGame g, boolean fin) {
        try {
            Class<?> styleC = Class.forName("android.app.Notification$MetricStyle");
            Class<?> metricC = Class.forName("android.app.Notification$Metric");
            Class<?> valueC = Class.forName("android.app.Notification$Metric$MetricValue");
            Constructor<?> metric = metricC.getConstructor(valueC, CharSequence.class);
            Constructor<?> fixedInt = Class.forName("android.app.Notification$Metric$FixedInt").getConstructor(int.class, CharSequence.class);
            Constructor<?> fixedText = Class.forName("android.app.Notification$Metric$FixedText").getConstructor(CharSequence.class);
            Object style = styleC.getConstructor().newInstance();
            Method add = styleC.getMethod("addMetric", metricC);

            // The row is laid out in the phone's direction: right to left in Hebrew (the first column on the right),
            // left to right in English. The home team must be on the right either way, and each label must sit on
            // the same side as its value (the value is placed at the column's start).
            boolean rtl = c.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            String st = fin ? "סיום" : g.status, mid = fin || g.clock.isEmpty() ? (st.isEmpty() ? "לייב" : st) : g.clock;
            String midLabel = !fin && !g.clock.isEmpty() && !st.isEmpty() ? st : "מצב המשחק";
            Object home = metric.newInstance(fixedInt.newInstance(g.hs, pop(fin ? 0 : g.popHome)), label(g.homeName, rtl));
            Object clock = metric.newInstance(fixedText.newInstance(mid), label(midLabel, rtl));
            Object away = metric.newInstance(fixedInt.newInstance(g.as, pop(fin ? 0 : g.popAway)), label(g.awayName, rtl));
            add.invoke(style, rtl ? home : away);
            add.invoke(style, clock);
            add.invoke(style, rtl ? away : home);
            styleC.getMethod("setCriticalMetric", int.class).invoke(style, -1); // the chip shows the score
            return (Notification.Style) style;
        } catch (Throwable e) {
            Log.w(TAG, "MetricStyle not available", e);
            return null;
        }
    }

    /**
     * A label of up to 10 characters ("הפועל ירושלים" → "ירושלים", "דרטונה טורטונה" → "דרטונה"). On a left-to-right
     * phone it starts with a left-to-right mark, so it is aligned to the left like the number under it, and the Hebrew
     * itself is isolated so it still reads correctly ("רבע 4", not "4 רבע").
     */
    static String label(String n, boolean rtl) {
        n = n == null ? "" : n.trim();
        if (n.length() > 10) {
            String s = n.replaceFirst("^(הפועל|מכבי|בני|עירוני|אליצור|הכח)\\s+", "");
            if (s.length() > 10) {
                StringBuilder b = new StringBuilder();
                for (String w : s.split("\\s+")) {
                    if (b.length() + (b.length() > 0 ? 1 : 0) + w.length() > 10) break;
                    if (b.length() > 0) b.append(' ');
                    b.append(w);
                }
                s = b.length() > 0 ? b.toString() : s.substring(0, 10);
            }
            n = s;
        }
        return rtl ? RLM + n : LRM + RLI + n + PDI;
    }

    private static CharSequence pop(int pts) {
        return pts >= 1 && pts <= 3 ? "+" + pts : null;
    }

    /**
     * Both crests in one square image (the notification's large icon): the home crest at the top right and the away
     * crest at the bottom left, overlapping a little – home on the right, as in the app's card.
     */
    static Bitmap pair(Bitmap home, Bitmap away) {
        if (home == null && away == null) return null;
        final int size = 192, crest = 108, off = size - crest;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (home == null || away == null) {
            cv.drawBitmap(home != null ? home : away, null, new Rect(16, 16, size - 16, size - 16), p);
            return out;
        }
        cv.drawBitmap(away, null, new Rect(0, off, crest, size), p);
        cv.drawBitmap(home, null, new Rect(off, 0, size, crest), p);
        return out;
    }
}
