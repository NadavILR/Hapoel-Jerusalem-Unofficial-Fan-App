package il.nadavilr.hapoeljerusalem;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The phone fetches Hapoel's next games by itself – the app does not need to be opened. Runs every 6 hours (and
 * after a restart or an app update): reads the fixtures from 365scores (and the EuroCup game codes from EuroLeague,
 * for the play-by-play), arms the tip-off alarms, and if a game is live right now starts the live score card.
 */
public class LiveFixtures extends Worker {

    private static final String PERIODIC = "hpj-fixtures", NOW = "hpj-fixtures-now";
    private static final String FIXTURES = "https://webws.365scores.com/web/games/fixtures/?appTypeId=5&langId=2"
        + "&timezoneName=Asia/Jerusalem&userCountryId=6&competitors=" + LiveGame.HAPOEL;
    private static final String EURO = "https://feeds.incrowdsports.com/provider/euroleague-feeds/v2/competitions/U/seasons/U2026/games?teamCode=JER";
    private static final long HOUR = 3_600_000L;

    public LiveFixtures(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Every 6 hours (kept if already scheduled), when there is internet. */
    static void schedule(Context c) {
        try {
            Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            PeriodicWorkRequest p = new PeriodicWorkRequest.Builder(LiveFixtures.class, 6, TimeUnit.HOURS).setConstraints(net).build();
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, p);
        } catch (Exception ignored) {
        }
    }

    /** Once, as soon as there is internet (after a restart / an app update). */
    static void runNow(Context c) {
        try {
            Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            OneTimeWorkRequest o = new OneTimeWorkRequest.Builder(LiveFixtures.class).setConstraints(net).build();
            WorkManager.getInstance(c).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, o);
        } catch (Exception ignored) {
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            refresh(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    /** 365scores competition id → the app's competition key. */
    private static String comp(int id) {
        switch (id) {
            case 47: return "league";
            case 329: return "euro";
            case 609: return "wcup";
            case 51: return "cup";
            default: return "";
        }
    }

    static void refresh(Context c) throws Exception {
        JSONArray list = new JSONObject(LivePlay.get(FIXTURES)).optJSONArray("games");
        if (list == null) return;
        JSONArray euro = null;
        long now = System.currentTimeMillis(), liveAt = 0;
        JSONArray out = new JSONArray();
        for (int i = 0; i < list.length(); i++) {
            JSONObject g = list.optJSONObject(i);
            if (g == null) continue;
            int sg = g.optInt("statusGroup");
            if (sg != 2 && sg != 3) continue; // upcoming or live
            long t = parse(g.optString("startTime"));
            if (t <= 0 || t > now + 45L * 24 * HOUR) continue;
            String key = comp(g.optInt("competitionId"));
            JSONObject x = new JSONObject().put("t", t).put("c", key);
            if ("euro".equals(key)) {
                if (euro == null) {
                    try {
                        euro = new JSONObject(LivePlay.get(EURO)).optJSONArray("data");
                    } catch (Exception e) {
                        euro = new JSONArray();
                    }
                }
                x.put("eg", euroCode(euro, t));
            }
            out.put(x);
            if (sg == 3 && liveAt == 0) liveAt = t;
        }
        if (out.length() == 0) return;
        LiveScheduler.saveFetched(c, out);
        // A game is on right now: start the live score card
        if (liveAt > 0 && LiveScheduler.allowed(c) && LiveScheduler.current(c) > 0) {
            try {
                LiveScoreService.start(c, LiveScheduler.current(c));
            } catch (Exception ignored) {
            }
        }
    }

    /** The EuroLeague game code of the EuroCup game at {@code t} (within 3 hours), or 0. */
    private static int euroCode(JSONArray euro, long t) {
        if (euro == null) return 0;
        for (int i = 0; i < euro.length(); i++) {
            JSONObject g = euro.optJSONObject(i);
            if (g == null) continue;
            long d = parse(g.optString("date"));
            if (d > 0 && Math.abs(d - t) < 3 * HOUR) return g.optInt("code", 0);
        }
        return 0;
    }

    private static long parse(String s) {
        if (s == null || s.isEmpty()) return 0;
        String[] fmts = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"};
        for (String f : fmts) {
            try {
                return new SimpleDateFormat(f, Locale.US).parse(s).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
