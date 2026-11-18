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
            RemoteViews v = build(c, now, g);
            for (int id : ids) m.updateAppWidget(id, v);
            schedule(c, now, g);
        } catch (Exception ignored) {
        }
    }

    private static final String[] COMP = {"league", "ליגת ווינר", "euro", "יורוקאפ", "wcup", "גביע ווינר", "cup", "גביע המדינה", "super", "סופרקאפ", "prep", "משחק הכנה"};

    /** The competition, short ("ליגת ווינר, מחזור 7" → "ליגת ווינר"): from the app's label, else from its code. */
    private static String comp(JSONObject g) {
        String l = g.optString("l", "").trim();
        if (!l.isEmpty()) return l.split("[,–]| - ")[0].trim();
        String c = g.optString("c", "");
        for (int i = 0; i < COMP.length; i += 2) if (COMP[i].equals(c)) return COMP[i + 1];
        return "";
    }

    private static RemoteViews build(Context c, long now, JSONObject g) {
        String rlm = "\u200F";
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        Calendar cal = Calendar.getInstance(IL);
        cal.setTimeInMillis(now);
        v.setTextViewText(R.id.c_day, rlm + "יום " + DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]);
        v.setTextViewText(R.id.c_greg, String.format(Locale.ROOT, "%d.%d.%d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)));
        String heb = hebrewDate(now >= sunset(now) ? now + DAY : now); // the Hebrew day begins at sunset
        v.setTextViewText(R.id.c_heb, rlm + heb);
        v.setViewVisibility(R.id.c_heb, heb.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        if (g == null) {
            v.setTextViewText(R.id.c_lb, rlm + "המשחק הבא");
            v.setTextViewText(R.id.c_op, rlm + "אין משחקים קרובים");
            v.setTextViewText(R.id.c_wh, "");
            v.setViewVisibility(R.id.c_crests, View.GONE);
            v.setViewVisibility(R.id.c_count, View.GONE);
            v.setOnClickPendingIntent(R.id.c_root, LiveGame.open(c, 0));
            return v;
        }
        long t = g.optLong("t"), left = t - now;
        Calendar gc = Calendar.getInstance(IL);
        gc.setTimeInMillis(t);
        String cn = comp(g);
        v.setTextViewText(R.id.c_lb, rlm + "המשחק הבא" + (cn.isEmpty() ? "" : " · " + cn));
        v.setTextViewText(R.id.c_op, rlm + g.optString("o", ""));
        v.setViewVisibility(R.id.c_crests, View.VISIBLE);
        Bitmap oc = NextGameWidget.crest(c, g.optString("img", ""));
        if (oc != null) v.setImageViewBitmap(R.id.c_opp, oc); else v.setImageViewResource(R.id.c_opp, android.R.color.transparent);
        String when = "יום " + DAY_LETTER[gc.get(Calendar.DAY_OF_WEEK) - 1] + " "
            + String.format(Locale.ROOT, "%d.%d · %02d:%02d", gc.get(Calendar.DAY_OF_MONTH), gc.get(Calendar.MONTH) + 1, gc.get(Calendar.HOUR_OF_DAY), gc.get(Calendar.MINUTE));
        v.setOnClickPendingIntent(R.id.c_root, LiveGame.open(c, t));
        if (left <= 0) {
            v.setTextViewText(R.id.c_wh, rlm + "המשחק התחיל · לחצו לתוצאה");
            v.setViewVisibility(R.id.c_count, View.GONE);
            return v;
        }
        v.setTextViewText(R.id.c_wh, rlm + when);
        v.setViewVisibility(R.id.c_count, View.VISIBLE);
        // days and hours set here (refreshed when the hour turns); minutes and seconds: one "MM:SS" countdown in both
        // boxes, each clipped to its own two digits – the same as the next-game widget
        long days = left / DAY, hours = (left % DAY) / HOUR, rest = left % HOUR;
        v.setTextViewText(R.id.c_dd, String.format(Locale.US, "%02d", days));
        v.setTextViewText(R.id.c_hh, String.format(Locale.US, "%02d", hours));
        long base = SystemClock.elapsedRealtime() + rest;
        v.setChronometer(R.id.c_mm, base, null, true);
        v.setChronometer(R.id.c_ss, base, null, true);
        if (Build.VERSION.SDK_INT >= 24) {
            v.setChronometerCountDown(R.id.c_mm, true);
            v.setChronometerCountDown(R.id.c_ss, true);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            float w = NextGameWidget.twoDigits(c, 16);
            v.setViewLayoutWidth(R.id.c_mm_clip, w, android.util.TypedValue.COMPLEX_UNIT_PX);
            v.setViewLayoutWidth(R.id.c_ss_clip, w, android.util.TypedValue.COMPLEX_UNIT_PX);
        }
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
