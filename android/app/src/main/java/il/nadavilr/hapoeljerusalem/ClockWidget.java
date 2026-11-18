package il.nadavilr.hapoeljerusalem;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Clock widget (5×2, transparent): the club crest, a big clock (the system's TextClock – it ticks on its own), the day,
 * the date (changes at midnight) and the Hebrew date (changes at sunset in Jerusalem, as the Hebrew calendar does), the club's titles, and a countdown to the next game (a Chronometer counting down the
 * hours, minutes and seconds; the days beside it). Refreshed at midnight (the dates), when a day of the countdown runs
 * out, at tip-off, and whenever the app syncs its game list.
 */
public class ClockWidget extends AppWidgetProvider {

    private static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;
    private static final TimeZone IL = TimeZone.getTimeZone("Asia/Jerusalem");
    private static final String[] DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
    private static final String[] DAY_LETTER = {"א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'"};

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
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) {
        onUpdate(c, m, new int[] {id});
    }

    /** From any thread (the app's sync). */
    static void refresh(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> update(app)).start();
    }

    static void update(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, ClockWidget.class));
            if (ids == null || ids.length == 0) return;
            long now = System.currentTimeMillis();
            JSONObject g = next(c, now);
            Calendar cal = Calendar.getInstance(IL);
            cal.setTimeInMillis(now);
            String day = "יום " + DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1];
            String greg = String.format(Locale.ROOT, "%d.%d.%d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
            String heb = hebrewDate(now >= sunset(now) ? now + DAY : now);
            for (int id : ids) {
                RemoteViews v = build(c, now, g);
                fit(c, v, m.getAppWidgetOptions(id), day, greg, heb);
                m.updateAppWidget(id, v);
            }
            schedule(c, now, g);
        } catch (Exception ignored) {
        }
    }

    private static final String[] COMP = {"league", "ליגת ווינר", "euro", "יורוקאפ", "wcup", "גביע ווינר", "cup", "גביע המדינה", "super", "סופרקאפ", "prep", "משחק הכנה"};

    /**
     * EuroCup titles won. The ULEB Cup (2004) is the EuroCup's former name: while this is 0 the row shows the ULEB Cup
     * (its trophy, "גביע יול"ב", 1); from the first EuroCup title on it shows the EuroCup trophy, "יורוקאפ", and 1 + this.
     */
    static final int EUROCUP_WINS = 0;

    /** The competition and round, short: "ליגת ווינר, מחזור 7" → "ליגת ווינר · מחזור 7"; a group is left out. */
    private static String comp(JSONObject g) {
        String l = g.optString("l", "").trim();
        if (!l.isEmpty()) {
            String[] p = l.split("\\s*(,|–| - )\\s*");
            String c0 = p[0].trim(), last = p.length > 1 ? p[p.length - 1].trim() : "";
            return last.isEmpty() || last.equals(c0) ? c0 : c0 + " · " + last;
        }
        String c = g.optString("c", "");
        for (int i = 0; i < COMP.length; i += 2) if (COMP[i].equals(c)) return COMP[i + 1];
        return "";
    }

    private static RemoteViews build(Context c, long now, JSONObject g) {
        String rlm = "‏";
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        // the titles – the ULEB Cup row becomes the EuroCup row from the first EuroCup title
        if (EUROCUP_WINS > 0) {
            v.setImageViewResource(R.id.k_i4, R.drawable.cw_t_eurocup);
            v.setTextViewText(R.id.k_l4, "יורוקאפ");
            v.setTextViewText(R.id.k_n4, String.valueOf(1 + EUROCUP_WINS));
        }
        Calendar cal = Calendar.getInstance(IL);
        cal.setTimeInMillis(now);
        v.setTextViewText(R.id.k_day, rlm + "יום " + DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]);
        v.setTextViewText(R.id.k_greg, String.format(Locale.ROOT, "%d.%d.%d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)));
        String heb = hebrewDate(now >= sunset(now) ? now + DAY : now); // the Hebrew day begins at sunset
        v.setTextViewText(R.id.k_heb, rlm + heb);
        v.setViewVisibility(R.id.k_heb, heb.isEmpty() ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.k_s2, heb.isEmpty() ? View.GONE : View.VISIBLE);
        if (g == null) {
            v.setTextViewText(R.id.k_op, rlm + "אין משחקים קרובים");
            v.setTextViewText(R.id.k_comp, "");
            v.setViewVisibility(R.id.k_crests, View.GONE);
            v.setViewVisibility(R.id.k_when, View.GONE);
            v.setViewVisibility(R.id.k_count, View.GONE);
            v.setOnClickPendingIntent(R.id.k_root, LiveGame.open(c, 0));
            return v;
        }
        long t = g.optLong("t"), left = t - now;
        Calendar gc = Calendar.getInstance(IL);
        gc.setTimeInMillis(t);
        v.setTextViewText(R.id.k_op, rlm + g.optString("o", ""));
        v.setTextViewText(R.id.k_comp, rlm + comp(g));
        Bitmap oc = NextGameWidget.crest(c, g.optString("img", ""));
        if (oc != null) v.setImageViewBitmap(R.id.k_opp, oc); else v.setImageViewResource(R.id.k_opp, android.R.color.transparent);
        v.setTextViewText(R.id.k_wd, rlm + "יום " + DAY_LETTER[gc.get(Calendar.DAY_OF_WEEK) - 1] + " · "
            + String.format(Locale.ROOT, "%d.%d", gc.get(Calendar.DAY_OF_MONTH), gc.get(Calendar.MONTH) + 1));
        v.setTextViewText(R.id.k_wt, String.format(Locale.ROOT, "%02d:%02d", gc.get(Calendar.HOUR_OF_DAY), gc.get(Calendar.MINUTE)));
        v.setOnClickPendingIntent(R.id.k_root, LiveGame.open(c, t));
        if (left <= 0) {
            v.setViewVisibility(R.id.k_count, View.GONE);
            v.setTextViewText(R.id.k_status, rlm + "המשחק התחיל · לחצו לתוצאה");
            v.setViewVisibility(R.id.k_status, View.VISIBLE);
            return v;
        }
        // days and hours set here (refreshed when the hour turns); minutes and seconds: one "MM:SS" countdown in both
        // boxes, each clipped to its own two digits – the same as the next-game widget
        long days = left / DAY, hours = (left % DAY) / HOUR, rest = left % HOUR;
        v.setTextViewText(R.id.k_dd, String.format(Locale.US, "%02d", days));
        v.setTextViewText(R.id.k_hh, String.format(Locale.US, "%02d", hours));
        long base = SystemClock.elapsedRealtime() + rest;
        v.setChronometer(R.id.k_mm, base, null, true);
        v.setChronometer(R.id.k_ss, base, null, true);
        if (Build.VERSION.SDK_INT >= 24) {
            v.setChronometerCountDown(R.id.k_mm, true);
            v.setChronometerCountDown(R.id.k_ss, true);
        }
        return v;
    }

    // ---------- fitting the design to the widget's real size (Android 12+) ----------

    /** The design's base size, in dp: the widget as measured on a Galaxy S24 Ultra. */
    private static final float BW = 362f, BH = 195f;

    private static android.graphics.Paint paint(Context c, float dp, boolean bold) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(dp * c.getResources().getDisplayMetrics().density);
        try { p.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, bold ? R.font.noto_sans_hebrew_bold : R.font.noto_sans_hebrew_regular)); }
        catch (Exception e) { p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL)); }
        p.setFontFeatureSettings("tnum");
        return p;
    }

    /**
     * Scales every size, text and gap to the widget's real size (the launcher reports it in dp, so the screen-resolution
     * setting does not change the result): one factor k = the smaller of width and height against the base. The date row
     * shrinks further if its text would not fit inside the padding.
     */
    private static void fit(Context c, RemoteViews v, android.os.Bundle o, String day, String greg, String heb) {
        if (Build.VERSION.SDK_INT < 31 || o == null) return;
        float w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (w < 100 || h < 60) return;
        // k: text and widths (the smaller of width and height against the base); kh: what can grow with the height –
        // the clock, the crest, the row heights and the countdown – so a narrower widget (4 columns) keeps its height full
        float k = Math.min(1.6f, Math.min(w / BW, h / BH)), kh = Math.min(1.6f, h / BH);
        final int DP = android.util.TypedValue.COMPLEX_UNIT_DIP;
        float px = c.getResources().getDisplayMetrics().density;
        int pad = Math.round(12 * k * px);
        // the club panel
        float club = 122 * k;
        v.setViewLayoutWidth(R.id.k_club, club, DP);
        v.setViewPadding(R.id.k_club, pad, pad, pad, pad);
        float crest = Math.min(48 * kh, club - 24 * k);
        v.setViewLayoutWidth(R.id.k_crest, crest, DP);
        v.setViewLayoutHeight(R.id.k_crest, crest, DP);
        v.setTextViewTextSize(R.id.k_name, DP, 11.5f * k);
        v.setViewLayoutMargin(R.id.k_name, RemoteViews.MARGIN_TOP, 2 * k, DP);
        int[][] r = {{R.id.k_r1, R.id.k_i1, R.id.k_l1, R.id.k_n1}, {R.id.k_r2, R.id.k_i2, R.id.k_l2, R.id.k_n2}, {R.id.k_r3, R.id.k_i3, R.id.k_l3, R.id.k_n3},
            {R.id.k_r4, R.id.k_i4, R.id.k_l4, R.id.k_n4}, {R.id.k_r5, R.id.k_i5, R.id.k_l5, R.id.k_n5}};
        for (int[] x : r) {
            v.setViewLayoutHeight(x[0], 18 * kh, DP);
            v.setViewLayoutWidth(x[1], 18 * k, DP);
            v.setViewLayoutHeight(x[1], 16 * Math.min(kh, k * 1.15f), DP);
            v.setTextViewTextSize(x[2], DP, 10 * k);
            v.setViewLayoutMargin(x[2], RemoteViews.MARGIN_START, 4 * k, DP);
            v.setTextViewTextSize(x[3], DP, 11.5f * k);
        }
        // the main panel
        v.setViewPadding(R.id.k_main, pad, pad, pad, pad);
        float mainW = w - club - 24 * k, dateText = 10.5f * k, sepW = 13 * k;
        v.setTextViewTextSize(R.id.k_time, DP, Math.min(52 * kh, mainW / 2.75f));
        // measured in pixels → back to dp (the widths here are in dp), or the row would shrink by the screen density
        float dw = (paint(c, dateText, true).measureText(day) + paint(c, dateText, false).measureText(greg) + (heb.isEmpty() ? 0 : paint(c, dateText, false).measureText(heb))) / px
            + sepW * (heb.isEmpty() ? 1 : 2);
        if (dw > mainW) dateText *= mainW / dw * 0.98f; // never past the padding
        for (int id : new int[] {R.id.k_day, R.id.k_greg, R.id.k_heb}) v.setTextViewTextSize(id, DP, dateText);
        for (int id : new int[] {R.id.k_s1, R.id.k_s2}) {
            v.setViewLayoutHeight(id, 10 * k, DP);
            v.setViewLayoutMargin(id, RemoteViews.MARGIN_START, 6 * k * Math.min(1, dateText / (10.5f * k)), DP);
            v.setViewLayoutMargin(id, RemoteViews.MARGIN_END, 6 * k * Math.min(1, dateText / (10.5f * k)), DP);
        }
        v.setViewPadding(R.id.k_dates, 0, 0, 0, Math.round(6 * k * px)); // optical centring between the clock and the card
        // the next-game card
        v.setViewPadding(R.id.k_card, Math.round(9 * k * px), Math.round(7 * kh * px), Math.round(9 * k * px), Math.round(7 * kh * px));
        v.setViewLayoutWidth(R.id.k_crests, 34 * k, DP);
        v.setViewLayoutHeight(R.id.k_crests, 20 * k, DP);
        for (int id : new int[] {R.id.k_me, R.id.k_opp}) { v.setViewLayoutWidth(id, 20 * k, DP); v.setViewLayoutHeight(id, 20 * k, DP); }
        v.setViewLayoutMargin(R.id.k_who, RemoteViews.MARGIN_START, 6 * k, DP);
        v.setViewLayoutMargin(R.id.k_when, RemoteViews.MARGIN_START, 6 * k, DP);
        v.setTextViewTextSize(R.id.k_op, DP, 12 * k);
        for (int id : new int[] {R.id.k_comp, R.id.k_wd, R.id.k_wt}) v.setTextViewTextSize(id, DP, 9 * k);
        v.setTextViewTextSize(R.id.k_status, DP, 11 * k);
        v.setViewLayoutMargin(R.id.k_count, RemoteViews.MARGIN_TOP, 5 * kh, DP);
        float boxW = (mainW - 18 * k - 18 * k) / 4, num = Math.min(19 * kh, (boxW - 6 * k) / 1.25f);
        int[][] b = {{R.id.k_b1, R.id.k_dd, R.id.k_b1l}, {R.id.k_b2, R.id.k_hh, R.id.k_b2l}, {R.id.k_b3, R.id.k_mm, R.id.k_b3l}, {R.id.k_b4, R.id.k_ss, R.id.k_b4l}};
        for (int i = 0; i < b.length; i++) {
            v.setViewPadding(b[i][0], 0, Math.round(4 * kh * px), 0, Math.round(5 * kh * px));
            if (i > 0) v.setViewLayoutMargin(b[i][0], RemoteViews.MARGIN_START, 6 * k, DP);
            v.setTextViewTextSize(b[i][1], DP, num);
            v.setTextViewTextSize(b[i][2], DP, 8.5f * k);
            v.setViewLayoutMargin(b[i][2], RemoteViews.MARGIN_TOP, k, DP);
        }
        float two = (float) Math.ceil(paint(c, num, true).measureText("00")) + 1f;
        v.setViewLayoutWidth(R.id.k_mm_clip, two, android.util.TypedValue.COMPLEX_UNIT_PX);
        v.setViewLayoutWidth(R.id.k_ss_clip, two, android.util.TypedValue.COMPLEX_UNIT_PX);
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

    // ---------- the next refresh: midnight (dates), the end of a day of the countdown, tip-off ----------

    private static void schedule(Context c, long now, JSONObject g) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar mid = Calendar.getInstance(IL);
        mid.setTimeInMillis(now);
        mid.add(Calendar.DAY_OF_MONTH, 1);
        mid.set(Calendar.HOUR_OF_DAY, 0);
        mid.set(Calendar.MINUTE, 0);
        mid.set(Calendar.SECOND, 5);
        mid.set(Calendar.MILLISECOND, 0);
        long at = Math.min(mid.getTimeInMillis(), now + 30 * MIN), ss = sunset(now);
        if (ss > now) at = Math.min(at, ss + 5_000L); // the Hebrew date changes at sunset
        if (g != null) {
            long left = g.optLong("t") - now;
            if (left > 0) {
                long rest = left % HOUR;
                at = Math.min(at, now + (rest > 0 ? rest : HOUR) + 1_000L); // the hours (and days) go down
            } else at = Math.min(at, g.optLong("t") + 3 * HOUR + 60_000L);
        }
        Intent i = new Intent(c, ClockWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        try {
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, ClockWidget.class)));
        } catch (Exception ignored) {
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, 7302, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi);
            else am.set(AlarmManager.RTC, at, pi);
        } catch (Exception ignored) {
        }
    }
}
