package il.nadavilr.hapoeljerusalem;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The latest line of the game's play-by-play ("מהלך המשחק"), for the live score card. The same sources and wording
 * as the app's game page (index.html: pbpEuro / pbpSegevId):
 * - EuroCup: live.euroleague.net PlayByPlay (by the game code the app sends);
 * - Winner League and the cups: basket.co.il livestats (the game is found by time in games.json).
 * Returns e.g. "07:28 · ג'ייר גרייר – שלשה", or null.
 */
final class LivePlay {

    private static final String SEASON = "U2026";
    private static final long HOUR = 3_600_000L;

    private LivePlay() {
    }

    private static final Map<String, String[]> EUA = new HashMap<>();

    static {
        String[][] t = {
            {"2FGM", "קליעה ל-2"}, {"2FGA", "החטאה ל-2"}, {"3FGM", "שלשה"}, {"3FGA", "החטאה לשלוש"},
            {"FTM", "קליעה מהעונשין"}, {"FTA", "החטאה מהעונשין"}, {"D", "ריבאונד הגנה"}, {"O", "ריבאונד התקפה"},
            {"AS", "אסיסט"}, {"ST", "חטיפה"}, {"TO", "איבוד"}, {"FV", "חסימה"}, {"CM", "עבירה"}, {"OF", "עבירת התקפה"},
            {"C", "עבירה טכנית למאמן"}, {"CMT1", "עבירה טכנית"}, {"CMU_FL", "עבירה בוטה"}, {"TOUT", "פסק זמן"},
            {"TOUT_TV", "פסק זמן טלוויזיוני"}, {"CCH", "אתגר מאמן"}};
        for (String[] r : t) EUA.put(r[0], new String[]{r[1]});
    }

    /**
     * @param comp     the app's competition key (league / wcup / cup / super / euro …)
     * @param euroCode EuroCup game code (0 if unknown)
     * @param jer      Hapoel's players by shirt number (Hebrew names, from the app)
     */
    static String latest(String comp, int euroCode, long tipOff, LiveGame g, Map<String, String> jer) {
        try {
            if ("euro".equals(comp)) return euroCode > 0 ? euro(euroCode, g, jer) : null;
            if ("league".equals(comp) || "wcup".equals(comp) || "cup".equals(comp) || "super".equals(comp)) return segev(tipOff, g);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String q(int n) {
        return n <= 4 ? "Q" + n : "OT" + (n - 4);
    }

    private static String line(String clock, String text) {
        clock = clock == null ? "" : clock.trim();
        return clock.isEmpty() ? text : clock + " · " + text;
    }

    // ---------- EuroCup ----------

    private static String euro(int code, LiveGame g, Map<String, String> jer) throws Exception {
        JSONObject d = new JSONObject(get("https://live.euroleague.net/api/PlayByPlay?gamecode=" + code + "&seasoncode=" + SEASON));
        String[] keys = {"FirstQuarter", "SecondQuarter", "ThirdQuarter", "ForthQuarter", "ExtraTime"};
        for (int k = keys.length - 1; k >= 0; k--) {
            JSONArray a = d.optJSONArray(keys[k]);
            if (a == null) continue;
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject x = a.optJSONObject(i);
                if (x == null) continue;
                String t = x.optString("PLAYTYPE", "").trim(), text = null;
                int qn = k + 1;
                if ("BP".equals(t)) text = "תחילת " + q(qn);
                else if ("EP".equals(t)) text = "סיום " + q(qn);
                else if ("EG".equals(t)) text = "סיום המשחק";
                else if (EUA.containsKey(t)) {
                    String team = x.optString("CODETEAM", "").trim();
                    boolean isJ = "JER".equals(team);
                    String who = "";
                    if (!x.isNull("PLAYER") && !x.optString("PLAYER", "").trim().isEmpty()) {
                        String dorsal = x.optString("DORSAL", "").trim();
                        if (isJ && jer != null && jer.containsKey(dorsal)) who = jer.get(dorsal);
                        else who = titleCase(reverseName(x.optString("PLAYER", "")));
                    } else if (!team.isEmpty()) {
                        who = isJ ? LiveGame.TEAM : (g.meHome ? g.awayName : g.homeName);
                    }
                    text = (who.isEmpty() ? "" : who + " – ") + EUA.get(t)[0];
                }
                if (text != null) return line(x.optString("MARKERTIME", ""), text);
            }
        }
        return null;
    }

    private static String reverseName(String s) {
        String[] p = s.split(",");
        StringBuilder b = new StringBuilder();
        for (int i = p.length - 1; i >= 0; i--) {
            if (b.length() > 0) b.append(' ');
            b.append(p[i].trim());
        }
        return b.toString().trim();
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            b.append(up ? Character.toUpperCase(ch) : ch);
            up = ch == ' ' || ch == '-' || ch == '\'';
        }
        return b.toString();
    }

    // ---------- Winner League / cups (basket.co.il livestats) ----------

