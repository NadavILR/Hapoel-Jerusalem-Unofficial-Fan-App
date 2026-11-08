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

    /** From MainActivity: the app was opened from the live score card. */
    static void fromIntent(Intent i) {
        if (i == null) return;
        long t = i.getLongExtra(LiveGame.EXTRA_OPEN, 0);
        if (t > 0) {
            pendingOpen = t;
            i.removeExtra(LiveGame.EXTRA_OPEN);
        }
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
     * Test mode: demo({t, comp, venue, status, clock, play, home:{name, score, img}, away:{name, score, img}})
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
            nm.cancel(LiveScoreService.NOTIF_ID);
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
        g.venue = call.getString("venue", "");
        g.status = call.getString("status", "");
        g.clock = call.getString("clock", "");
        g.play = call.getString("play", "");
        g.homeName = home.optString("name", "");
        g.awayName = away.optString("name", "");
        g.hs = home.optInt("score", 0);
        g.as = away.optInt("score", 0);
        g.homeImg = home.optString("img", null);
        g.awayImg = away.optString("img", null);
        LiveScoreService.ensureChannel(c);
        try {
            nm.notify(LiveScoreService.NOTIF_ID, LiveGame.card(c, g, LiveGame.crest(g.homeImg), LiveGame.crest(g.awayImg), false, null));
            r.put("shown", true);
        } catch (SecurityException e) {
            r.put("shown", false);
            r.put("reason", "notifications-off");
        }
        call.resolve(r);
    }
}
