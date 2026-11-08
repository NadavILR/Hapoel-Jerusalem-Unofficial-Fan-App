package il.nadavilr.hapoeljerusalem;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.core.app.NotificationCompat;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/**
 * One live game, shown in two notifications while Hapoel plays:
 *
 * - The score card (custom layout, like Google's sports card, in the app's colours): the two crests with the scores
 *   and a quarter • clock pill between them; expanded, it adds the competition / stage / round and the arena.
 * - The Live Update (Android 16+: status-bar chip, lock screen, Samsung Now Bar): the score, quarter • clock and
 *   one image with both crests. A custom layout can't be a Live Update, hence two notifications.
 *
 * Everything reads right to left (home team on the right) even on a phone set to English.
 */
final class LiveGame {

    static final int HAPOEL = 634;
    private static final String RLM = "‏";
    private static final int RED = 0xFFD21919, GREY = 0x33888888;
    private static final String IMG = "https://imagecache.365scores.com/image/upload/f_png,w_96,h_96,c_limit,q_auto:eco,dpr_2,"
        + "d_Competitors:default1.png/v%d/Competitors/%d";

    String comp = "", homeName = "", awayName = "", venue = "", status = "", clock = "", homeImg, awayImg;
    int hs, as;
    boolean meHome = true;

    /** From a 365scores game (games/current). */
    static LiveGame from365(JSONObject g) {
        JSONObject h = g.optJSONObject("homeCompetitor"), a = g.optJSONObject("awayCompetitor");
        if (h == null || a == null) return null;
        LiveGame x = new LiveGame();
        x.meHome = h.optInt("id") == HAPOEL;
        x.homeName = name(h);
        x.awayName = name(a);
        JSONObject v = g.optJSONObject("venue");
        if (v != null) x.venue = v.optString("name", "").trim();
        x.hs = (int) Math.max(0, Math.round(h.optDouble("score", 0)));
        x.as = (int) Math.max(0, Math.round(a.optDouble("score", 0)));
        x.homeImg = img(h);
        x.awayImg = img(a);
        x.status = g.optString("statusText", g.optString("shortStatusText", "")).trim();
        x.clock = g.optString("gameTimeDisplay", "").trim();
        // 365scores puts the stage in the competition name ("ליגת ווינר - חצי גמר", "יורוקאפ - בית א'");
        // the round is "מחזור N", or "משחק N" in a playoff series; a one-game knockout has no round number.
        String comp = g.optString("competitionDisplayName", "").trim().replace(" - ", " – ");
        int round = g.optInt("roundNum", 0);
        String roundName = g.optString("roundName", "").trim();
        if (roundName.isEmpty() || "null".equals(roundName)) roundName = "מחזור";
        x.comp = comp + (round > 0 ? (comp.isEmpty() ? "" : " · ") + roundName + " " + round : "");
        return x;
    }

    private static String name(JSONObject c) {
        String n = c.optString("name", "");
        return n.isEmpty() ? c.optString("shortName", "") : n;
    }

    private static String img(JSONObject c) {
        int id = c.optInt("id", 0);
        return id > 0 ? String.format(Locale.US, IMG, Math.max(1, c.optInt("imageVersion", 1)), id) : null;
    }

    /** Score with the home team on the right: "78 – 72" in a right-to-left line. */
    String score() {
        return RLM + hs + " – " + as + RLM;
    }

    /** Names and score, home on the right (for phones without the crests). */
    String scoreWithNames() {
        return RLM + homeName + "  " + hs + " – " + as + "  " + awayName + RLM;
    }

    String statusLine() {
        String s = status + (clock.isEmpty() ? "" : (status.isEmpty() ? "" : " • ") + clock);
        return s.isEmpty() ? "" : RLM + s + RLM;
    }

    String key() {
        return comp + "|" + hs + "|" + as + "|" + status + "|" + clock;
    }

    // ---------- crests ----------

    private static final Map<String, Bitmap> CRESTS = new HashMap<>();

