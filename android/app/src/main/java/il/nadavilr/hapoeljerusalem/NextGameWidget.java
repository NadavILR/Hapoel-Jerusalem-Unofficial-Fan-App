package il.nadavilr.hapoeljerusalem;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home-screen widget in the style of the app's next-game card: the next game with a countdown (days, and a ticking
 * hours:minutes:seconds), and while the game is on – the score live (from the live score service). It adapts to its
 * size: 1×2 (narrow), 2×2, 2×3, 2×4 and a wide strip (5×2), each with its own layout. Home team on the right.
 * Tapping it opens the game's page in the app.
 */
public class NextGameWidget extends AppWidgetProvider {

    private static final String PREFS = "hpj-widget";
    private static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;
    /** The live snapshot is used while it is fresh (the service updates it every few seconds during a game). */
    private static final long LIVE_FRESH = 10 * MIN;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        async(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        async(c);
    }

    private void async(Context c) {
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                update(app);
            } finally {
                pr.finish();
            }
        }).start();
    }

    /** Refresh the widgets from any thread (the app's sync, the live score service). */
    static void refresh(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> update(app)).start();
    }

    // ---------- the live game (from LiveScoreService) ----------

    static void live(Context c, LiveGame x) {
        try {
            JSONObject o = new JSONObject().put("t", x.t).put("hs", x.hs).put("as", x.as).put("home", x.homeName)
                .put("away", x.awayName).put("status", x.status).put("clock", x.clock).put("comp", x.comp)
                .put("homeImg", x.homeImg == null ? "" : x.homeImg).put("awayImg", x.awayImg == null ? "" : x.awayImg)
                .put("meHome", x.meHome).put("ts", System.currentTimeMillis());
            prefs(c).edit().putString("live", o.toString()).apply();
        } catch (Exception ignored) {
        }
        if (has(c)) update(c);
    }

    static void clearLive(Context c) {
        prefs(c).edit().remove("live").apply();
        if (has(c)) refresh(c);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean has(Context c) {
        try {
            return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, NextGameWidget.class)).length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- building the widget ----------

    private static final int TINY = 0, SMALL = 1, TALL = 2, XTALL = 3, WIDE = 4, ROW2 = 5, ROW = 6, COL = 7, COLX = 8;
    private static final int[] LAYOUTS = {R.layout.widget_tiny, R.layout.widget_small, R.layout.widget_tall,
        R.layout.widget_xtall, R.layout.widget_wide, R.layout.widget_row2, R.layout.widget_row, R.layout.widget_col,
        R.layout.widget_colx};
    /** The smallest size (dp) for each layout; Android picks the biggest one that fits the widget. */
    private static final float[][] SIZES = {{40f, 110f}, {100f, 110f}, {100f, 160f}, {100f, 230f}, {230f, 120f},
        {100f, 40f}, {170f, 40f}, {40f, 160f}, {40f, 250f}};

    static synchronized void update(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids;
        try {
            ids = m.getAppWidgetIds(new ComponentName(c, NextGameWidget.class));
        } catch (Exception e) {
            return;
        }
        if (ids == null || ids.length == 0) return;
        Model d = model(c);
        Bitmap[] crests = d != null ? crests(c, d) : new Bitmap[2];
        for (int id : ids) {
            try {
                RemoteViews v;
                if (Build.VERSION.SDK_INT >= 31) {
                    // Android picks the biggest layout that fits the widget's size
                    Map<SizeF, RemoteViews> map = new HashMap<>();
                    for (int k = 0; k < LAYOUTS.length; k++) map.put(new SizeF(SIZES[k][0], SIZES[k][1]), build(c, k, d, crests));
                    v = new RemoteViews(map);
                } else {
                    v = build(c, pick(m.getAppWidgetOptions(id)), d, crests);
                }
                m.updateAppWidget(id, v);
            } catch (Exception ignored) {
            }
        }
        schedule(c, d);
    }

    /** Before Android 12: the layout by the widget's size (portrait width, tallest height). */
    private static int pick(Bundle o) {
        int w = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) : 110;
        int h = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110) : 110;
        // the biggest layout (by area) whose smallest size fits, as Android 12+ does
        int best = SMALL;
        float area = -1;
        for (int k = 0; k < SIZES.length; k++) {
            if (SIZES[k][0] <= w && SIZES[k][1] <= h && SIZES[k][0] * SIZES[k][1] > area) {
                area = SIZES[k][0] * SIZES[k][1];
                best = k;
            }
        }
        return best;
    }

    /** What the widget shows: the game (next or live). */
    private static final class Model {
        long t;
        boolean meHome = true, live, started;
        String home = LiveGame.TEAM, away = "", comp = "", venue = "", homeImg, awayImg, status = "", clock = "";
        int hs, as;
    }

    private static Model model(Context c) {
        long now = System.currentTimeMillis();
        // A game on now (fresh snapshot from the live score service)
        try {
            JSONObject l = new JSONObject(prefs(c).getString("live", "{}"));
            if (l.has("t") && now - l.optLong("ts") < LIVE_FRESH) {
                Model d = new Model();
                d.t = l.optLong("t");
                d.live = d.started = true;
                d.home = l.optString("home");
                d.away = l.optString("away");
                d.hs = l.optInt("hs");
                d.as = l.optInt("as");
                d.status = l.optString("status");
                d.clock = l.optString("clock");
                d.comp = l.optString("comp");
                d.homeImg = l.optString("homeImg", "");
                d.awayImg = l.optString("awayImg", "");
                d.meHome = l.optBoolean("meHome", true);
                return d;
            }
        } catch (Exception ignored) {
        }
        // The next game from the app's list (kept until 3 hours after its tip-off)
        try {
            JSONArray a = new JSONArray(LiveScheduler.gamesJson(c));
            JSONObject best = null;
            for (int i = 0; i < a.length(); i++) {
                JSONObject g = a.optJSONObject(i);
                if (g == null || g.optLong("t") <= 0 || g.optLong("t") < now - 3 * HOUR) continue;
                if (best == null || g.optLong("t") < best.optLong("t")) best = g;
            }
            if (best == null) return null;
            Model d = new Model();
            d.t = best.optLong("t");
            d.started = now >= d.t;
            d.meHome = best.optInt("h", 1) == 1;
            String opp = best.optString("o", "");
            d.home = d.meHome ? LiveGame.TEAM : opp;
            d.away = d.meHome ? opp : LiveGame.TEAM;
            String img = best.optString("img", "");
            d.homeImg = d.meHome ? "" : img;
            d.awayImg = d.meHome ? img : "";
            d.comp = best.optString("l", "");
            d.venue = best.optString("v", "");
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private static RemoteViews build(Context c, int size, Model d, Bitmap[] cr) {
        RemoteViews v = new RemoteViews(c.getPackageName(), LAYOUTS[size]);
        if (d == null) {
            v.setTextViewText(R.id.w_mid, "");
            v.setTextViewText(R.id.w_comp, "");
            v.setTextViewText(R.id.w_names, "אין משחקים קרובים");
            v.setTextViewText(R.id.w_status, "אין משחקים קרובים");
            v.setViewVisibility(R.id.w_count, View.GONE);
            v.setViewVisibility(R.id.w_chrono, View.GONE);
            v.setViewVisibility(R.id.w_days, View.GONE);
            v.setOnClickPendingIntent(R.id.w_root, LiveGame.open(c, 0));
            return v;
        }
        String rlm = "‏";
        if (cr[0] != null) v.setImageViewBitmap(R.id.w_home_crest, cr[0]);
        if (cr[1] != null) v.setImageViewBitmap(R.id.w_away_crest, cr[1]);
        Bitmap opp = d.meHome ? cr[1] : cr[0];
        if (opp != null) v.setImageViewBitmap(R.id.w_opp_crest, opp);
        v.setContentDescription(R.id.w_home_crest, d.home);
        v.setContentDescription(R.id.w_away_crest, d.away);
        v.setTextViewText(R.id.w_comp, d.comp.isEmpty() ? "" : rlm + d.comp);
        v.setViewVisibility(R.id.w_comp, d.comp.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.w_names, rlm + d.home + " – " + d.away);
        v.setTextViewText(R.id.w_venue, d.venue.isEmpty() ? "" : rlm + d.venue);
        v.setViewVisibility(R.id.w_venue, d.venue.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.w_when, rlm + when(d.t));
        if (d.live) {
            // Live: the score (home on the right), the LIVE pill, the quarter and clock
            v.setTextViewText(R.id.w_mid, rlm + d.hs + " – " + d.as);
            v.setViewVisibility(R.id.w_live, View.VISIBLE);
            String st = d.status + (d.clock.isEmpty() ? "" : (d.status.isEmpty() ? "" : " • ") + d.clock);
            v.setTextViewText(R.id.w_status, rlm + (st.isEmpty() ? "בלייב" : st));
            v.setViewVisibility(R.id.w_status, View.VISIBLE);
            v.setViewVisibility(R.id.w_when, View.GONE);
            v.setViewVisibility(R.id.w_count, View.GONE);
            v.setViewVisibility(R.id.w_chrono, View.GONE);
            v.setViewVisibility(R.id.w_days, View.GONE);
        } else if (d.started) {
            // Tip-off has passed and the live service has no score (yet): open the app for the score
            v.setTextViewText(R.id.w_mid, "VS");
            v.setViewVisibility(R.id.w_live, View.VISIBLE);
            v.setTextViewText(R.id.w_status, rlm + "המשחק התחיל");
            v.setViewVisibility(R.id.w_status, View.VISIBLE);
            v.setViewVisibility(R.id.w_count, View.GONE);
            v.setViewVisibility(R.id.w_chrono, View.GONE);
            v.setViewVisibility(R.id.w_days, View.GONE);
        } else {
            // The countdown: whole days as text, the rest ticking (Chronometer counting down)
            long left = d.t - System.currentTimeMillis(), days = left / DAY, rest = left - days * DAY;
            v.setTextViewText(R.id.w_mid, "VS");
            v.setViewVisibility(R.id.w_live, View.GONE);
            v.setViewVisibility(R.id.w_status, View.GONE);
            v.setViewVisibility(R.id.w_count, View.VISIBLE);
            boolean boxed = size == TALL || size == XTALL;
            if (boxed) {
                v.setTextViewText(R.id.w_days, String.valueOf(days));
                v.setViewVisibility(R.id.w_days, View.VISIBLE);
            } else {
                v.setTextViewText(R.id.w_days, days <= 0 ? "" : days == 1 ? "יום ו-" : days + " ימים ו-");
                v.setViewVisibility(R.id.w_days, days <= 0 ? View.GONE : View.VISIBLE);
            }
            v.setViewVisibility(R.id.w_chrono, View.VISIBLE);
            v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + rest, null, true);
            if (Build.VERSION.SDK_INT >= 24) v.setChronometerCountDown(R.id.w_chrono, true);
        }
        v.setOnClickPendingIntent(R.id.w_root, LiveGame.open(c, d.t));
        return v;
    }

    private static String when(long t) {
        SimpleDateFormat f = new SimpleDateFormat("EEEE · d.M · HH:mm", new Locale("he"));
        f.setTimeZone(TimeZone.getTimeZone("Asia/Jerusalem"));
        return f.format(new Date(t));
    }

    // ---------- crests: Hapoel's from the app, the opponent's from 365scores (kept as a file for the widget) ----------

    private static Bitmap[] crests(Context c, Model d) {
        Bitmap me = LiveGame.hapoelCrest(c);
        Bitmap h = d.meHome ? me : crest(c, d.homeImg), a = d.meHome ? crest(c, d.awayImg) : me;
        return new Bitmap[] {h, a};
    }

    private static Bitmap crest(Context c, String url) {
        if (url == null || url.isEmpty()) return null;
        File f = new File(c.getFilesDir(), "wcrest_" + Integer.toHexString(url.hashCode()) + ".png");
        try {
            if (f.exists()) {
                Bitmap b = BitmapFactory.decodeFile(f.getPath());
                if (b != null) return b;
            }
            Bitmap b = LiveGame.crest(url);
            if (b != null) {
                try (FileOutputStream o = new FileOutputStream(f)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, o);
                }
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- the next refresh: the day changes in the countdown, tip-off, and every 30 minutes ----------

    private static void schedule(Context c, Model d) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis(), at = now + 30 * MIN;
        if (d != null && !d.live) {
            long left = d.t - now;
            if (left > 0) {
                long rest = left % DAY;
                at = Math.min(at, now + (rest > 0 ? rest : DAY) + 1_000L); // the day count goes down
                at = Math.min(at, d.t + 1_000L);
            }
        }
        Intent i = new Intent(c, NextGameWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        try {
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
                AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, NextGameWidget.class)));
        } catch (Exception ignored) {
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, 7301, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi);
            else am.set(AlarmManager.RTC, at, pi);
        } catch (Exception ignored) {
        }
    }
}
