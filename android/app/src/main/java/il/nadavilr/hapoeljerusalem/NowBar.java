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
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * The live score card as an Android Live Update, when Live Updates are allowed for the app: shown in the Now Bar /
 * status bar chip and on top of the notification shade. A Live Update can't have a custom layout (no RemoteViews), so:
 *
 * Android 17+ – MetricStyle, a row of three columns like the app's card (home team on the right):
 *
 *                          competition, round / stage (title, centred)            [both crests – the large icon]
 *     away team (full name)        quarter            home team (full name)       ← labels
 *              72 ⁺²                04:40                     77                  ← values; +N next to a new score
 *
 *   The order follows the phone's layout direction, so the home team is on the right also on a phone set to English
 *   (there the row is laid out left to right). The template puts a column's label and value at the column's start
 *   and has no centring, so the narrower of the two is padded with thin spaces (measured on the phone) to sit centred
 *   over / under the other.
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
            if (pair != null) {
                Icon crests = Icon.createWithBitmap(pair);
                b.setLargeIcon(crests);
                // Samsung's Now Bar: the card's icon (in place of the app icon), a key seen in dumps of Samsung's own
                // Now Bar cards – not a published contract, ignored elsewhere
                Bundle ci = new Bundle();
                ci.putParcelable("android.ongoingActivityCardIcon", crests);
                b.addExtras(ci);
            }

            Notification.Style metrics = Build.VERSION.SDK_INT >= 37 ? metrics(c, g, fin) : null;
            if (metrics != null) {
                // The competition and round / stage in the middle of the card (a Live Update keeps text styling)
                b.setStyle(metrics)
                    .setContentTitle(centred(RLM + (fin ? "סיום · " : "") + title + RLM))
                    .setContentText(centred(fin || g.play.isEmpty() ? g.scoreWithNames() : RLM + g.play + RLM));
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
            Constructor<?> fixedText = Class.forName("android.app.Notification$Metric$FixedText").getConstructor(CharSequence.class, CharSequence.class);
            Object style = styleC.getConstructor().newInstance();
            Method add = styleC.getMethod("addMetric", metricC);

            // The row is laid out in the phone's direction: right to left in Hebrew (the first column on the right),
            // left to right in English. The home team must be on the right either way.
            boolean rtl = c.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            String st = fin ? "סיום" : g.status, mid = fin || g.clock.isEmpty() ? (st.isEmpty() ? "לייב" : st) : g.clock;
            String midLabel = !fin && !g.clock.isEmpty() && !st.isEmpty() ? st : "מצב המשחק";
            Col cols = new Col(c);
            // +1 / +2 / +3 next to the score, on its side facing the middle (as in the app): left of the home score
            // (on the right), right of the away score (on the left). In a left-to-right row "left" is the start.
            String[] h = cols.pair(g.homeName, String.valueOf(g.hs), badge(fin ? 0 : g.popHome), !rtl);
            String[] m = cols.pair(midLabel, mid, "", false);
            String[] a = cols.pair(g.awayName, String.valueOf(g.as), badge(fin ? 0 : g.popAway), rtl);
            // Team names, the quarter and the clock in bold
            Object home = metric.newInstance(fixedText.newInstance(value(h[2], rtl, false), null), label(h, rtl));
            Object clock = metric.newInstance(fixedText.newInstance(value(m[2], rtl, true), null), label(m, rtl));
            Object away = metric.newInstance(fixedText.newInstance(value(a[2], rtl, false), null), label(a, rtl));
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
     * A label in the phone's direction. On a left-to-right phone it starts with a left-to-right mark, so it starts on
     * the left like the value under it, and the Hebrew itself is isolated so it still reads correctly ("רבע 4", not
     * "4 רבע"). {@code s} is [padding, text]. Bold.
     */
    static CharSequence label(String[] s, boolean rtl) {
        return bold(rtl ? RLM + s[0] + s[1] : LRM + s[0] + RLI + s[1] + PDI);
    }

    /** A value in the phone's direction (its padding first, at the column's start); the clock in bold. */
    static CharSequence value(String v, boolean rtl, boolean bold) {
        String t = (rtl ? RLM : "") + v;
        return bold ? bold(t) : t;
    }

    static CharSequence bold(String t) {
        SpannableString s = new SpannableString(t);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return s;
    }

    /** "⁺²" – the points just scored, small and raised like a badge; "" for none. */
    static String badge(int pts) {
        return pts == 1 ? "\u207A\u00B9" : pts == 2 ? "\u207A\u00B2" : pts == 3 ? "\u207A\u00B3" : "";
    }

    /** A whole text centred in its line (the title and the text of the card). */
    static CharSequence centred(String t) {
        SpannableString s = new SpannableString(t);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, s.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        return s;
    }

    /**
     * Centres a column's label (14sp) and value (28sp) over each other: both start at the column's start, so the
     * narrower one gets thin spaces in front of it, half the difference in width. Widths are measured with the
     * phone's own font at the template's text sizes; a label wider than the column is cut by the system, so its width
     * is capped at an estimate of the column's width.
     */
    private static final class Col {
        final Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG), vp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float colW;

        Col(Context c) {
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            lp.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, dm));
            lp.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            vp.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28, dm));
            vp.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            // The card's width less the template's margins (about 104dp), in three columns with 4dp between them
            colW = Math.max(60 * dm.density, (Math.min(dm.widthPixels, dm.heightPixels) - 104 * dm.density) / 3f - 4 * dm.density);
        }

        /**
         * [label padding, label, value padding + value]. {@code badge} (may be "") goes next to the value – before it
         * (at the column's start) or after it – without moving the value off the centre: a badge before it takes its
         * width out of the padding.
         */
        String[] pair(String label, String value, String badge, boolean badgeFirst) {
            float wl = Math.min(lp.measureText(label), colW), wv = Math.min(vp.measureText(value), colW);
            String body = value;
            float wb = 0;
            if (!badge.isEmpty()) {
                String iso = LRI + badge + PDI; // keeps "⁺²" as it is inside a right-to-left line
                body = badgeFirst ? iso + "\u2009" + value : value + "\u2009" + iso;
                if (badgeFirst) wb = vp.measureText(badge + "\u2009");
            }
            String pl = wv > wl ? pad((wv - wl) / 2f, lp) : "";
            String pv = wl > wv ? pad(Math.max(0f, (wl - wv) / 2f - wb), vp) : "";
            return new String[] {pl, label, pv + body};
        }

        /** Spaces (then hair spaces for the rest) about {@code w} pixels wide. */
        private static String pad(float w, Paint p) {
            StringBuilder b = new StringBuilder();
            while (b.length() < 40 && p.measureText(b + " ") <= w) b.append(' ');
            while (b.length() < 80 && p.measureText(b + "\u200A") <= w) b.append('\u200A');
            return b.toString();
        }
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
