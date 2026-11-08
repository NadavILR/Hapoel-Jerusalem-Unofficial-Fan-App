package il.nadavilr.hapoeljerusalem;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the tip-off times of the next games (sent by the app) and an exact alarm for each one, so the live
 * score notification starts at tip-off even when the app is closed. Survives restarts (LiveAlarmReceiver).
 */
final class LiveScheduler {

    private static final String PREFS = "hpj-live";
    private static final int SLOTS = 8;
    private static final long WINDOW_BEFORE = 10 * 60_000L, WINDOW_AFTER = 3 * 3_600_000L;

    private LiveScheduler() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Live score is on in the app's settings and the device allows the app's notifications. */
    static boolean allowed(Context c) {
        return prefs(c).getBoolean("on", false) && NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    /** Saves the settings and tip-off times from the app and re-arms the alarms. */
    static void save(Context c, boolean on, List<Long> times) {
        StringBuilder sb = new StringBuilder();
        for (Long t : times) {
            if (t == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        prefs(c).edit().putBoolean("on", on).putString("times", sb.toString()).apply();
        reschedule(c);
    }

    static List<Long> times(Context c) {
        List<Long> out = new ArrayList<>();
        for (String s : prefs(c).getString("times", "").split(",")) {
            try {
                if (!s.isEmpty()) out.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** A game whose live window (10 minutes before tip-off to 3 hours after) is now, and not yet done/dismissed. */
    static long current(Context c) {
        long now = System.currentTimeMillis();
        for (long t : times(c)) {
            if (now >= t - WINDOW_BEFORE && now < t + WINDOW_AFTER && !handled(c, t)) return t;
        }
        return 0;
    }

    static void done(Context c, long t) {
        prefs(c).edit().putLong("done", t).apply();
    }

    static void dismissed(Context c, long t) {
        done(c, t);
    }

    private static boolean handled(Context c, long t) {
        return prefs(c).getLong("done", 0) == t;
    }

    private static PendingIntent alarm(Context c, int slot, long t, int flags) {
        Intent i = new Intent(c, LiveAlarmReceiver.class).setAction(LiveAlarmReceiver.ACTION_TIPOFF).putExtra("t", t);
        return PendingIntent.getBroadcast(c, 900 + slot, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static void reschedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < SLOTS; i++) {
            PendingIntent old = alarm(c, i, 0, PendingIntent.FLAG_NO_CREATE);
            if (old != null) {
                am.cancel(old);
                old.cancel();
            }
        }
        if (!prefs(c).getBoolean("on", false)) return;
        long now = System.currentTimeMillis();
        int slot = 0;
        for (long t : times(c)) {
            if (slot >= SLOTS) break;
            if (t <= now) continue;
            PendingIntent pi = alarm(c, slot++, t, PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                if (exact && Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                else if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                else am.setExact(AlarmManager.RTC_WAKEUP, t, pi);
            } catch (Exception ignored) {
            }
        }
    }
}
