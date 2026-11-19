package il.nadavilr.hapoeljerusalem;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Clock widget (5×2): the club panel with the titles, the clock with seconds, the day / date / Hebrew date (the Hebrew
 * date changes at sunset in Jerusalem), and the next game with a countdown. Every text and number is drawn in Noto Sans
 * Hebrew (NotoText) – a widget cannot use the app's font. The seconds are flippers of 60 Noto digit drawings that turn
 * every second on their own; WidgetTick re-syncs them to the real time every few seconds while the screen is on, and
 * redraws the widget at the turn of every minute.
 */
public class ClockWidget extends AppWidgetProvider {

    static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;
    static final TimeZone IL = TimeZone.getTimeZone("Asia/Jerusalem");
    private static final String[] DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
    private static final String[] DAY_LETTER = {"א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'"};
    private static final int WHITE = 0xFFFFFFFF, GOLD = 0xFFFFE08A, MUTED = 0xBFFFFFFF, SOFT = 0xE0FFFFFF, LABEL = 0xB3FFFFFF;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try { update(app); } finally { pr.finish(); }
        }).start();
    }

    /** The widget was resized, or the launcher measured it (e.g. after a screen-resolution change): fit it again. */
    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        onUpdate(c, m, new int[] {id});
    }

    /** From any thread (the app's sync). */
    static void refresh(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> update(app)).start();
    }

    static int[] ids(Context c) {
        try {
            return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, ClockWidget.class));
        } catch (Exception e) {
            return new int[0];
        }
    }

    private static long drawnMinute = -1;

    /** Full redraw of every clock widget, then the next tick. */
    static synchronized void update(Context c) {
        try {
            int[] ids = ids(c);
            if (ids.length == 0) return;
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            long now = System.currentTimeMillis();
            JSONObject g = next(c, now);
            for (int id : ids) m.updateAppWidget(id, build(c, now, g, m.getAppWidgetOptions(id)));
            drawnMinute = now / MIN;
        } catch (Exception ignored) {
        }
        WidgetTick.schedule(c);
    }

    /**
     * Every few seconds while the screen is on (WidgetTick): a new minute – redraw; otherwise only put the seconds
     * flippers on the right second (a tiny partial update).
     */
    static synchronized void tick(Context c) {
        int[] ids = ids(c);
        if (ids.length == 0) return;
        long now = System.currentTimeMillis();
        if (now / MIN != drawnMinute) { update(c); return; }
        try {
            RemoteViews p = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
            p.setDisplayedChild(R.id.k_sec, (int) ((now / 1000) % 60));
            JSONObject g = next(c, now);
            if (g != null && g.optLong("t") > now) p.setDisplayedChild(R.id.k_ss, 59 - (int) (((g.optLong("t") - now) / 1000) % 60));
            AppWidgetManager.getInstance(c).partiallyUpdateAppWidget(ids, p);
        } catch (Exception ignored) {
        }
    }

    static boolean has(Context c) {
        return ids(c).length > 0;
    }

    private static final String[] COMP = {"league", "ליגת ווינר", "euro", "יורוקאפ", "wcup", "גביע ווינר", "cup", "גביע המדינה", "super", "סופרקאפ", "prep", "משחק הכנה"};

    /**
     * EuroCup titles won. The ULEB Cup (2004) is the EuroCup's former name: while this is 0 the row shows the ULEB Cup
     * (its trophy, "גביע יול"ב", 1); from the first EuroCup title on it shows the EuroCup trophy, "יורוקאפ", and 1 + this.
     */
    static final int EUROCUP_WINS = 0;

    /** "ליגת ווינר, מחזור 7" → "ליגת ווינר · מחזור 7"; "יורוקאפ – בית א', מחזור 10" → "יורוקאפ · מחזור 10". */
    static String shortComp(String l) {
        l = l == null ? "" : l.trim();
        if (l.isEmpty()) return "";
        String[] p = l.split("\\s*(,|–| - )\\s*");
        String c0 = p[0].trim(), last = p.length > 1 ? p[p.length - 1].trim() : "";
        return last.isEmpty() || last.equals(c0) ? c0 : c0 + " · " + last;
    }

    private static String comp(JSONObject g) {
        String l = shortComp(g.optString("l", ""));
        if (!l.isEmpty()) return l;
        String c = g.optString("c", "");
        for (int i = 0; i < COMP.length; i += 2) if (COMP[i].equals(c)) return COMP[i + 1];
        return "";
    }

    /** The design's base size, in dp: the widget as measured on a Galaxy S24 Ultra. */
    private static final float BW = 362f, BH = 195f;

    /** The real size of a widget in dp (Android 12+ reports it; before that the base size). */
    static float[] size(Bundle o, float bw, float bh, boolean land) {
        if (o == null) return new float[] {bw, bh};
        float w = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        float h = o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        return w < 100 || h < 30 ? new float[] {bw, bh} : new float[] {w, h};
    }

    private static RemoteViews build(Context c, long now, JSONObject g, Bundle o) {
        final int DP = android.util.TypedValue.COMPLEX_UNIT_DIP;
        boolean resize = Build.VERSION.SDK_INT >= 31;
        float[] sz = resize ? size(o, BW, BH, false) : new float[] {BW, BH};
        float w = sz[0], h = sz[1];
        // k: text and widths (the smaller of width and height against the base); kh: what may grow with the height
        float k = resize ? Math.min(1.6f, Math.min(w / BW, h / BH)) : 1f, kh = resize ? Math.min(1.6f, h / BH) : 1f;
        float px = c.getResources().getDisplayMetrics().density;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_clock);

        // ---------- the club panel ----------
        float club = 122 * k, pad = 12 * k, inner = club - 2 * pad;
        if (resize) {
            v.setViewLayoutWidth(R.id.k_club, club, DP);
            int p = Math.round(pad * px);
            v.setViewPadding(R.id.k_club, p, p, p, p);
            float crest = Math.min(48 * kh, inner);
            v.setViewLayoutWidth(R.id.k_crest, crest, DP);
            v.setViewLayoutHeight(R.id.k_crest, crest, DP);
            v.setViewLayoutMargin(R.id.k_name, RemoteViews.MARGIN_TOP, 2 * k, DP);
        }
        NotoText.set(c, v, R.id.k_name, "הפועל ירושלים", 11.5f * k, true, WHITE, inner);
        String[] labels = {"אליפויות", "גביע המדינה", "גביע ווינר", "גביע יול\"ב", "סופרקאפ"};
        String[] counts = {"2", "8", "7", "1", "1"};
        if (EUROCUP_WINS > 0) {
            v.setImageViewResource(R.id.k_i4, R.drawable.cw_t_eurocup);
            labels[3] = "יורוקאפ";
            counts[3] = String.valueOf(1 + EUROCUP_WINS);
        }
        int[][] r = {{R.id.k_r1, R.id.k_i1, R.id.k_l1, R.id.k_n1}, {R.id.k_r2, R.id.k_i2, R.id.k_l2, R.id.k_n2}, {R.id.k_r3, R.id.k_i3, R.id.k_l3, R.id.k_n3},
            {R.id.k_r4, R.id.k_i4, R.id.k_l4, R.id.k_n4}, {R.id.k_r5, R.id.k_i5, R.id.k_l5, R.id.k_n5}};
        for (int i = 0; i < r.length; i++) {
            if (resize) {
                v.setViewLayoutHeight(r[i][0], 18 * kh, DP);
                v.setViewLayoutWidth(r[i][1], 18 * k, DP);
                v.setViewLayoutHeight(r[i][1], 16 * Math.min(kh, k * 1.15f), DP);
            }
            NotoText.set(c, v, r[i][2], labels[i], 10 * k, false, WHITE, inner - 18 * k - 4 * k - 16 * k);
            NotoText.set(c, v, r[i][3], counts[i], 11.5f * k, true, GOLD, 0);
        }

        // ---------- the clock ----------
        float mainW = w - club - 2 * pad;
        if (resize) {
            int p = Math.round(pad * px);
            v.setViewPadding(R.id.k_main, p, p, p, p);
        }
        Calendar cal = Calendar.getInstance(IL);
        cal.setTimeInMillis(now);
        String hm = String.format(Locale.ROOT, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        // HH:mm big, :ss at 0.42 of it; together they never pass the panel's width
        float clock = Math.min(52 * kh, mainW / 3.85f);
        float need = NotoText.widthDp(c, hm, clock, true) + NotoText.widthDp(c, ":", clock * 0.42f, true) + clock * 0.42f * 1.164f + 2 * k;
        if (need > mainW) clock *= mainW / need * 0.98f;
        float sec = clock * 0.42f;
        NotoText.set(c, v, R.id.k_time, hm, clock, true, WHITE, 0);
        NotoText.set(c, v, R.id.k_colon, ":", sec, true, 0xD9FFFFFF, 0);
        if (resize) {
            v.setViewLayoutWidth(R.id.k_sec, sec * 1.164f, DP);
            v.setViewLayoutHeight(R.id.k_sec, sec * 1.36f, DP);
            v.setViewLayoutMargin(R.id.k_colon, RemoteViews.MARGIN_START, 2 * k, DP);
        }
        v.setDisplayedChild(R.id.k_sec, cal.get(Calendar.SECOND));

        // ---------- the dates (the row shrinks if it would not fit inside the padding) ----------
        String day = "יום " + DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1];
        String greg = String.format(Locale.ROOT, "%d.%d.%d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
        String heb = hebrewDate(now >= sunset(now) ? now + DAY : now); // the Hebrew day begins at sunset
        float dt = 10.5f * k, sepW = 13 * k;
        float dw = NotoText.widthDp(c, day, dt, true) + NotoText.widthDp(c, greg, dt, false) + (heb.isEmpty() ? 0 : NotoText.widthDp(c, heb, dt, false) + sepW) + sepW;
        if (dw > mainW) dt *= mainW / dw * 0.98f;
        NotoText.set(c, v, R.id.k_day, day, dt, true, WHITE, 0);
        NotoText.set(c, v, R.id.k_greg, greg, dt, false, SOFT, 0);
        NotoText.set(c, v, R.id.k_heb, heb, dt, false, SOFT, 0);
        v.setViewVisibility(R.id.k_heb, heb.isEmpty() ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.k_s2, heb.isEmpty() ? View.GONE : View.VISIBLE);
        if (resize) {
            float sm = 6 * k * Math.min(1, dt / (10.5f * k));
            for (int id : new int[] {R.id.k_s1, R.id.k_s2}) {
                v.setViewLayoutHeight(id, 10 * k, DP);
                v.setViewLayoutMargin(id, RemoteViews.MARGIN_START, sm, DP);
                v.setViewLayoutMargin(id, RemoteViews.MARGIN_END, sm, DP);
            }
            v.setViewPadding(R.id.k_dates, 0, 0, 0, Math.round(6 * k * px)); // optical centring between the clock and the card
        }

        // ---------- the next game ----------
        float cardW = mainW - 18 * k;
        if (resize) {
            v.setViewPadding(R.id.k_card, Math.round(9 * k * px), Math.round(7 * kh * px), Math.round(9 * k * px), Math.round(7 * kh * px));
            v.setViewLayoutWidth(R.id.k_crests, 34 * k, DP);
            v.setViewLayoutHeight(R.id.k_crests, 20 * k, DP);
            for (int id : new int[] {R.id.k_me, R.id.k_opp}) { v.setViewLayoutWidth(id, 20 * k, DP); v.setViewLayoutHeight(id, 20 * k, DP); }
            v.setViewLayoutMargin(R.id.k_who, RemoteViews.MARGIN_START, 6 * k, DP);
            v.setViewLayoutMargin(R.id.k_when, RemoteViews.MARGIN_START, 6 * k, DP);
            v.setViewLayoutMargin(R.id.k_count, RemoteViews.MARGIN_TOP, 5 * kh, DP);
        }
        float small = 9 * k;
        if (g == null) {
            NotoText.set(c, v, R.id.k_op, "אין משחקים קרובים", small, true, WHITE, cardW);
            v.setViewVisibility(R.id.k_crests, View.GONE);
            v.setViewVisibility(R.id.k_comp, View.GONE);
            v.setViewVisibility(R.id.k_when, View.GONE);
            v.setViewVisibility(R.id.k_count, View.GONE);
            v.setOnClickPendingIntent(R.id.k_root, LiveGame.open(c, 0));
            return v;
        }
        long t = g.optLong("t"), left = t - now;
        Calendar gc = Calendar.getInstance(IL);
        gc.setTimeInMillis(t);
        String wd = "יום " + DAY_LETTER[gc.get(Calendar.DAY_OF_WEEK) - 1] + " · " + String.format(Locale.ROOT, "%d.%d", gc.get(Calendar.DAY_OF_MONTH), gc.get(Calendar.MONTH) + 1);
        String wt = String.format(Locale.ROOT, "%02d:%02d", gc.get(Calendar.HOUR_OF_DAY), gc.get(Calendar.MINUTE));
        float whenW = Math.max(NotoText.widthDp(c, wd, small, false), NotoText.widthDp(c, wt, small, false));
        float whoW = cardW - 34 * k - 6 * k - 6 * k - whenW;
        NotoText.set(c, v, R.id.k_wd, wd, small, false, MUTED, 0);
        NotoText.set(c, v, R.id.k_wt, wt, small, false, MUTED, 0);
        NotoText.set(c, v, R.id.k_op, g.optString("o", ""), small, true, WHITE, whoW);
        NotoText.set(c, v, R.id.k_comp, comp(g), small, false, MUTED, whoW);
        Bitmap oc = NextGameWidget.crest(c, g.optString("img", ""));
        if (oc != null) v.setImageViewBitmap(R.id.k_opp, oc); else v.setImageViewResource(R.id.k_opp, android.R.color.transparent);
        v.setOnClickPendingIntent(R.id.k_root, LiveGame.open(c, t));
        if (left <= 0) {
            v.setViewVisibility(R.id.k_count, View.GONE);
            v.setViewVisibility(R.id.k_statusbox, View.VISIBLE);
            NotoText.set(c, v, R.id.k_status, "המשחק התחיל · לחצו לתוצאה", 11 * k, true, WHITE, cardW);
            return v;
        }
        // the countdown: days, hours and minutes drawn here (redrawn every minute), the seconds a flipper
        long secs = left / 1000, days = secs / 86400, hours = (secs % 86400) / 3600, mins = (secs % 3600) / 60, ss = secs % 60;
        float boxW = (mainW - 18 * k - 18 * k) / 4, num = Math.min(19 * kh, (boxW - 6 * k) / 1.25f);
        String[] lab = {"ימים", "שעות", "דקות", "שניות"};
        int[][] b = {{R.id.k_b1, R.id.k_dd, R.id.k_b1l}, {R.id.k_b2, R.id.k_hh, R.id.k_b2l}, {R.id.k_b3, R.id.k_mm, R.id.k_b3l}, {R.id.k_b4, 0, R.id.k_b4l}};
        long[] val = {days, hours, mins};
        for (int i = 0; i < b.length; i++) {
            if (resize) {
                v.setViewPadding(b[i][0], 0, Math.round(4 * kh * px), 0, Math.round(5 * kh * px));
                if (i > 0) v.setViewLayoutMargin(b[i][0], RemoteViews.MARGIN_START, 6 * k, DP);
                v.setViewLayoutMargin(b[i][2], RemoteViews.MARGIN_TOP, k, DP);
            }
            if (b[i][1] != 0) NotoText.set(c, v, b[i][1], String.format(Locale.ROOT, "%02d", val[i]), num, true, WHITE, 0);
            NotoText.set(c, v, b[i][2], lab[i], 8.5f * k, false, LABEL, 0);
        }
        if (resize) {
            v.setViewLayoutWidth(R.id.k_ss, num * 1.164f, DP);
            v.setViewLayoutHeight(R.id.k_ss, num * 1.36f, DP);
        }
        v.setDisplayedChild(R.id.k_ss, 59 - (int) ss);
        return v;
    }

    /** The next game from the app's list: the first that has not started, or one that started less than 3 hours ago. */
    private static JSONObject next(Context c, long now) {
        try {
            JSONArray a = new JSONArray(LiveScheduler.gamesJson(c));
            JSONObject best = null;
            for (int i = 0; i < a.length(); i++) {
                JSONObject g = a.optJSONObject(i);
                if (g == null || g.optLong("t") <= 0 || g.optLong("t") < now - 3 * HOUR) continue;
                if (best == null || g.optLong("t") < best.optLong("t")) best = g;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- the Hebrew date (ICU's Hebrew calendar, Android 7+), e.g. "ח' בכסלו תשפ"ז" ----------

    private static final String[] HMONTHS = {"תשרי", "חשוון", "כסלו", "טבת", "שבט", "אדר א'", "אדר", "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול"};

    static String hebrewDate(long t) {
        if (Build.VERSION.SDK_INT < 24) return "";
        try {
            android.icu.util.HebrewCalendar h = new android.icu.util.HebrewCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Jerusalem"));
            h.setTimeInMillis(t);
            int d = h.get(android.icu.util.Calendar.DAY_OF_MONTH), mo = h.get(android.icu.util.Calendar.MONTH), y = h.get(android.icu.util.Calendar.YEAR);
            boolean leap = ((7 * y + 1) % 19) < 7;
            String month = mo == 6 && leap ? "אדר ב'" : HMONTHS[mo];
            return gematria(d) + " ב" + month + " " + gematria(y / 1000).replace("'", "") + "'" + gematria(y % 1000); // the full year: ה'תשפ"ז = 5787
        } catch (Throwable e) {
            return "";
        }
    }

    // ---------- sunset in Jerusalem (the Hebrew date changes then) ----------

    private static final double LAT = 31.7683, LON = 35.2137, ZENITH = 90.833;

    /** Sunset (epoch ms) on the Israel calendar day of {@code t}, by the standard almanac formula (about a minute off). */
    static long sunset(long t) {
        Calendar day = Calendar.getInstance(IL);
        day.setTimeInMillis(t);
        int n = day.get(Calendar.DAY_OF_YEAR);
        double lngHour = LON / 15, tt = n + ((18 - lngHour) / 24);
        double m = 0.9856 * tt - 3.289;
        double l = norm(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))));
        ra = (ra + (Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90)) / 15;
        double sinDec = 0.39782 * sin(l), cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (cos(ZENITH) - sinDec * sin(LAT)) / (cosDec * cos(LAT));
        if (cosH < -1 || cosH > 1) return Long.MAX_VALUE;
        double h = Math.toDegrees(Math.acos(cosH)) / 15;
        double ut = ((h + ra - 0.06571 * tt - 6.622 - lngHour) % 24 + 24) % 24;
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return u.getTimeInMillis() + Math.round(ut * HOUR);
    }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double norm(double deg) { return ((deg % 360) + 360) % 360; }

    /** A number in Hebrew letters, with a geresh (one letter) or gershayim (before the last letter); 15 → ט"ו, 16 → ט"ז. */
    static String gematria(int n) {
        StringBuilder s = new StringBuilder();
        String[] hundreds = {"", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק"};
        String[] tens = {"", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"};
        String[] ones = {"", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"};
        s.append(hundreds[(n / 100) % 10]);
        int r = n % 100;
        if (r == 15) s.append("טו");
        else if (r == 16) s.append("טז");
        else s.append(tens[r / 10]).append(ones[r % 10]);
        if (s.length() == 0) return "";
        if (s.length() == 1) return s + "'";
        return s.substring(0, s.length() - 1) + "\"" + s.charAt(s.length() - 1);
    }

}