    /** Downloads a crest (365scores image server only), cached. Call off the main thread. */
    static Bitmap crest(String url) {
        if (url == null || !url.startsWith("https://imagecache.365scores.com/")) return null;
        synchronized (CRESTS) {
            if (CRESTS.containsKey(url)) return CRESTS.get(url);
        }
        Bitmap b = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(10_000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) HapoelJerusalemFanApp");
            try (InputStream in = c.getInputStream()) {
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw != null) b = square(raw, 120);
            } finally {
                c.disconnect();
            }
        } catch (Exception ignored) {
        }
        if (b != null) synchronized (CRESTS) {
            CRESTS.put(url, b);
        }
        return b;
    }

    /** Both crests in one square image: the home team top right, the away team bottom left. */
    static Bitmap matchup(Bitmap home, Bitmap away) {
        int size = 160, cs = 94;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        cv.drawBitmap(Bitmap.createScaledBitmap(away, cs, cs, true), 3, size - cs - 3, p);
        cv.drawBitmap(Bitmap.createScaledBitmap(home, cs, cs, true), size - cs - 3, 3, p);
        return out;
    }

    /** The crest centred on a transparent square, so it is not stretched. */
    private static Bitmap square(Bitmap src, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        float k = Math.min(size / (float) src.getWidth(), size / (float) src.getHeight());
        int w = Math.round(src.getWidth() * k), h = Math.round(src.getHeight() * k);
        Bitmap s = Bitmap.createScaledBitmap(src, Math.max(1, w), Math.max(1, h), true);
        new Canvas(out).drawBitmap(s, (size - w) / 2f, (size - h) / 2f, null);
        return out;
    }

    // ---------- the notification ----------

    /**
     * Builds the live notification. {@code homeCrest} / {@code awayCrest} may be null (then the names are shown).
     * {@code del} runs when the user swipes it away (may be null).
     */
    static Notification notification(Context c, LiveGame g, Bitmap homeCrest, Bitmap awayCrest, PendingIntent del) {
        return notification(c, g, homeCrest, awayCrest, del, LiveScheduler.route(c));
    }

    /**
     * {@code route} – how the Now Bar gets the score card's layout (Samsung publishes no contract for this; the keys
     * come from dumps of Google's sports card in the Now Bar and from Samsung's own apps):
     * 1 = Android 16 Live Update, plus Samsung's custom-layout extras (nowbarRemoteView / expandedRemoteView);
     * 2 = Samsung's own Now Bar path, like Google's card: the hidden Notification.OngoingActivityStyle with the score
     *     row as the card's centre and the full card as its expanded view.
     */
    static Notification notification(Context c, LiveGame g, Bitmap homeCrest, Bitmap awayCrest, PendingIntent del, int route) {
        boolean crests = homeCrest != null && awayCrest != null;
        String title = crests && Build.VERSION.SDK_INT >= 36 ? g.score() : g.scoreWithNames();
        String text = g.statusLine();
        NotificationCompat.Builder b = LiveScoreService.base(c, title, text)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (del != null) b.setDeleteIntent(del);

        // The score card's own layouts: the row (the Now Bar) and the full card (expanded)
        android.widget.RemoteViews row = new android.widget.RemoteViews(c.getPackageName(), R.layout.notif_live_small);
        android.widget.RemoteViews big = new android.widget.RemoteViews(c.getPackageName(), R.layout.notif_live_card);
        fill(row, g, homeCrest, awayCrest, false, false);
        fill(big, g, homeCrest, awayCrest, false, true);

        Bundle x = new Bundle();
        x.putInt("android.ongoingActivityNoti.chipBgColor", RED);
        x.putBoolean("android.ongoingActivityNoti.show", true);
        x.putString("android.ongoingActivityNoti.primaryInfo", g.score());
        x.putString("android.ongoingActivityNoti.secondaryInfo", text);
        x.putString("android.ongoingActivityNoti.nowbarPrimaryInfo", g.score());
        x.putString("android.ongoingActivityNoti.nowbarSecondaryInfo", text);
        x.putString("android.ongoingActivityNoti.chipExpandedText", g.score());
        x.putParcelable("android.ongoingActivityNoti.nowbarRemoteView", row);
        x.putParcelable("android.ongoingActivityNoti.expandedRemoteView", big);
        x.putCharSequence("android.substName", "הפועל ירושלים");
        boolean samsungPath = route == 2 || Build.VERSION.SDK_INT < 36;
        if (samsungPath) {
            // Samsung's private card path (One UI 7, and route 2 on One UI 8+)
            x.putInt("android.ongoingActivityNoti.style", 1);
            x.putInt("android.ongoingActivityNoti.actionType", 1);
            x.putInt("android.ongoingActivityNoti.actionPrimarySet", 0);
        }
        b.addExtras(x);
        Notification n = b.build();
        if (samsungPath) return samsungStyle(c, n, g, row, big);
        return liveUpdate(c, n, g, crests ? homeCrest : null, crests ? awayCrest : null);
    }

    /**
     * Route 2: Samsung's hidden Notification.OngoingActivityStyle (what Google's sports card uses in the Now Bar),
     * with our score row as the card's centre and the full score card as its expanded view. Every method is optional:
     * whatever this One UI build lacks is skipped, and the same layouts are also in the extras.
     */
    private static Notification samsungStyle(Context c, Notification n, LiveGame g, android.widget.RemoteViews row, android.widget.RemoteViews big) {
        try {
            Class<?> k = Class.forName("android.app.Notification$OngoingActivityStyle");
            java.lang.reflect.Constructor<?> ctor = k.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object style = ctor.newInstance();
            call(style, "setPrimaryInfo", g.score());
            call(style, "setSecondaryInfo", g.statusLine());
            call(style, "setChipBackground", RED);
            call(style, "setCustomCardViewCenterUI", row);
            call(style, "setCustomExpandedCardView", big);
            Notification.Builder fb = Notification.Builder.recoverBuilder(c, n);
            fb.setStyle((Notification.Style) style);
            try {
                Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(fb, true);
            } catch (Throwable ignored) {
            }
            Notification out = fb.build();
            // Keep all the Samsung extras next to the style's own (without overwriting the style's keys)
            for (String key : n.extras.keySet()) {
                if (!out.extras.containsKey(key)) {
                    Object v = n.extras.get(key);
                    if (v instanceof android.os.Parcelable) out.extras.putParcelable(key, (android.os.Parcelable) v);
                    else if (v instanceof CharSequence) out.extras.putCharSequence(key, (CharSequence) v);
                    else if (v instanceof Integer) out.extras.putInt(key, (Integer) v);
                    else if (v instanceof Boolean) out.extras.putBoolean(key, (Boolean) v);
                }
            }
            return out;
        } catch (Throwable e) {
            // No hidden style on this phone: the extras alone (centre / expanded card layouts by key)
            n.extras.putParcelable("android.ongoingActivityCustomCardViewCenterUI", row);
            n.extras.putParcelable("android.ongoingActivityCustomExpandedCardView", big);
            return n;
        }
    }

    /** Calls a one-argument method by name if this build has it (any compatible parameter type). */
    private static void call(Object target, String name, Object arg) {
        for (Class<?> t = target.getClass(); t != null; t = t.getSuperclass()) {
            for (java.lang.reflect.Method m : t.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                boolean ok = p.isInstance(arg) || (p == int.class && arg instanceof Integer)
                    || (p == CharSequence.class && arg instanceof CharSequence);
                if (!ok) continue;
                try {
                    m.setAccessible(true);
                    m.invoke(target, arg);
                } catch (Throwable ignored) {
                }
                return;
            }
        }
    }

    /**
     * Android 16+: Live Update (setRequestPromotedOngoing + setShortCriticalText) with both crests as its image. These APIs are newer than the SDK the app is built with, so they are called by reflection;
     * each step that is missing on a device is skipped.
     */
    private static Notification liveUpdate(Context c, Notification n, LiveGame g, Bitmap homeCrest, Bitmap awayCrest) {
        Notification.Builder fb;
        try {
            fb = Notification.Builder.recoverBuilder(c, n);
        } catch (Throwable e) {
            n.extras.putBoolean("android.requestPromotedOngoing", true);
            n.extras.putString("android.shortCriticalText", g.score());
            return n;
        }
        try {
            Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(fb, true);
        } catch (Throwable e) {
            fb.getExtras().putBoolean("android.requestPromotedOngoing", true);
        }
        boolean chip = false;
        for (Class<?> t : new Class<?>[]{String.class, CharSequence.class}) {
            try {
                Notification.Builder.class.getMethod("setShortCriticalText", t).invoke(fb, g.score());
                chip = true;
                break;
            } catch (Throwable ignored) {
            }
        }
        if (!chip) fb.getExtras().putString("android.shortCriticalText", g.score());

        if (homeCrest != null && awayCrest != null) {
            // No progress bar: the standard Live Update, like the score card – the two crests (one image, home team
            // on the right) next to the score, and quarter • clock under it
            fb.setLargeIcon(Icon.createWithBitmap(matchup(homeCrest, awayCrest)));
        } else {
            fb.setContentTitle(g.scoreWithNames());
        }
        try {
            return fb.build();
        } catch (Throwable e) {
            return n;
        }
    }

    // ---------- the score card (Google-style, the app's colours) ----------

    /** The card's notification id, next to the Live Update (LiveScoreService.NOTIF_ID). */
    static final int CARD_ID = 63402;

    private static void fill(android.widget.RemoteViews v, LiveGame g, Bitmap homeCrest, Bitmap awayCrest, boolean fin, boolean details) {
        v.setTextViewText(R.id.home_score, String.valueOf(g.hs));
        v.setTextViewText(R.id.away_score, String.valueOf(g.as));
        String st = fin ? "סיום" : g.status + (g.clock.isEmpty() ? "" : (g.status.isEmpty() ? "" : " • ") + g.clock);
        v.setTextViewText(R.id.status, RLM + (st.isEmpty() ? "לייב" : st) + RLM);
        v.setInt(R.id.status, "setBackgroundResource", fin ? R.drawable.notif_pill_final : R.drawable.notif_pill);
        if (homeCrest != null) v.setImageViewBitmap(R.id.home_crest, homeCrest);
        else v.setViewVisibility(R.id.home_crest, View.GONE);
        if (awayCrest != null) v.setImageViewBitmap(R.id.away_crest, awayCrest);
        else v.setViewVisibility(R.id.away_crest, View.GONE);
        v.setContentDescription(R.id.home_crest, g.homeName);
        v.setContentDescription(R.id.away_crest, g.awayName);
        if (details) {
            v.setTextViewText(R.id.comp, g.comp.isEmpty() ? "" : RLM + g.comp + RLM);
            v.setViewVisibility(R.id.comp, g.comp.isEmpty() ? View.GONE : View.VISIBLE);
            v.setTextViewText(R.id.venue, g.venue.isEmpty() ? "" : RLM + g.venue + RLM);
            v.setViewVisibility(R.id.venue, g.venue.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * The score card in the notification shade, laid out like Google's sports card: crests and team names on the sides,
     * the scores, a pill with quarter • clock between them, and the competition and arena below. Home team on the
     * right. A custom layout can't be a Live Update, so the Live Update (Now Bar, status-bar chip) is a separate notification.
     */
    static Notification card(Context c, LiveGame g, Bitmap homeCrest, Bitmap awayCrest, boolean fin, PendingIntent del) {
        android.widget.RemoteViews big = new android.widget.RemoteViews(c.getPackageName(), R.layout.notif_live_card);
        android.widget.RemoteViews small = new android.widget.RemoteViews(c.getPackageName(), R.layout.notif_live_small);
        fill(big, g, homeCrest, awayCrest, fin, true);
        fill(small, g, homeCrest, awayCrest, fin, false);
        NotificationCompat.Builder b = LiveScoreService.base(c, (fin ? RLM + "סיום: " : "") + g.scoreWithNames(), g.statusLine())
            .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setOngoing(!fin);
        if (fin) b.setAutoCancel(true).setTimeoutAfter(30 * 60_000L);
        if (del != null) b.setDeleteIntent(del);
        return b.build();
    }

    /** "Final" notification (not ongoing), home on the right. */
    static Notification finalNotification(Context c, LiveGame g) {
        NotificationCompat.Builder b = LiveScoreService.base(c, RLM + "סיום: " + g.scoreWithNames(), RLM + "התוצאה הסופית" + RLM)
            .setOngoing(false)
            .setAutoCancel(true)
            .setTimeoutAfter(30 * 60_000L);
        if (!g.comp.isEmpty()) b.setSubText(RLM + g.comp + RLM);
        return b.build();
    }
}
