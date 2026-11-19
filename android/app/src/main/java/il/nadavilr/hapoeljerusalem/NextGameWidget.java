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
        ClockWidget.refresh(app); // the clock widget shows the next game too
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

    private static long drawnMinute = -1, nextT;
    private static boolean countdown;

    /** A widget with a countdown on it needs WidgetTick (its seconds). */
    static boolean ticking(Context c) {
        return countdown && has(c);
    }

    /** WidgetTick: a new minute – redraw; otherwise put the countdown's seconds on the right second. */
    static synchronized void tick(Context c) {
        if (!has(c)) return;
        long now = System.currentTimeMillis();
        if (now / MIN != drawnMinute) { update(c); return; }
        if (!countdown || nextT <= now) return;
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, NextGameWidget.class));
            int ss = 59 - (int) (((nextT - now) / 1000) % 60);
            RemoteViews port = new RemoteViews(c.getPackageName(), R.layout.widget_row), land = new RemoteViews(c.getPackageName(), R.layout.widget_row_land);
            port.setDisplayedChild(R.id.w_ss, ss);
            land.setDisplayedChild(R.id.w_ss, ss);
            m.partiallyUpdateAppWidget(ids, new RemoteViews(land, port));
        } catch (Exception ignored) {
        }
    }

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
                // one row, five columns by default; lying down (landscape) it gets its own size – Android picks the views
                // for the current orientation. Every text is drawn in Noto Sans Hebrew at the widget's real size
                Bundle o = m.getAppWidgetOptions(id);
                m.updateAppWidget(id, new RemoteViews(row(c, d, crests, true, o), row(c, d, crests, false, o)));
            } catch (Exception ignored) {
            }
        }
        drawnMinute = System.currentTimeMillis() / MIN;
        countdown = d != null && !d.live && !d.started;
        nextT = d != null ? d.t : 0;
        schedule(c, d);
        WidgetTick.schedule(c);
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
                /* the opponent's crest, if the live snapshot has none: the one in the app's game list */
                String oi = LiveScheduler.game(c, d.t).optString("img", "");
                if (!oi.isEmpty()) { if (d.meHome) { if (d.awayImg.isEmpty()) d.awayImg = oi; } else if (d.homeImg.isEmpty()) d.homeImg = oi; }
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
        if (size == ROW || size == WIDE) return row(c, d, cr);
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
            if (boxed || size == ROW) {
                v.setTextViewText(R.id.w_days, String.valueOf(days));
                v.setViewVisibility(R.id.w_days, View.VISIBLE);
                // no whole days left: only the ticking box
                v.setViewVisibility(R.id.w_days_box, size == ROW && days <= 0 ? View.GONE : View.VISIBLE);
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

    /** One row and the wide strip – the user's design: the countdown in four boxes, or the live score. */
    private static RemoteViews row(Context c, Model d, Bitmap[] cr) { return row(c, d, cr, false, null); }

    /** The row's design base size (dp): five columns × one row on a Galaxy S24 Ultra. */
    private static final float RW = 362f, RH = 88f;
    private static final int WHITE = 0xFFFFFFFF, MUTED = 0xBFFFFFFF, LABEL = 0xB3FFFFFF;

    /** One row (standing or lying down), drawn at the widget's real size. */
    private static RemoteViews row(Context c, Model d, Bitmap[] cr, boolean land, Bundle o) {
        final int DP = android.util.TypedValue.COMPLEX_UNIT_DIP;
        String rlm = "‏";
        boolean live = d != null && d.live, resize = Build.VERSION.SDK_INT >= 31;
        float[] sz = resize ? ClockWidget.size(o, RW, RH, land) : new float[] {RW, RH};
        float w = sz[0], h = sz[1];
        // k: sizes, text and widths; kh: what may grow with the height (the crests, the countdown boxes, the score)
        float k = resize ? Math.min(1.6f, Math.min(w / RW, h / RH)) : 1f, kh = resize ? Math.min(1.6f, Math.min(h / RH, k * 1.3f)) : 1f;
        float px = c.getResources().getDisplayMetrics().density;
        RemoteViews v = new RemoteViews(c.getPackageName(), live ? (land ? R.layout.widget_row_live_land : R.layout.widget_row_live) : (land ? R.layout.widget_row_land : R.layout.widget_row));
        float club = 54 * k, pad = 10 * k, mainW = w - club - 2 * pad;
        if (resize) {
            v.setViewLayoutWidth(R.id.w_club, club, DP);
            float ci = Math.min(38 * kh, club - 10 * k);
            v.setViewLayoutWidth(R.id.w_clubimg, ci, DP);
            v.setViewLayoutHeight(R.id.w_clubimg, ci, DP);
            int p = Math.round(pad * px);
            v.setViewPadding(R.id.w_main, p, 0, p, 0);
        }
        float small = 9 * k;
        if (live) {
            v.setOnClickPendingIntent(R.id.w_root, LiveGame.open(c, d.t));
            /* no crest for the opponent: an empty space, never Hapoel's crest on both sides */
            if (cr[0] != null) v.setImageViewBitmap(R.id.w_home_crest, cr[0]); else v.setImageViewResource(R.id.w_home_crest, android.R.color.transparent);
            if (cr[1] != null) v.setImageViewBitmap(R.id.w_away_crest, cr[1]); else v.setImageViewResource(R.id.w_away_crest, android.R.color.transparent);
            v.setContentDescription(R.id.w_home_crest, d.home);
            v.setContentDescription(R.id.w_away_crest, d.away);
            float crest = 26 * kh, score = 24 * Math.min(kh, k * 1.15f);
            if (resize) {
                for (int id : new int[] {R.id.w_home_crest, R.id.w_away_crest}) { v.setViewLayoutWidth(id, crest, DP); v.setViewLayoutHeight(id, crest, DP); }
                v.setViewLayoutMargin(R.id.w_away_crest, RemoteViews.MARGIN_START, 6 * k, DP);
                v.setViewLayoutMargin(R.id.w_hs, RemoteViews.MARGIN_START, 6 * k, DP);
                v.setViewLayoutMargin(R.id.w_dash, RemoteViews.MARGIN_START, 6 * k, DP);
                v.setViewLayoutMargin(R.id.w_dash, RemoteViews.MARGIN_END, 6 * k, DP);
                v.setViewPadding(R.id.w_tag, Math.round(8 * k * px), Math.round(2 * k * px), Math.round(8 * k * px), Math.round(2 * k * px));
                v.setViewLayoutMargin(R.id.w_clock, RemoteViews.MARGIN_START, 6 * k, DP);
                v.setViewLayoutMargin(R.id.w_comp, RemoteViews.MARGIN_TOP, 4 * k, DP);
            }
            String hs = String.valueOf(d.hs), as = String.valueOf(d.as);
            NotoText.set(c, v, R.id.w_hs, hs, score, true, WHITE, 0);
            NotoText.set(c, v, R.id.w_dash, "–", 18 * k, false, 0x66FFFFFF, 0);
            NotoText.set(c, v, R.id.w_as, as, score, true, WHITE, 0);
            float scoreW = 2 * crest + 24 * k + NotoText.widthDp(c, hs, score, true) + NotoText.widthDp(c, as, score, true) + NotoText.widthDp(c, "–", 18 * k, false);
            float textW = mainW - 8 * k - scoreW;
            NotoText.set(c, v, R.id.w_tagtxt, "● לייב", 9.5f * k, true, WHITE, 0);
            // the quarter and the game clock; at a break ("סיום רבע 1", "מחצית") the status itself
            String st = d.status.isEmpty() ? "בלייב" : d.status;
            String clk = rlm + st + (d.clock.isEmpty() ? "" : " · ⁦" + d.clock + "⁩");
            NotoText.set(c, v, R.id.w_clock, clk, 11.5f * k, true, WHITE, textW - NotoText.widthDp(c, "● לייב", 9.5f * k, true) - 22 * k);
            String comp = ClockWidget.shortComp(d.comp);
            NotoText.set(c, v, R.id.w_comp, rlm + (comp.isEmpty() ? d.home + " – " + d.away : comp), small, false, MUTED, textW);
            return v;
        }
        if (resize) {
            v.setViewLayoutWidth(R.id.w_crests, 30 * k, DP);
            v.setViewLayoutHeight(R.id.w_crests, 18 * k, DP);
            for (int id : new int[] {R.id.w_me, R.id.w_opp}) { v.setViewLayoutWidth(id, 18 * k, DP); v.setViewLayoutHeight(id, 18 * k, DP); }
            v.setViewLayoutMargin(R.id.w_op, RemoteViews.MARGIN_START, 6 * k, DP);
            v.setViewLayoutMargin(R.id.w_comp, RemoteViews.MARGIN_TOP, 3 * k, DP);
            v.setViewLayoutMargin(R.id.w_when, RemoteViews.MARGIN_TOP, 2 * k, DP);
            v.setViewLayoutMargin(R.id.w_count, RemoteViews.MARGIN_START, 8 * k, DP);
        }
        // the countdown boxes: a little taller with a taller widget, never wider than the row allows
        float bw = 35 * k, num = Math.min(16 * kh, (bw - 6 * k) / 1.25f), countW = 4 * bw + 12 * k;
        float textW = mainW - 8 * k - countW;
        if (d == null) {
            v.setOnClickPendingIntent(R.id.w_root, LiveGame.open(c, 0));
            NotoText.set(c, v, R.id.w_op, rlm + "אין משחקים קרובים", 11.5f * k, true, WHITE, mainW);
            v.setViewVisibility(R.id.w_crests, View.GONE);
            v.setViewVisibility(R.id.w_comp, View.GONE);
            v.setViewVisibility(R.id.w_when, View.GONE);
            v.setViewVisibility(R.id.w_count, View.GONE);
            return v;
        }
        v.setOnClickPendingIntent(R.id.w_root, LiveGame.open(c, d.t));
        String opp = d.meHome ? d.away : d.home;
        Bitmap oc = d.meHome ? cr[1] : cr[0];
        if (oc != null) v.setImageViewBitmap(R.id.w_opp, oc); else v.setImageViewResource(R.id.w_opp, android.R.color.transparent);
        String comp = ClockWidget.shortComp(d.comp);
        if (d.started) textW = mainW - 8 * k - (NotoText.widthDp(c, "לחצו לתוצאה", 10.5f * k, true) + 20 * k);
        NotoText.set(c, v, R.id.w_op, rlm + opp, 11.5f * k, true, WHITE, textW - 36 * k);
        NotoText.set(c, v, R.id.w_comp, rlm + comp, small, false, MUTED, textW);
        v.setViewVisibility(R.id.w_comp, comp.isEmpty() ? View.GONE : View.VISIBLE);
        NotoText.set(c, v, R.id.w_when, rlm + when(d.t), small, false, MUTED, textW);
        if (d.started) {
            // Tip-off has passed and the live score card has no score (yet)
            v.setViewVisibility(R.id.w_count, View.GONE);
            v.setViewVisibility(R.id.w_statusbox, View.VISIBLE);
            NotoText.set(c, v, R.id.w_status1, "המשחק התחיל", 10.5f * k, true, WHITE, 0);
            NotoText.set(c, v, R.id.w_status2, "לחצו לתוצאה", 9 * k, false, MUTED, 0);
            return v;
        }
        long left = Math.max(0, d.t - System.currentTimeMillis()), secs = left / 1000;
        long[] val = {secs / 86400, (secs % 86400) / 3600, (secs % 3600) / 60};
        String[] lab = {"ימים", "שעות", "דקות", "שניות"};
        int[][] b = {{R.id.w_days_box, R.id.w_days, R.id.w_bl1}, {R.id.w_b2, R.id.w_hours, R.id.w_bl2}, {R.id.w_b3, R.id.w_mins, R.id.w_bl3}, {R.id.w_b4, 0, R.id.w_bl4}};
        for (int i = 0; i < b.length; i++) {
            if (resize) {
                v.setViewLayoutWidth(b[i][0], bw, DP);
                v.setViewPadding(b[i][0], 0, Math.round(4 * kh * px), 0, Math.round(5 * kh * px));
                if (i > 0) v.setViewLayoutMargin(b[i][0], RemoteViews.MARGIN_START, 4 * k, DP);
                v.setViewLayoutMargin(b[i][2], RemoteViews.MARGIN_TOP, k, DP);
            }
            if (b[i][1] != 0) NotoText.set(c, v, b[i][1], String.format(Locale.ROOT, "%02d", val[i]), num, true, WHITE, 0);
            NotoText.set(c, v, b[i][2], lab[i], 8 * k, false, LABEL, 0);
        }
        if (resize) {
            v.setViewLayoutWidth(R.id.w_ss, num * 1.164f, DP);
            v.setViewLayoutHeight(R.id.w_ss, num * 1.36f, DP);
        }
        v.setDisplayedChild(R.id.w_ss, 59 - (int) (secs % 60));
        return v;
    }

    /** The width of two digits in the countdown's font (22dp bold, tabular digits), so a clip shows just "MM" / "SS". */
    static float twoDigits(Context c, int sp) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sp * c.getResources().getDisplayMetrics().density);
        // the widget's own font (Noto Sans Hebrew Bold), so the clip is exactly two of its digits
        try { p.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c, R.font.noto_sans_hebrew_bold)); }
        catch (Exception e) { p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)); }
        if (Build.VERSION.SDK_INT >= 21) p.setFontFeatureSettings("tnum");
        return (float) Math.ceil(p.measureText("00")) + 1f;
    }

    private static String when(long t) {
        SimpleDateFormat f = new SimpleDateFormat("EEE d.M · HH:mm", new Locale("he"));
        f.setTimeZone(TimeZone.getTimeZone("Asia/Jerusalem"));
        return f.format(new Date(t));
    }

    // ---------- crests: Hapoel's from the app, the opponent's from 365scores (kept as a file for the widget) ----------

    private static Bitmap[] crests(Context c, Model d) {
        Bitmap me = LiveGame.hapoelCrest(c);
        Bitmap h = d.meHome ? me : crest(c, d.homeImg), a = d.meHome ? crest(c, d.awayImg) : me;
        return new Bitmap[] {h, a};
    }

    static Bitmap crest(Context c, String url) {
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
                long rest = left % HOUR;
                at = Math.min(at, now + (rest > 0 ? rest : HOUR) + 300L); // the hours (and days) go down – right at the turn
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
            // exact: the minutes and seconds count down by themselves and run past zero ("−00:17") if the redraw at the
            // turn of the hour comes late – an inexact alarm can be minutes late while the phone sleeps
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact && Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
            else if (exact) am.setExact(AlarmManager.RTC, at, pi);
            else if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi);
            else am.set(AlarmManager.RTC, at, pi);
        } catch (Exception ignored) {
        }
    }
}
