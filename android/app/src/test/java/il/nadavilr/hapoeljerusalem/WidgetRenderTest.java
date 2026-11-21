package il.nadavilr.hapoeljerusalem;

import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import org.robolectric.RuntimeEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Renders both home-screen widgets in every state (countdown, started, no game, live with a clock, at a break, in
 * overtime, three-digit scores), with short and long names, at every size the launcher can give them – and fails if
 * any text is cut: ellipsized, wider or taller than its view, or outside the widget. The renders are written to
 * build/widget-renders for a look.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetRenderTest {

    private static final String OPP_URL = "https://example.invalid/opp.png";
    private final List<String> problems = new ArrayList<>();
    private String prefix = "";
    private Context c;
    private File out;

    private static final String[][] TEAMS = {{"מכבי אשדוד", "ליגת ווינר, מחזור 7"}, {"צדביטה אולימפיה בלגרד", "יורוקאפ – בית א', מחזור 10"},
        {"עירוני נס ציונה", "גביע ווינר – חצי גמר"}};

    @Test
    public void widgetsNeverCutText() throws Exception {
        c = RuntimeEnvironment.getApplication();
        out = new File("build/widget-renders");
        out.mkdirs();
        // the opponent's crest, as if it had been downloaded before
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("opp.png")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            File f = new File(c.getFilesDir(), "wcrest_" + Integer.toHexString(OPP_URL.hashCode()) + ".png");
            try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        }
        long now = System.currentTimeMillis();

        // clock widget: 5×2, 4×2, 3×2, bigger and shorter
        int[][] clock = {{362, 195}, {290, 195}, {215, 195}, {440, 240}, {362, 170}, {300, 160}, {215, 150}};
        // next-game widget: 5×1, 4×1, bigger, low; landscape: wide and low
        int[][] row = {{362, 88}, {290, 88}, {250, 70}, {440, 100}, {362, 60}};
        int[][] land = {{700, 60}, {600, 50}, {800, 72}};
        for (int ti = 0; ti < TEAMS.length; ti++) {
            for (int home = 0; home < 2; home++) {
                String opp = TEAMS[ti][0], comp = TEAMS[ti][1];
                String tag = "t" + ti + (home == 1 ? "h" : "a");
                // --- countdown, started, no game
                JSONObject g = new JSONObject().put("t", now + 2 * 86_400_000L + 3_723_000L).put("o", opp).put("l", comp).put("h", home).put("img", OPP_URL).put("c", "league");
                JSONObject started = new JSONObject(g.toString()).put("t", now - 600_000L);
                NextGameWidget.clearLive(c);
                for (int[] s : clock) {
                    clockCase("clock-count-" + tag, s, now, g);
                    clockCase("clock-started-" + tag, s, now, started);
                    if (ti == 0 && home == 1) clockCase("clock-none", s, now, null);
                }
                for (int[] s : row) {
                    rowCase("row-count-" + tag, s, false, model(g, false, 0, 0, "", ""));
                    rowCase("row-started-" + tag, s, false, model(started, false, 0, 0, "", ""));
                    if (ti == 0 && home == 1) rowCase("row-none", s, false, null);
                }
                for (int[] s : land) rowCase("land-count-" + tag, s, true, model(g, false, 0, 0, "", ""));
                // --- live: a quarter with a clock, a break, overtime with three-digit scores
                String[][] st = {{"רבע 3", "04:12", "47", "41"}, {"מחצית", "", "38", "40"}, {"הארכה 2", "04:59", "112", "108"}, {"סיום רבע 1", "", "9", "100"}};
                for (int k = 0; k < st.length; k++) {
                    int hs = Integer.parseInt(st[k][2]), as = Integer.parseInt(st[k][3]);
                    LiveGame x = new LiveGame();
                    x.t = started.optLong("t");
                    x.meHome = home == 1;
                    x.homeName = home == 1 ? LiveGame.TEAM : opp;
                    x.awayName = home == 1 ? opp : LiveGame.TEAM;
                    x.homeImg = home == 1 ? "" : OPP_URL;
                    x.awayImg = home == 1 ? OPP_URL : "";
                    x.hs = hs;
                    x.as = as;
                    x.status = st[k][0];
                    x.clock = st[k][1];
                    x.comp = comp;
                    NextGameWidget.live(c, x);
                    for (int[] s : clock) clockCase("clock-live" + k + "-" + tag, s, now, started);
                    for (int[] s : row) rowCase("row-live" + k + "-" + tag, s, false, model(started, true, hs, as, st[k][0], st[k][1]));
                    for (int[] s : land) rowCase("land-live" + k + "-" + tag, s, true, model(started, true, hs, as, st[k][0], st[k][1]));
                }
                NextGameWidget.clearLive(c);
            }
        }
        try (FileOutputStream o = new FileOutputStream(new File(out, "clock-sizes.txt"))) { o.write(String.join("\n", sizes).getBytes("UTF-8")); }
        File report = new File(out, "problems.txt");
        try (FileOutputStream o = new FileOutputStream(report)) { o.write(String.join("\n", problems).getBytes("UTF-8")); }
        assertTrue(problems.size() + " problems – see " + report.getAbsolutePath() + "\n" + String.join("\n", problems.subList(0, Math.min(40, problems.size()))), problems.isEmpty());
    }

    // ---------- the clock widget ----------

    private void clockCase(String name, int[] size, long now, JSONObject g) throws Exception {
        Method build = ClockWidget.class.getDeclaredMethod("build", Context.class, long.class, JSONObject.class);
        build.setAccessible(true);
        RemoteViews v = (RemoteViews) build.invoke(null, c, now, g);
        Method fit = ClockWidget.class.getDeclaredMethod("fit", Context.class, RemoteViews.class, Bundle.class, String.class, String.class, String.class);
        fit.setAccessible(true);
        fit.invoke(null, c, v, opts(size[0], size[1], size[0], size[1]), "יום רביעי", "18.11.2026", "ח' בכסלו ה'תשפ\"ז");
        render(prefix + name + "-" + size[0] + "x" + size[1], v, size[0], size[1]);
    }

    // ---------- the next-game widget ----------

    private Object model(JSONObject g, boolean live, int hs, int as, String status, String clock) throws Exception {
        Class<?> m = Class.forName("il.nadavilr.hapoeljerusalem.NextGameWidget$Model");
        Constructor<?> k = m.getDeclaredConstructor();
        k.setAccessible(true);
        Object d = k.newInstance();
        boolean home = g.optInt("h", 1) == 1;
        set(d, "t", g.optLong("t"));
        set(d, "meHome", home);
        set(d, "live", live);
        set(d, "started", live || g.optLong("t") <= System.currentTimeMillis());
        set(d, "home", home ? LiveGame.TEAM : g.optString("o"));
        set(d, "away", home ? g.optString("o") : LiveGame.TEAM);
        set(d, "homeImg", home ? "" : OPP_URL);
        set(d, "awayImg", home ? OPP_URL : "");
        set(d, "comp", g.optString("l"));
        set(d, "hs", hs);
        set(d, "as", as);
        set(d, "status", status);
        set(d, "clock", clock);
        return d;
    }

    private static void set(Object o, String f, Object val) throws Exception {
        Field x = o.getClass().getDeclaredField(f);
        x.setAccessible(true);
        x.set(o, val);
    }

    private void rowCase(String name, int[] size, boolean land, Object d) throws Exception {
        Class<?> m = Class.forName("il.nadavilr.hapoeljerusalem.NextGameWidget$Model");
        Bitmap[] cr = new Bitmap[2];
        if (d != null) {
            Method crests = NextGameWidget.class.getDeclaredMethod("crests", Context.class, m);
            crests.setAccessible(true);
            cr = (Bitmap[]) crests.invoke(null, c, d);
        }
        Method row = NextGameWidget.class.getDeclaredMethod("row", Context.class, m, Bitmap[].class, boolean.class);
        row.setAccessible(true);
        RemoteViews v = (RemoteViews) row.invoke(null, c, d, cr, land);
        Method fit = NextGameWidget.class.getDeclaredMethod("fitRow", Context.class, RemoteViews.class, Bundle.class, boolean.class, m);
        fit.setAccessible(true);
        Field live = m.getDeclaredField("live");
        live.setAccessible(true);
        boolean isLive = d != null && live.getBoolean(d);
        // portrait: min width × max height; landscape: max width × min height (as the launcher reports them)
        Bundle o = land ? opts(100, 100, size[0], size[1]) : opts(size[0], size[1], 100, 100);
        if (land) { o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size[1]); o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size[0]); }
        else { o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size[0]); o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size[1]); }
        fit.invoke(null, c, v, o, land, d);
        render(prefix + name + "-" + size[0] + "x" + size[1], v, size[0], size[1]);
    }

    private static Bundle opts(int minW, int minH, int maxW, int maxH) {
        Bundle o = new Bundle();
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minW);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minH);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxW);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxH);
        return o;
    }

    // ---------- rendering and checking ----------

    private void render(String name, RemoteViews rv, int wDp, int hDp) throws Exception {
        float px = c.getResources().getDisplayMetrics().density;
        int w = Math.round(wDp * px), h = Math.round(hDp * px);
        FrameLayout host = new FrameLayout(c);
        View v = rv.apply(c, host);
        host.addView(v, new FrameLayout.LayoutParams(w, h));
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(Color.rgb(90, 110, 140));
        host.draw(cv);
        try (FileOutputStream o = new FileOutputStream(new File(out, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        check(name, v, v, px);
        if (name.startsWith("clock-count")) {
            float[] op = ink(v.findViewById(R.id.k_op)), cp = ink(v.findViewById(R.id.k_comp)), wd = ink(v.findViewById(R.id.k_wd)), wt = ink(v.findViewById(R.id.k_wt));
            if (op != null && cp != null && wd != null && wt != null)
                sizes.add(name + " gaps: competition→stage=" + Math.round((cp[0] - op[1]) / px * 10) / 10f + "dp day→time=" + Math.round((wt[0] - wd[1]) / px * 10) / 10f + "dp");
        }
        View card = v.findViewById(R.id.k_card);
        if (card != null) sizes.add(name + " card=" + Math.round(card.getHeight() / px * 10) / 10f + "dp");
        View clock = v.findViewById(R.id.k_time);
        if (clock instanceof TextView) sizes.add(name + " clock=" + Math.round(((TextView) clock).getTextSize() / px * 10) / 10f + "dp");
    }

    private final List<String> sizes = new ArrayList<>();

    /** A one-line text's ink: its top and bottom in the widget (px). */
    private static float[] ink(View v) {
        if (!(v instanceof TextView) || v.getVisibility() != View.VISIBLE) return null;
        TextView t = (TextView) v;
        Layout l = t.getLayout();
        if (l == null) return null;
        String s = t.getText().toString();
        Rect r = new Rect();
        t.getPaint().getTextBounds(s, 0, s.length(), r);
        float y = 0;
        for (View p = t; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) y += p.getTop();
        float base = y + t.getTotalPaddingTop() + l.getLineBaseline(0);
        return new float[] {base + r.top, base + r.bottom};
    }

    private void check(String name, View root, View v, float px) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof Chronometer) return; // the countdown's minutes / seconds are clipped on purpose to two digits
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) check(name, root, g.getChildAt(i), px);
        }
        if (!(v instanceof TextView)) return;
        TextView t = (TextView) v;
        CharSequence s = t.getText();
        if (s == null || s.toString().trim().isEmpty()) return;
        String id = v.getId() != View.NO_ID ? c.getResources().getResourceEntryName(v.getId()) : "?";
        String what = name + " " + id + " \"" + s.toString().replace("\n", "⏎").replace("‏", "") + "\"";
        Layout l = t.getLayout();
        if (l == null) { problems.add(what + ": no layout"); return; }
        for (int i = 0; i < l.getLineCount(); i++) if (l.getEllipsisCount(i) > 0) problems.add(what + ": ellipsized");
        int availW = t.getWidth() - t.getTotalPaddingLeft() - t.getTotalPaddingRight();
        int availH = t.getHeight() - t.getTotalPaddingTop() - t.getTotalPaddingBottom();
        float lw = 0;
        for (int i = 0; i < l.getLineCount(); i++) lw = Math.max(lw, l.getLineMax(i));
        if (lw > availW + 1) problems.add(what + ": wider than its view (" + Math.round(lw / px) + " > " + Math.round(availW / px) + " dp)");
        if (l.getHeight() > availH + 1) problems.add(what + ": taller than its view (" + Math.round(l.getHeight() / px) + " > " + Math.round(availH / px) + " dp)");
        if (t.getMaxLines() > 0 && l.getLineCount() > t.getMaxLines()) problems.add(what + ": more lines than allowed");
        // the text's ink (the glyphs themselves, not the view's empty room above and below them) inside the widget and
        // inside every parent – nothing cut off by a smaller parent
        Rect r = new Rect();
        if (!t.getGlobalVisibleRect(r)) { problems.add(what + ": not visible"); return; }
        // the view's position in the same coordinates as getGlobalVisibleRect (the views are not in a window here)
        int[] at = new int[2];
        for (View p = t; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) { at[0] += p.getLeft() - p.getScrollX(); at[1] += p.getTop() - p.getScrollY(); }
        for (int i = 0; i < l.getLineCount(); i++) {
            String line = s.subSequence(l.getLineStart(i), l.getLineEnd(i)).toString().replace("\n", "");
            if (line.trim().isEmpty()) continue;
            Rect ink = new Rect();
            t.getPaint().getTextBounds(line, 0, line.length(), ink);
            int top = at[1] + t.getTotalPaddingTop() + l.getLineBaseline(i) + ink.top, bottom = at[1] + t.getTotalPaddingTop() + l.getLineBaseline(i) + ink.bottom;
            if (top < r.top - 1 || bottom > r.bottom + 1) // across: the width check above
                problems.add(what + ": ink cut (" + (r.top - top) + "/" + (bottom - r.bottom) + " px over top/bottom)");
        }
    }
}