    private static boolean isJ(JSONObject t) {
        return t != null && (t.optString("nameLocal", "").contains("ירושלים") || t.optString("name", "").toUpperCase(Locale.ROOT).contains("JERUSALEM"));
    }

    private static long idFor;
    private static int cachedId;

    /** The livestats game id of Hapoel's game at {@code tipOff} (from games.json, within 4 hours), 0 if none; cached. */
    static synchronized int segevId(long tipOff) throws Exception {
        if (idFor == tipOff && cachedId > 0) return cachedId;
        long bucket = System.currentTimeMillis() / 10_000L;
        JSONArray games = new JSONObject(get("https://basket.co.il/livestats/data/games.json?w=" + bucket)).optJSONArray("games");
        if (games == null) return 0;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Jerusalem"));
        int id = 0;
        for (int i = 0; i < games.length(); i++) {
            JSONObject x = games.optJSONObject(i);
            if (x == null || !(isJ(x.optJSONObject("homeTeam")) || isJ(x.optJSONObject("awayTeam")))) continue;
            String time = x.optString("time", "");
            if (time.length() < 16) continue;
            long t = f.parse(time.substring(0, 16)).getTime();
            if (Math.abs(t - tipOff) < 4 * HOUR) {
                id = x.optInt("id", 0);
                break;
            }
        }
        if (id > 0) {
            idFor = tipOff;
            cachedId = id;
        }
        return id;
    }

    /** A basket.co.il livestats game file (score, clock, play-by-play, box score). */
    static JSONObject segevData(int id) throws Exception {
        long bucket = System.currentTimeMillis() / 10_000L;
        return new JSONObject(get("https://basket.co.il/livestats/data/game-" + id + ".json?w=" + bucket));
    }

    private static String segev(long tipOff, LiveGame g) throws Exception {
        int id = segevId(tipOff);
        return id == 0 ? null : segevLine(segevData(id), g);
    }

    /** The latest line of a basket.co.il livestats game. */
    static String segevGame(int id, LiveGame g) throws Exception {
        return segevLine(segevData(id), g);
    }

