package il.nadavilr.hapoeljerusalem;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
 * the date and the Hebrew date, the club's titles, and a countdown to the next game (a Chronometer counting down the
 * hours, minutes and seconds; the days beside it). Refreshed at midnight (the dates), when a day of the countdown runs
 * out, at tip-off, and whenever the app syncs its game list.
 */
public class ClockWidget extends AppWidgetProvider {

    private static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;
    private static final TimeZone IL = TimeZone.getTimeZone("Asia/Jerusalem");
    private static final String TITLES = "אלופת המדינה (2) · גביע המדינה (8) · גביע ווינר (7) · גביע יול\"ב (1) · סופרקאפ (1)";
    private static final String[] DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
    private static final String[] DAY_LETTER = {"א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'"};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        update(c);
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

    private static RemoteViews build(Context c, long now, JSONObject g) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        Calendar cal = Calendar.getInstance(IL, new Locale("he", "IL"));
        cal.setTimeInMillis(now);
        String date = "יום " + DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1] + " · "
            + String.format(Locale.ROOT, "%02d/%02d/%d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
        String heb = hebrewDate(now);
        v.setTextViewText(R.id.c_date, "‏" + date + (heb.isEmpty() ? "" : " · " + heb));
        v.setTextViewText(R.id.c_titles, "‏" + TITLES);
        if (g == null) {
            v.setTextViewText(R.id.c_next, "‏אין משחקים קרובים");
            v.setViewVisibility(R.id.c_days, View.GONE);
            v.setViewVisibility(R.id.c_count, View.GONE);
            v.setOnClickPendingIntent(R.id.c_root, LiveGame.open(c, 0));
            return v;
        }
        long t = g.optLong("t"), left = t - now;
        Calendar gc = Calendar.getInstance(IL);
        gc.setTimeInMillis(t);
        String when = "יום " + DAY_LETTER[gc.get(Calendar.DAY_OF_WEEK) - 1] + " "
            + String.format(Locale.ROOT, "%d/%d", gc.get(Calendar.DAY_OF_MONTH), gc.get(Calendar.MONTH) + 1)
            + (g.optInt("tbd", 0) == 1 ? "" : String.format(Locale.ROOT, " · %02d:%02d", gc.get(Calendar.HOUR_OF_DAY), gc.get(Calendar.MINUTE)));
        String opp = g.optString("o", "");
        v.setTextViewText(R.id.c_next, "‏" + (opp.isEmpty() ? "" : opp + " · ") + when);
        if (left <= 0) {
            v.setTextViewText(R.id.c_days, "המשחק התחיל");
            v.setViewVisibility(R.id.c_days, View.VISIBLE);
            v.setViewVisibility(R.id.c_count, View.GONE);
        } else {
            long days = left / DAY, rest = left % DAY;
            v.setTextViewText(R.id.c_days, days > 0 ? (days == 1 ? "עוד יום" : "עוד " + days + " ימים") : "היום");
            v.setViewVisibility(R.id.c_days, View.VISIBLE);
            if (Build.VERSION.SDK_INT >= 24) {
                // The hours, minutes and seconds left beyond the whole days, counting down on their own
                v.setChronometer(R.id.c_count, SystemClock.elapsedRealtime() + rest, null, true);
                v.setChronometerCountDown(R.id.c_count, true);
                v.setViewVisibility(R.id.c_count, View.VISIBLE);
            } else {
                v.setViewVisibility(R.id.c_count, View.GONE);
                long h = rest / HOUR;
                v.setTextViewText(R.id.c_days, (days > 0 ? "עוד " + days + " ימים ו-" : "עוד ") + h + " שעות");
            }
        }
        v.setOnClickPendingIntent(R.id.c_root, LiveGame.open(c, t));
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
            return gematria(d) + " ב" + month + " " + gematria(y % 1000);
        } catch (Throwable e) {
            return "";
        }
    }

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
        long at = Math.min(mid.getTimeInMillis(), now + 30 * MIN);
        if (g != null) {
            long left = g.optLong("t") - now;
            if (left > 0) {
                long rest = left % DAY;
                at = Math.min(at, now + (rest > 0 ? rest : DAY) + 1_000L);
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
