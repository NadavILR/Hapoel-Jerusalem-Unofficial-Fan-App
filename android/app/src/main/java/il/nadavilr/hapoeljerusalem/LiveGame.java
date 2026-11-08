package il.nadavilr.hapoeljerusalem;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/**
 * One live game, shown as the live score card (one ongoing notification, laid out like Google's sports card in the
 * app's colours) – one compact card that does not expand, without the app-name row:
 *
 *   [home crest]  home score  (quarter • clock)  away score  [away crest]
 *   **competition – stage / round**
 *   the latest line of the play-by-play
 *
 * Everything reads right to left (home team on the right) even on a phone set to English.
 * Tapping it opens the game's page in the app.
 */
final class LiveGame {

    static final int HAPOEL = 634;
    static final String TEAM = "הפועל ירושלים";
    /** Intent extra: the tip-off time of the game whose page to open. */
    static final String EXTRA_OPEN = "hpj_open_game";
    private static final String RLM = "‏";
    private static final String IMG = "https://imagecache.365scores.com/image/upload/f_png,w_96,h_96,c_limit,q_auto:eco,dpr_2,"
        + "d_Competitors:default1.png/v%d/Competitors/%d";

    String comp = "", homeName = "", awayName = "", status = "", clock = "", play = "", homeImg, awayImg;
    int hs, as;
    /** Points just scored (1–3), shown as +1 / +2 / +3 next to the score; 0 = none. */
    int popHome, popAway;
    boolean meHome = true;
    /** Scheduled tip-off (ms): identifies the game when the card is tapped. */
    long t;

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
        x.status = str(g, "statusText");
        if (x.status.isEmpty()) x.status = str(g, "shortStatusText");
        x.clock = str(g, "gameTimeDisplay"); // 365scores sends null between plays / at a break
        // 365scores puts the stage in the competition name ("ליגת ווינר - חצי גמר", "יורוקאפ - בית א'");
        // the round is "מחזור N", or "משחק N" in a playoff series; a one-game knockout has no round number.
        String comp = g.optString("competitionDisplayName", "").trim().replace(" - ", " – ");
        int round = g.optInt("roundNum", 0);
        String roundName = g.optString("roundName", "").trim();
        if (roundName.isEmpty() || "null".equals(roundName)) roundName = "מחזור";
        // Same format as in the app: "ליגת ווינר, מחזור 5", "ליגת ווינר – חצי גמר, משחק 3"
        x.comp = comp + (round > 0 ? (comp.isEmpty() ? "" : ", ") + roundName + " " + round : "");
        return x;
    }

    /** A text field, "" for a missing or JSON-null value (org.json would turn null into the text "null"). */
    static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        String v = o.optString(key, "").trim();
        return "null".equals(v) ? "" : v;
    }

    private static String name(JSONObject c) {
        String n = c.optString("name", "");
        return n.isEmpty() ? c.optString("shortName", "") : n;
    }

    private static String img(JSONObject c) {
        int id = c.optInt("id", 0);
        return id > 0 ? String.format(Locale.US, IMG, Math.max(1, c.optInt("imageVersion", 1)), id) : null;
    }

    /** Names and score, home on the right (the notification's text, e.g. for accessibility and the lock screen). */
    String scoreWithNames() {
        return RLM + homeName + "  " + hs + " – " + as + "  " + awayName + RLM;
    }

    String statusLine() {
        String s = status + (clock.isEmpty() ? "" : (status.isEmpty() ? "" : " • ") + clock);
        return s.isEmpty() ? "" : RLM + s + RLM;
    }

    String key() {
        return comp + "|" + hs + "|" + as + "|" + status + "|" + clock + "|" + play + "|" + popHome + "|" + popAway;
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
                if (raw != null) b = square(raw, 120);
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

    private static Bitmap appCrest;

    /** Hapoel's crest as in the app's header (not the 365scores image). */
    static Bitmap hapoelCrest(Context c) {
        synchronized (CRESTS) {
            if (appCrest == null) {
                Bitmap raw = BitmapFactory.decodeResource(c.getResources(), R.drawable.hpj_crest);
                if (raw != null) appCrest = square(raw, 120);
            }
            return appCrest;
        }
    }

    /** The crest for one side: Hapoel's from the app, the opponent's from 365scores. Call off the main thread. */
    static Bitmap crestFor(Context c, String team, String url) {
        if (team != null && team.contains(TEAM)) return hapoelCrest(c);
        return crest(url);
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

    // ---------- the live score card ----------

    private static void fill(RemoteViews v, LiveGame g, Bitmap homeCrest, Bitmap awayCrest, boolean fin) {
        v.setTextViewText(R.id.home_score, String.valueOf(g.hs));
        v.setTextViewText(R.id.away_score, String.valueOf(g.as));
        String st = fin ? "סיום" : g.status + (g.clock.isEmpty() ? "" : (g.status.isEmpty() ? "" : " • ") + g.clock);
        v.setTextViewText(R.id.status, RLM + (st.isEmpty() ? "לייב" : st) + RLM);
        v.setInt(R.id.status, "setBackgroundResource", fin ? R.drawable.notif_pill_final : R.drawable.notif_pill);
        if (homeCrest != null) v.setImageViewBitmap(R.id.home_crest, homeCrest);
        else v.setViewVisibility(R.id.home_crest, View.GONE);
        if (awayCrest != null) v.setImageViewBitmap(R.id.away_crest, awayCrest);
        else v.setViewVisibility(R.id.away_crest, View.GONE);
        v.setContentDescription(R.id.home_crest, g.homeName);
        v.setContentDescription(R.id.away_crest, g.awayName);
        pop(v, R.id.home_pop, R.id.home_pop_text, fin ? 0 : g.popHome);
        pop(v, R.id.away_pop, R.id.away_pop_text, fin ? 0 : g.popAway);
        // **competition – stage / round**  (bold)
        SpannableStringBuilder info = new SpannableStringBuilder(RLM + g.comp + RLM);
        info.setSpan(new StyleSpan(Typeface.BOLD), 0, info.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        v.setTextViewText(R.id.info, info);
        v.setViewVisibility(R.id.info, g.comp.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.play, g.play.isEmpty() ? "" : RLM + g.play + RLM);
        v.setViewVisibility(R.id.play, g.play.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * +1 / +2 / +3 next to a score, like in the app: the ViewFlipper shows the badge (pop-in animation) and flips to
     * its empty side after 2.2 seconds (fade-out). Showing it again restarts it from the badge.
     */
    private static void pop(RemoteViews v, int flipper, int text, int pts) {
        if (pts >= 1 && pts <= 3) {
            v.setTextViewText(text, "+" + pts);
            v.setDisplayedChild(flipper, 0);
            v.setViewVisibility(flipper, View.VISIBLE);
        } else {
            v.setViewVisibility(flipper, View.INVISIBLE); // keeps its space: nothing moves when it appears
        }
    }

    /** Points scored since the previous update (for the +N badge): 1–3, otherwise 0. */
    static int delta(int now, int before) {
        int d = now - before;
        return d >= 1 && d <= 3 ? d : 0;
    }

    /** Opens the app on the page of the game that tips off at {@code t}. */
    static PendingIntent open(Context c, long t) {
        Intent i = new Intent(c, MainActivity.class)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, t);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * The live score card. {@code fin} = after the final buzzer (grey "סיום" pill, not ongoing, gone after 30 minutes).
     * {@code del} runs when the user swipes it away (may be null).
     *
     * One compact layout and nothing to expand (no expanded view, no style, no actions). The app targets SDK 30, so
     * Android shows this layout as the whole notification – without its template, the app-name row, the icon or an
     * expand arrow (Notification.Builder: fullyCustomViewRequiresDecoration / expandedContentViewRequired).
     */
    static Notification card(Context c, LiveGame g, Bitmap homeCrest, Bitmap awayCrest, boolean fin, PendingIntent del) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.notif_live_card);
        fill(v, g, homeCrest, awayCrest, fin);
        NotificationCompat.Builder b = LiveScoreService.base(c, (fin ? RLM + "סיום: " : "") + g.scoreWithNames(), g.statusLine())
            .setContentIntent(open(c, g.t))
            .setCustomContentView(v)
            .setOngoing(!fin)
            // In the app's one notification group, on top (sort key "0"); a fixed time (the tip-off) so updating
            // the score every few seconds does not move it around in the notification shade
            .setGroup(NotifGroup.KEY)
            .setSortKey("0")
            .setWhen(g.t > 0 ? g.t : System.currentTimeMillis());
        if (fin) b.setAutoCancel(true).setTimeoutAfter(30 * 60_000L);
        else b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (del != null) b.setDeleteIntent(del);
        return b.build();
    }
}