    /** The latest line of an already-fetched livestats game file. */
    static String segevLine(JSONObject d, LiveGame g) {
        JSONObject gi = d.optJSONObject("gameInfo");
        boolean jh = gi != null && isJ(gi.optJSONObject("homeTeam"));
        Map<String, String> names = new HashMap<>();
        if (gi != null) {
            JSONObject[] teams = {gi.optJSONObject("homeTeam"), gi.optJSONObject("awayTeam")};
            for (int ti = 0; ti < 2; ti++) {
                JSONArray ps = teams[ti] != null ? teams[ti].optJSONArray("players") : null;
                if (ps == null) continue;
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject p = ps.optJSONObject(i);
                    if (p == null) continue;
                    String n = (p.optString("firstNameLocal", "") + " " + p.optString("lastNameLocal", "")).trim();
                    if (n.isEmpty()) n = titleCase((p.optString("firstName", "") + " " + p.optString("lastName", "")).trim());
                    names.put((ti + 1) + ":" + p.optString("jerseyNumber", ""), n);
                }
            }
        }
        JSONObject acts = d.optJSONObject("actions");
        JSONArray list = acts != null ? acts.optJSONArray("actions") : null;
        if (list == null) return null;
        String opp = g.meHome ? g.awayName : g.homeName;
        for (int i = list.length() - 1; i >= 0; i--) {
            JSONObject a = list.optJSONObject(i);
            if (a == null) continue;
            JSONObject p = a.optJSONObject("parameters");
            if (p == null) p = new JSONObject();
            String type = a.optString("type", ""), pt = p.optString("type", ""), text = null;
            int team = p.optInt("team", 0), qn = a.optInt("quarter", 1);
            boolean jSide = team == 1 ? jh : team == 2 && !jh;
            String who = team > 0 && !p.isNull("player") ? names.getOrDefault(team + ":" + p.optString("player", ""), "") : "";
            switch (type) {
                case "game":
                    text = "start-of-game".equals(pt) ? "שריקת פתיחה" : "end-of-game".equals(pt) ? "סיום המשחק" : null;
                    break;
                case "quarter":
                    text = "start-of-quarter".equals(pt) ? "תחילת " + q(qn) : "end-of-quarter".equals(pt) ? "סיום " + q(qn) : null;
                    break;
                case "shot": {
                    boolean made = "made".equals(p.optString("made"));
                    int pts = p.optInt("points", 2);
                    text = pts == 3 ? (made ? "שלשה" : "החטאה לשלוש") : (made ? "קליעה ל-2" : "החטאה ל-2");
                    break;
                }
                case "freeThrow":
                    text = "made".equals(p.optString("made")) ? "קליעה מהעונשין" : "החטאה מהעונשין";
                    break;
                case "rebound":
                    text = "offensive".equals(pt) ? "ריבאונד התקפה" : "ריבאונד הגנה";
                    break;
                case "assist": text = "אסיסט"; break;
                case "steal": text = "חטיפה"; break;
                case "block": text = "חסימה"; break;
                case "turnover": text = "איבוד"; break;
                case "foul":
                    text = "offensive".equals(p.optString("kind")) ? "עבירת התקפה" : "technical".equals(pt) ? "עבירה טכנית"
                        : "unsportsmanlike".equals(pt) ? "עבירה בוטה" : "עבירה";
                    break;
                case "timeout": text = "פסק זמן"; break;
                case "challenge": text = "אתגר מאמן"; break;
                default: break;
            }
            if (text == null) continue;
            switch (type) {
                case "shot": case "freeThrow": case "rebound": case "assist": case "steal": case "block": case "turnover": case "foul":
                    if (!who.isEmpty()) text = who + " – " + text;
                    break;
                case "timeout":
                    if (team > 0) text = (jSide ? LiveGame.TEAM : opp) + " – " + text;
                    break;
                default: break;
            }
            return line(a.optString("quarterTime", ""), text);
        }
        return null;
    }

    // ---------- the live game itself from livestats (Winner League / Winner Cup) ----------

    static final int PRE = 2, LIVE = 3, FINAL = 4;

    /** The game's sides from a livestats file. The names are replaced by 365scores' (no sponsors) in LiveScoreService. */
    static LiveGame baseGame(JSONObject d) {
        JSONObject gi = d.optJSONObject("gameInfo");
        if (gi == null) return null;
        JSONObject h = gi.optJSONObject("homeTeam"), a = gi.optJSONObject("awayTeam");
        LiveGame x = new LiveGame();
        x.meHome = isJ(h);
        x.homeName = x.meHome ? LiveGame.TEAM : (h != null ? h.optString("nameLocal", h.optString("shortNameLocal", "")) : "");
        x.awayName = !x.meHome ? LiveGame.TEAM : (a != null ? a.optString("nameLocal", a.optString("shortNameLocal", "")) : "");
        return x;
    }

    /**
     * Fills the score, quarter, clock and status of {@code x} from a livestats game file – the official live stats of
     * the Winner League / Winner Cup – and returns PRE / LIVE / FINAL. {@code x}'s home / away are kept (365scores'
     * order); the livestats sides are matched by which one is Hapoel.
     */
    static int applyLive(JSONObject d, LiveGame x) {
        JSONObject gi = d.optJSONObject("gameInfo");
        JSONObject box = d.optJSONObject("boxscore");
        JSONObject bg = box != null ? box.optJSONObject("gameInfo") : null;
        if (gi == null) return PRE;
        boolean lsHomeIsJ = isJ(gi.optJSONObject("homeTeam"));
        int lh = bg != null ? bg.optInt("homeScore", 0) : 0, la = bg != null ? bg.optInt("awayScore", 0) : 0;
        boolean same = lsHomeIsJ == x.meHome; // livestats' home is 365scores' home
        x.hs = same ? lh : la;
        x.as = same ? la : lh;
        JSONObject acts = d.optJSONObject("actions");
        JSONArray list = acts != null ? acts.optJSONArray("actions") : null;
        boolean started = false, onBreak = false;
        if (list != null) {
            for (int i = 0; i < list.length() && !started; i++) {
                JSONObject a = list.optJSONObject(i);
                if (a != null && !"clock".equals(a.optString("type"))) started = true;
            }
            for (int i = list.length() - 1; i >= 0; i--) {
                JSONObject a = list.optJSONObject(i);
                if (a == null || "clock".equals(a.optString("type"))) continue;
                JSONObject p = a.optJSONObject("parameters");
                onBreak = "quarter".equals(a.optString("type")) && p != null && "end-of-quarter".equals(p.optString("type"));
                break;
            }
        }
        if (gi.optBoolean("gameFinished", false)) {
            x.status = "סיום";
            x.clock = "";
            return FINAL;
        }
        if (!started) return PRE;
        int q = gi.optInt("currentQuarter", 1);
        if (onBreak) {
            x.status = q == 2 ? "מחצית" : "הפסקה";
            x.clock = "";
        } else {
            x.status = q <= 4 ? "רבע " + q : "הארכה" + (q > 5 ? " " + (q - 4) : "");
            // The newest clock: the latest action of this quarter (currentQuarterTime is updated less often)
            String clock = "";
            for (int i = list != null ? list.length() - 1 : -1; i >= 0; i--) {
                JSONObject a = list.optJSONObject(i);
                if (a == null) continue;
                if (a.optInt("quarter", q) == q) clock = a.optString("quarterTime", "");
                break;
            }
            JSONObject t = gi.optJSONObject("currentQuarterTime");
            if (clock.isEmpty() && t != null) clock = String.format(Locale.US, "%02d:%02d", t.optInt("m", 0), t.optInt("s", 0));
            x.clock = clock;
        }
        return LIVE;
    }

    // ---------- network ----------

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12_000);
        c.setReadTimeout(12_000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) HapoelJerusalemFanApp");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String s = out.toString("UTF-8");
            return s.startsWith("﻿") ? s.substring(1) : s; // basket.co.il starts with a byte-order mark
        } finally {
            c.disconnect();
        }
    }
}
