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
import android.text.TextUtils;
import android.view.View;
import androidx.core.app.NotificationCompat;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * One live game as the notification shows it, laid out like the app's design:
 *
 *   competition – round
 *   [home crest]   home score – away score   [away crest]
 *   quarter • clock
 *
 * Everything reads right to left (home team on the right) even on a phone set to English.
 * On Android 16+ it is a Live Update (status-bar chip, lock screen, Samsung Now Bar) in Android's
 * ProgressStyle: the two crests sit at the two ends and the bar between them is the game clock (4 quarters).
 */
final class LiveGame {

    static final int HAPOEL = 634;
    private static final String RLM = "‏";
    private static final int RED = 0xFFD21919, GREY = 0x33888888;
    private static final String IMG = "https://imagecache.365scores.com/image/upload/f_png,w_96,h_96,c_limit,q_auto:eco,dpr_2,"
        + "d_Competitors:default1.png/v%d/Competitors/%d";

    String comp = "", homeName = "", awayName = "", status = "", clock = "", homeImg, awayImg;
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
        x.hs = (int) Math.max(0, Math.round(h.optDouble("score", 0)));
        x.as = (int) Math.max(0, Math.round(a.optDouble("score", 0)));
        x.homeImg = img(h);
        x.awayImg = img(a);
        x.status = g.optString("statusText", g.optString("shortStatusText", "")).trim();
        x.clock = g.optString("gameTimeDisplay", "").trim();
        String comp = g.optString("competitionDisplayName", "").trim();
        String stage = g.optString("stageName", "").trim();
        if ("null".equals(stage)) stage = "";
        int round = g.optInt("roundNum", 0);
        String roundName = g.optString("roundName", "").trim();
        String sub = !stage.isEmpty() ? stage : round > 0 ? (roundName.isEmpty() || "null".equals(roundName) ? "מחזור" : roundName) + " " + round : "";
        x.comp = comp + (sub.isEmpty() ? "" : " – " + sub);
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

    /** Seconds played out of 4 x 10 minutes, from the quarter and the clock (counting down). */
    int elapsed() {
        String s = status;
        if (s.contains("הארכה") || s.toUpperCase(Locale.US).contains("OT")) return 2400;
        if (s.contains("מחצית") || s.toLowerCase(Locale.US).contains("half")) return 1200;
        Matcher m = Pattern.compile("(\\d)").matcher(s);
        int q = m.find() ? Integer.parseInt(m.group(1)) : 0;
        if (q < 1 || q > 4) return 0;
        int left = 600;
        Matcher c = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(clock);
        if (c.find()) left = Math.min(600, Integer.parseInt(c.group(1)) * 60 + Integer.parseInt(c.group(2)));
        return Math.max(0, Math.min(2400, (q - 1) * 600 + (600 - left)));
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
                if (raw != null) b = square(raw, 192);
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
        boolean crests = homeCrest != null && awayCrest != null && Build.VERSION.SDK_INT >= 36;
        String title = crests ? g.score() : g.scoreWithNames();
        String text = g.statusLine();
        NotificationCompat.Builder b = LiveScoreService.base(c, title, text)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (!g.comp.isEmpty()) b.setSubText(RLM + g.comp + RLM);
        if (del != null) b.setDeleteIntent(del);

        Bundle x = new Bundle();
        // Samsung (One UI): colour of the status-bar chip / Now Bar pill
        x.putInt("android.ongoingActivityNoti.chipBgColor", RED);
        if (Build.VERSION.SDK_INT < 36) {
            // One UI 7 (Android 15): Samsung's own Live Notifications card in the Now Bar
            x.putInt("android.ongoingActivityNoti.style", 1);
            x.putString("android.ongoingActivityNoti.primaryInfo", g.scoreWithNames());
            x.putString("android.ongoingActivityNoti.secondaryInfo", text);
            x.putInt("android.ongoingActivityNoti.actionType", 1);
            x.putInt("android.ongoingActivityNoti.actionPrimarySet", 0);
            x.putString("android.ongoingActivityNoti.nowbarPrimaryInfo", g.score());
            x.putString("android.ongoingActivityNoti.nowbarSecondaryInfo", text);
            x.putString("android.ongoingActivityNoti.chipExpandedText", g.score());
        }
        b.addExtras(x);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT < 36) return n;
        return liveUpdate(c, n, g, crests ? homeCrest : null, crests ? awayCrest : null);
    }

    /**
     * Android 16+: Live Update (setRequestPromotedOngoing + setShortCriticalText) in ProgressStyle with the crests at
     * both ends. These APIs are newer than the SDK the app is built with, so they are called by reflection;
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
            try {
                Class<?> ps = Class.forName("android.app.Notification$ProgressStyle");
                Class<?> seg = Class.forName("android.app.Notification$ProgressStyle$Segment");
                Object style = ps.getConstructor().newInstance();
                List<Object> segs = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    Object s = seg.getConstructor(int.class).newInstance(600);
                    seg.getMethod("setColor", int.class).invoke(s, RED);
                    segs.add(s);
                }
                ps.getMethod("setProgressSegments", List.class).invoke(style, segs);
                ps.getMethod("setProgress", int.class).invoke(style, g.elapsed());
                try {
                    ps.getMethod("setStyledByProgress", boolean.class).invoke(style, true);
                } catch (Throwable ignored) {
                }
                // Home team on the right whatever the phone's language: on a right-to-left phone the bar's start
                // is on the right, on a left-to-right phone it is on the left.
                boolean rtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
                Icon home = Icon.createWithBitmap(homeCrest), away = Icon.createWithBitmap(awayCrest);
                ps.getMethod("setProgressStartIcon", Icon.class).invoke(style, rtl ? home : away);
                ps.getMethod("setProgressEndIcon", Icon.class).invoke(style, rtl ? away : home);
                fb.setStyle((Notification.Style) style);
            } catch (Throwable ignored) {
                // No ProgressStyle on this device: the standard Live Update with the names in the title
                fb.setContentTitle(g.scoreWithNames());
            }
        } else {
            fb.setContentTitle(g.scoreWithNames());
        }
        try {
            return fb.build();
        } catch (Throwable e) {
            return n;
        }
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
