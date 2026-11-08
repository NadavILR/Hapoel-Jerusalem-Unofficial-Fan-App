package il.nadavilr.hapoeljerusalem;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.List;

/**
 * The app sends the tip-off times of the next games and whether live-score notifications are on
 * (LiveActivity.sync({on, games:[{t}]})). If a game is on right now, the live notification starts immediately.
 */
@CapacitorPlugin(name = "LiveActivity")
public class LiveActivityPlugin extends Plugin {

    @PluginMethod
    public void sync(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        List<Long> times = new ArrayList<>();
        JSArray games = call.getArray("games");
        if (games != null) {
            for (int i = 0; i < games.length(); i++) {
                org.json.JSONObject g = games.optJSONObject(i);
                if (g == null) continue;
                long t = g.optLong("t", 0);
                if (t > 0) times.add(t);
            }
        }
        LiveScheduler.save(getContext(), on, times);
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
}
