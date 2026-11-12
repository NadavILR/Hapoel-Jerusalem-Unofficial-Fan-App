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

    /**
     * Notification test screen: posts an in-game notification exactly as the live score service does (GameAlerts).
     * testAlert({kind: start | quarter | final, q, ot, comp, t, meHome, home:{name, score}, away:{name, score}})
     */
    @PluginMethod
    public void testAlert(PluginCall call) {
        Context c = getContext();
        JSObject home = call.getObject("home"), away = call.getObject("away");
        LiveGame g = new LiveGame();
        g.comp = call.getString("comp", "");
        g.meHome = Boolean.TRUE.equals(call.getBoolean("meHome", true));
        g.homeName = home != null ? home.optString("name", "") : "";
        g.awayName = away != null ? away.optString("name", "") : "";
        g.hs = home != null ? home.optInt("score", 0) : 0;
        g.as = away != null ? away.optInt("score", 0) : 0;
        Long t = call.getLong("t");
        long tipOff = t != null ? t : 0L;
        String kind = call.getString("kind", "");
        if ("start".equals(kind)) GameAlerts.start(c, g, tipOff);
        else if ("quarter".equals(kind)) GameAlerts.quarter(c, g, call.getInt("q", 1), tipOff);
        else if ("final".equals(kind)) GameAlerts.fin(c, g, Boolean.TRUE.equals(call.getBoolean("ot", false)), tipOff);
        JSObject r = new JSObject();
        r.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        call.resolve(r);
    }

    /** Notification test screen: the live card's state on the phone (settings, the game on now, running or not). */
    @PluginMethod
    public void state(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        try {
            org.json.JSONObject o = LiveScheduler.state(c);
            java.util.Iterator<String> k = o.keys();
            while (k.hasNext()) {
                String n = k.next();
                r.put(n, o.get(n));
            }
            boolean shown = false;
            for (android.service.notification.StatusBarNotification s : c.getSystemService(android.app.NotificationManager.class).getActiveNotifications())
                if (s.getId() == LiveScoreService.NOTIF_ID) shown = true;
            r.put("shown", shown);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = c.getSystemService(android.app.NotificationManager.class).getNotificationChannel(LiveScoreService.CHANNEL);
                r.put("channel", ch == null ? -1 : ch.getImportance());
            }
        } catch (Exception e) {
            r.put("error", String.valueOf(e));
        }
        call.resolve(r);
    }

    /**
     * Share to a story: shareImage({data: base64 PNG, text}) – saves the image in the app's cache and opens Android's
     * share sheet (Instagram / Facebook / WhatsApp story or status, and any other app).
     */
    @PluginMethod
    public void shareImage(PluginCall call) {
        Context c = getContext();
        try {
            byte[] png = android.util.Base64.decode(call.getString("data", ""), android.util.Base64.DEFAULT);
            java.io.File dir = new java.io.File(c.getCacheDir(), "share");
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, "hapoel-story.png");
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
                o.write(png);
            }
            android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String text = call.getString("text", "");
            if (text != null && !text.isEmpty()) send.putExtra(Intent.EXTRA_TEXT, text);
            Intent ch = Intent.createChooser(send, "שיתוף לסטורי").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (getActivity() != null) getActivity().startActivity(ch);
            else c.startActivity(ch);
            call.resolve();
        } catch (Exception e) {
            call.reject("share failed", e);
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
        Boolean bg = call.getBoolean("bg");
        if (bg != null) LiveScheduler.setBackground(getContext(), bg);
        LiveScheduler.saveSettings(getContext(), call.getBoolean("quarter"), call.getObject("comps"));
        LiveScheduler.saveNowBar(getContext(), call.getString("nb"));
        LiveScheduler.save(getContext(), on, games, jer);
        // The app is open: a game on now gets its live card back, even if it was swiped away earlier in the game
        LiveScheduler.undone(getContext());
        NextGameWidget.refresh(getContext());
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
     * Test mode: demo({t, comp, status, clock, play, home:{name, score, img}, away:{name, score, img}, check})
     * shows / updates the card; demo({}) without teams removes it. Returns {shown, sdk, nowbar, promoted (with check)}. Runs on Capacitor's plugin thread, so the crests
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
            NextGameWidget.clearLive(c);
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
        // The home-screen widget shows the simulated game too
        g.meHome = g.homeName.contains(LiveGame.TEAM);
        NextGameWidget.live(c, g);
        LiveScoreService.ensureChannel(c);
        try {
            android.graphics.Bitmap hc = LiveGame.crestFor(c, g.homeName, g.homeImg), ac = LiveGame.crestFor(c, g.awayName, g.awayImg);
            nm.notify(LiveScoreService.NOTIF_ID, LiveGame.card(c, g, hc, ac, false, null));
            NotifGroup.refresh(c);
            r.put("shown", true);
            // Android 17: whether the card is a Live Update (Now Bar). With check:true, waits a moment for Android to
            // process it and reports whether it was actually promoted
            r.put("sdk", android.os.Build.VERSION.SDK_INT);
            r.put("nowbar", NowBar.available(c));
            if (Boolean.TRUE.equals(call.getBoolean("check", false))) {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) {
                }
                r.put("promoted", NowBar.promoted(c, LiveScoreService.NOTIF_ID));
            }
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
