package il.nadavilr.hapoeljerusalem;

import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * The live score card from the app's side:
 * - sync({on, games:[{t, c, eg}], jer:{shirt: name}}) – live-score setting, the next games and Hapoel's roster;
 *   if a game is on right now, the card starts immediately;
 * - demo({...}) – test mode (three taps under the home screen) shows the same card with the simulated game;
 * - takeOpen() – the game whose card was tapped ({t}: its tip-off), once, so the app opens that game's page.
 */
@CapacitorPlugin(name = "LiveActivity")
public class LiveActivityPlugin extends Plugin {

    private static long pendingOpen;
    /** Test mode: the previous simulated score, for the +1 / +2 / +3 badge. */
    private static int demoHs = -1, demoAs = -1;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable demoClear;

    /** From MainActivity: the app was opened from the live score card. */
    static void fromIntent(Intent i) {
        if (i == null) return;
        long t = i.getLongExtra(LiveGame.EXTRA_OPEN, 0);
        if (t > 0) {
            pendingOpen = t;
            i.removeExtra(LiveGame.EXTRA_OPEN);
        }
    }

    /** After the app posted a notification (with the group key "hpj-all"): keep the app's notifications one group. */
    @PluginMethod
    public void group(PluginCall call) {
        NotifGroup.refresh(getContext());
        call.resolve();
    }

    @PluginMethod
    public void takeOpen(PluginCall call) {
        JSObject r = new JSObject();
        if (pendingOpen > 0) r.put("t", pendingOpen);
        pendingOpen = 0;
        call.resolve(r);
    }

    @PluginMethod
    public void sync(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        JSArray games = call.getArray("games");
        JSObject jer = call.getObject("jer");
        Boolean bg = call.getBoolean("bg");
        if (bg != null) LiveScheduler.setBackground(getContext(), bg);
        LiveScheduler.saveSettings(getContext(), call.getBoolean("quarter"), call.getObject("comps"));
        LiveScheduler.save(getContext(), on, games, jer);
        JSObject r = new JSObject();
        long now = LiveScheduler.current(getContext());
        if (LiveScheduler.allowed(getContext()) && now > 0) {
            try {
                LiveScoreService.start(getContext(), now);
                r.put("started", true);
            } catch (Exception e) {
                r.put("started", false);
            }
        } else if (!LiveScheduler.allowed(getContext())) {
            LiveScoreService.stop(getContext());
        }
        call.resolve(r);
    }

    /**
     * Background mode on: asks Android to let the app run without battery optimization (the system dialog), so the
     * live card keeps going when the app is closed. Returns {ignoring: whether the app is already exempt}.
     */
    @PluginMethod
    public void background(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        boolean ignoring = true;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
            ignoring = pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
            if (!ignoring && Boolean.TRUE.equals(call.getBoolean("ask", true))) {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(i);
                } catch (Exception e) {
                    try {
                        c.startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        r.put("ignoring", ignoring);
        call.resolve(r);
    }

    /**
     * Test mode: demo({t, comp, status, clock, play, home:{name, score, img}, away:{name, score, img}})
     * shows / updates the card; demo({}) without teams removes it. Runs on Capacitor's plugin thread, so the crests
     * can be downloaded here.
     */
    @PluginMethod
    public void demo(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        JSObject home = call.getObject("home"), away = call.getObject("away");
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (home == null || away == null) {
            demoHs = -1;
            demoAs = -1;
            if (demoClear != null) MAIN.removeCallbacks(demoClear);
            demoClear = null;
            nm.cancel(LiveScoreService.NOTIF_ID);
            NotifGroup.refresh(c);
            r.put("shown", false);
            call.resolve(r);
            return;
        }
        if (!nm.areNotificationsEnabled()) {
            r.put("shown", false);
            r.put("reason", "notifications-off");
            call.resolve(r);
            return;
        }
        LiveGame g = new LiveGame();
        Long t = call.getLong("t");
        g.t = t != null ? t : 0L;
        g.comp = call.getString("comp", "");
        g.status = call.getString("status", "");
        g.clock = call.getString("clock", "");
        g.play = call.getString("play", "");
        g.homeName = home.optString("name", "");
        g.awayName = away.optString("name", "");
        g.hs = home.optInt("score", 0);
        g.as = away.optInt("score", 0);
        g.homeImg = home.optString("img", null);
        g.awayImg = away.optString("img", null);
        if (demoHs >= 0) {
            g.popHome = LiveGame.delta(g.hs, demoHs);
            g.popAway = LiveGame.delta(g.as, demoAs);
        }
        demoHs = g.hs;
        demoAs = g.as;
        LiveScoreService.ensureChannel(c);
        try {
            android.graphics.Bitmap hc = LiveGame.crestFor(c, g.homeName, g.homeImg), ac = LiveGame.crestFor(c, g.awayName, g.awayImg);
            nm.notify(LiveScoreService.NOTIF_ID, LiveGame.card(c, g, hc, ac, false, null));
            NotifGroup.refresh(c);
            r.put("shown", true);
            // Remove the +N badge once it has faded (in case the next simulated update is late)
            if (demoClear != null) MAIN.removeCallbacks(demoClear);
            demoClear = null;
            if (g.popHome > 0 || g.popAway > 0) {
                demoClear = () -> {
                    g.popHome = 0;
                    g.popAway = 0;
                    try {
                        if (demoHs >= 0) nm.notify(LiveScoreService.NOTIF_ID, LiveGame.card(c, g, hc, ac, false, null));
                    } catch (SecurityException ignored) {
                    }
                };
                MAIN.postDelayed(demoClear, 3_500L);
            }
        } catch (SecurityException e) {
            r.put("shown", false);
            r.put("reason", "notifications-off");
        }
        call.resolve(r);
    }
}
