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

    /**
     * Live score is on in the app's settings and the device allows the app's notifications. On by default (like the
     * app's own defaults), so it works even if the app has not been opened since it was installed or updated.
     */
    static boolean allowed(Context c) {
        return prefs(c).getBoolean("on", true) && NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    /** The app's setting "score updates at the end of each quarter and at the end of the game" (on by default). */
    static boolean quarterAlerts(Context c) {
        return prefs(c).getBoolean("quarter", true);
    }

    /** The app's per-competition notification setting (league / euro / wcup / cup / super / prep), on by default. */
    static boolean compOn(Context c, String comp) {
        if (comp == null || comp.isEmpty()) return true;
        try {
            return new org.json.JSONObject(prefs(c).getString("comps", "{}")).optInt(comp, 1) != 0;
        } catch (Exception e) {
            return true;
        }
    }

    /** True the first time it is called for this game and key (start / q1..q8 / end): each alert is sent once. */
    static synchronized boolean alertOnce(Context c, long t, String key) {
        SharedPreferences p = prefs(c);
        String state = p.getLong("alertsFor", 0) == t ? p.getString("alerts", "") : "";
        if (("," + state + ",").contains("," + key + ",")) return false;
        p.edit().putLong("alertsFor", t).putString("alerts", state.isEmpty() ? key : state + "," + key).apply();
        return true;
    }

    /** Settings from the app (sync): quarter alerts and competitions. */
    /**
     * How the live score is shown, chosen in the app's settings: "c" – the app's own live card (default), or in the
     * Now Bar (Android 16+): "m" – the row of scores, "t" – text with the crests.
     */
    static String nowBar(Context c) {
        return prefs(c).getString("nb", "c");
    }

    static void saveNowBar(Context c, String nb) {
        if (nb != null && !nb.equals(nowBar(c))) prefs(c).edit().putString("nb", nb).apply();
    }

    static void saveSettings(Context c, Boolean quarter, org.json.JSONObject comps) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (quarter != null) e.putBoolean("quarter", quarter);
        if (comps != null) e.putString("comps", comps.toString());
        e.apply();
    }

    /**
     * The next games as the phone fetched them itself (LiveFixtures – no need to open the app): replaces the list,
     * keeps only the competitions that are on in the app's settings, and re-arms the tip-off alarms.
     */
    static void saveFetched(Context c, org.json.JSONArray games) {
        // Keep the app's own competition / round text ("l") of games it already sent
        // ... and the opponent's name, home / away, venue and crest the app sent (the widget shows them)
        java.util.Map<Long, String> labels = new java.util.HashMap<>();
        java.util.Map<Long, org.json.JSONObject> olds = new java.util.HashMap<>();
        try {
            org.json.JSONArray old = new org.json.JSONArray(prefs(c).getString("games", "[]"));
            for (int i = 0; i < old.length(); i++) {
                org.json.JSONObject o = old.optJSONObject(i);
                if (o != null && !o.optString("l", "").isEmpty()) labels.put(o.optLong("t", 0), o.optString("l"));
                if (o != null) olds.put(o.optLong("t", 0), o);
            }
        } catch (Exception ignored) {
        }
        org.json.JSONArray keep = new org.json.JSONArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < games.length() && keep.length() < 8; i++) {
            org.json.JSONObject g = games.optJSONObject(i);
            if (g == null || !compOn(c, g.optString("c", ""))) continue;
            String l = labels.get(g.optLong("t", 0));
            if (l != null) {
                try {
                    g.put("l", l);
                } catch (Exception ignored) {
                }
            }
            org.json.JSONObject o = olds.get(g.optLong("t", 0));
            for (String k : new String[] {"o", "h", "v", "img"}) {
                try {
                    if (o != null && o.has(k) && !"".equals(o.optString(k))) g.put(k, o.get(k));
                } catch (Exception ignored) {
                }
            }
            keep.put(g);
            if (sb.length() > 0) sb.append(',');
            sb.append(g.optLong("t", 0));
        }
        prefs(c).edit().putString("games", keep.toString()).putString("times", sb.toString()).apply();
        reschedule(c);
        NextGameWidget.refresh(c);
    }

    /**
     * Saves the settings and the next games from the app and re-arms the alarms.
     * {@code games}: [{t: tip-off ms, c: competition key, eg: EuroCup game code}], {@code jer}: Hapoel's players
     * {shirt number: Hebrew name} (for the play-by-play line).
     */
    static void save(Context c, boolean on, org.json.JSONArray games, org.json.JSONObject jer) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; games != null && i < games.length(); i++) {
            org.json.JSONObject g = games.optJSONObject(i);
            long t = g != null ? g.optLong("t", 0) : 0;
            if (t <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        SharedPreferences.Editor e = prefs(c).edit().putBoolean("on", on).putString("times", sb.toString());
        if (games != null) e.putString("games", games.toString());
        if (jer != null) e.putString("jer", jer.toString());
        e.apply();
        reschedule(c);
    }

    /** "Keep following in the background even when the app is closed" (the app's setting). */
    static boolean background(Context c) {
        return prefs(c).getBoolean("bg", false);
    }

    static void setBackground(Context c, boolean on) {
        prefs(c).edit().putBoolean("bg", on).apply();
        if (!on) cancelWatchdog(c);
    }

    private static final long WATCHDOG_EVERY = 5 * 60_000L;

    private static PendingIntent watchdog(Context c, int flags) {
        Intent i = new Intent(c, LiveAlarmReceiver.class).setAction(LiveAlarmReceiver.ACTION_WATCHDOG);
        return PendingIntent.getBroadcast(c, 899, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Background mode: an alarm {@code delay} ms from now that makes sure the live card is running while a game is on
     * (it restarts the service if Android or the phone's battery saver stopped it). Re-arms itself every 5 minutes
     * until the game's window ends.
     */
    static void armWatchdog(Context c, long delay) {
        if (!background(c)) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + Math.max(1_000L, delay);
        PendingIntent pi = watchdog(c, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Exception ignored) {
        }
    }

    static void armWatchdog(Context c) {
        armWatchdog(c, WATCHDOG_EVERY);
    }

    static void cancelWatchdog(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = watchdog(c, PendingIntent.FLAG_NO_CREATE);
        if (am != null && pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    /** The app's details of the game at {@code t}: {t, c, eg} (empty if unknown). */
    static org.json.JSONObject game(Context c, long t) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs(c).getString("games", "[]"));
            org.json.JSONObject best = null;
            long bd = 6 * 3_600_000L;
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject g = a.optJSONObject(i);
                if (g == null) continue;
                long d = Math.abs(g.optLong("t", 0) - t);
                if (d < bd) {
                    bd = d;
                    best = g;
                }
            }
            if (best != null) return best;
        } catch (Exception ignored) {
        }
        return new org.json.JSONObject();
    }

    /** Hapoel's players {shirt number: Hebrew name}. */
    static java.util.Map<String, String> roster(Context c) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        try {
            org.json.JSONObject o = new org.json.JSONObject(prefs(c).getString("jer", "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                m.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) {
        }
        return m;
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

    /** The next games as saved (for the home-screen widget). */
    static String gamesJson(Context c) {
        return prefs(c).getString("games", "[]");
    }

    static void dismissed(Context c, long t) {
        if (t > 0) done(c, t);
    }

    /**
     * Opening the app during a game brings the live card back, even if it was swiped away (or stopped) earlier in that
     * game: "done" only keeps it away until then. A game that has really ended is not in the app's list any more.
     */
    static void undone(Context c) {
        prefs(c).edit().remove("done").apply();
    }

    /** For the app's notification test screen: why the live card is or isn't running now. */
    static org.json.JSONObject state(Context c) {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            long now = System.currentTimeMillis(), next = 0;
            for (long t : times(c)) if (t > now - WINDOW_AFTER && (next == 0 || t < next)) next = t;
            o.put("on", prefs(c).getBoolean("on", true));
            o.put("notif", NotificationManagerCompat.from(c).areNotificationsEnabled());
            o.put("current", current(c));
            o.put("next", next);
            o.put("done", prefs(c).getLong("done", 0));
            o.put("games", times(c).size());
            o.put("bg", background(c));
            o.put("nb", nowBar(c));
        } catch (Exception ignored) {
        }
        return o;
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
        if (!prefs(c).getBoolean("on", true)) return;
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
