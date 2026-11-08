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

    /**
     * Test mode (three taps under the home screen): shows the same live notification with the simulated score.
     * demo({title, text, chip, opponent}) shows / updates it, demo({}) without a title removes it.
     */
    @PluginMethod
    public void demo(PluginCall call) {
        android.content.Context c = getContext();
        JSObject r = new JSObject();
        String title = call.getString("title");
        androidx.core.app.NotificationManagerCompat nm = androidx.core.app.NotificationManagerCompat.from(c);
        if (title == null || title.isEmpty()) {
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
        LiveScoreService.ensureChannel(c);
        try {
            nm.notify(LiveScoreService.NOTIF_ID, LiveScoreService.build(c, title, call.getString("text", ""),
                call.getString("chip"), call.getString("opponent"), true, null));
            r.put("shown", true);
        } catch (SecurityException e) {
            r.put("shown", false);
            r.put("reason", "notifications-off");
        }
        call.resolve(r);
    }

    /** For the settings screen: are notifications allowed, and (Android 16+) are this app's Live Updates allowed. */
    @PluginMethod
    public void status(PluginCall call) {
        android.content.Context c = getContext();
        JSObject r = new JSObject();
        r.put("notifications", androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled());
        r.put("liveUpdates", android.os.Build.VERSION.SDK_INT >= 36);
        r.put("promoted", LiveScoreService.canPromote(c));
        r.put("samsung", "samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER));
        r.put("sdk", android.os.Build.VERSION.SDK_INT);
        call.resolve(r);
    }

    /** Opens the app's notification settings (where Android 16 has the "Live updates" switch). */
    @PluginMethod
    public void openSettings(PluginCall call) {
        android.content.Context c = getContext();
        try {
            android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.getPackageName())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + c.getPackageName())).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception ignored) {
            }
        }
        call.resolve();
    }
}
