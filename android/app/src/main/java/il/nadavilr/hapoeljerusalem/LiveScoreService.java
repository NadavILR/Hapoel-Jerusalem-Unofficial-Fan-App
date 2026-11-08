package il.nadavilr.hapoeljerusalem;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Live score while Hapoel Jerusalem plays: one ongoing notification that follows the game from tip-off to
 * the final buzzer and updates every 20 seconds from 365scores.
 *
 * On Android 16 the notification asks to be a "Live Update" (promoted ongoing notification), so the score
 * also shows as a chip in the status bar and on the lock screen; on Samsung (One UI 8) Live Updates appear in
 * the Now Bar. The Samsung "ongoing activity" extras are also set for One UI 7's Now Bar.
 *
 * Started by LiveAlarmReceiver at tip-off (an exact alarm the app schedules), or by the app itself when it is
 * opened during a game. It runs only while the device allows the app's notifications.
 */
public class LiveScoreService extends Service {

    static final String CHANNEL = "hpj-live";
    static final int NOTIF_ID = 63401;
    static final String ACTION_STOP = "il.nadavilr.hapoeljerusalem.LIVE_STOP";
    private static final int HAPOEL = 634;
    private static final String TEAM = "הפועל ירושלים";
    private static final String API = "https://webws.365scores.com/web/games/current/?appTypeId=5&langId=2"
        + "&timezoneName=Asia/Jerusalem&userCountryId=6&competitors=" + HAPOEL;
    private static final long MIN = 60_000L, HOUR = 60 * MIN;

    private HandlerThread thread;
    private Handler handler;
    private long tipOff;
    private boolean wasLive;
    private String lastKey = "";

    /** Starts (or keeps) following the game that tips off at {@code tipOff} (ms). */
    static void start(Context c, long tipOff) {
        Intent i = new Intent(c, LiveScoreService.class).putExtra("t", tipOff);
        ContextCompat.startForegroundService(c, i);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, LiveScoreService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            // The user swiped the live notification away: stop following this game
            LiveScheduler.dismissed(this, tipOff);
            stopSelf();
            return START_NOT_STICKY;
        }
        long t = intent != null ? intent.getLongExtra("t", 0) : 0;
        if (t > 0) tipOff = t;
        if (tipOff == 0) tipOff = System.currentTimeMillis();
        ensureChannel(this);
        Notification first = build(TEAM, "שריקת הפתיחה בקרוב", null, null, false);
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, first,
                Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!LiveScheduler.allowed(this)) {
            finish(null);
            return START_NOT_STICKY;
        }
        if (handler == null) {
            thread = new HandlerThread("hpj-live");
            thread.start();
            handler = new Handler(thread.getLooper());
            handler.post(this::poll);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        handler = null;
        thread = null;
        super.onDestroy();
    }

    private void poll() {
        long wait = 20_000L, now = System.currentTimeMillis();
        if (!LiveScheduler.allowed(this)) {
            finish(null);
            return;
        }
        try {
            JSONArray games = new JSONObject(get(API)).optJSONArray("games");
            JSONObject g = pick(games);
            if (g != null) {
                int sg = g.optInt("statusGroup");
                if (sg == 4) { // final
                    finish(wasLive ? g : null);
                    return;
                }
                if (sg == 3) {
                    wasLive = true;
                    show(g);
                } else {
                    wait = 60_000L; // not started yet
                }
            }
        } catch (Exception ignored) {
        }
        if (now > tipOff + 4 * HOUR) { // never runs for more than 4 hours after the scheduled tip-off
            finish(null);
            return;
        }
        if (handler != null) handler.postDelayed(this::poll, wait);
    }

    /** The game in progress; otherwise the one closest to the scheduled tip-off (within 3 hours). */
    private JSONObject pick(JSONArray games) {
        if (games == null) return null;
        JSONObject best = null;
        long bestD = 3 * HOUR;
        for (int i = 0; i < games.length(); i++) {
            JSONObject g = games.optJSONObject(i);
            if (g == null) continue;
            if (g.optInt("statusGroup") == 3) return g;
            long st = parseTime(g.optString("startTime"));
            long d = Math.abs(st - tipOff);
            if (st > 0 && d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    private static long parseTime(String s) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Hapoel's score first: [title, status line, short chip text] */
    private static String[] texts(JSONObject g) {
        JSONObject h = g.optJSONObject("homeCompetitor"), a = g.optJSONObject("awayCompetitor");
        if (h == null || a == null) return null;
        boolean home = h.optInt("id") == HAPOEL;
        JSONObject me = home ? h : a, op = home ? a : h;
        int ms = (int) Math.max(0, Math.round(me.optDouble("score", 0))), os = (int) Math.max(0, Math.round(op.optDouble("score", 0)));
        String opName = op.optString("shortName", "");
        if (opName.isEmpty()) opName = op.optString("name", "");
        String status = g.optString("statusText", g.optString("shortStatusText", "")).trim();
        String clock = g.optString("gameTimeDisplay", "").trim();
        String line = status + (clock.isEmpty() ? "" : " · " + clock);
        return new String[]{TEAM + " " + ms + " – " + os + " " + opName, line, ms + "-" + os, opName};
    }

    private void show(JSONObject g) {
        String[] t = texts(g);
        if (t == null) return;
        String key = t[0] + "|" + t[1];
        if (key.equals(lastKey)) return;
        lastKey = key;
        notify(build(t[0], t[1], t[2], t[3], true));
    }

    /** Ends following the game; with a final game, leaves a regular "final score" notification for 30 minutes. */
    private void finish(JSONObject finalGame) {
        String[] t = finalGame != null ? texts(finalGame) : null;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (t != null && LiveScheduler.allowed(this)) {
            NotificationCompat.Builder b = base(this, "סיום: " + t[0], "התוצאה הסופית")
                .setOngoing(false)
                .setAutoCancel(true)
                .setTimeoutAfter(30 * MIN);
            notify(b.build());
        }
        LiveScheduler.done(this, tipOff);
        stopSelf();
    }

    @SuppressWarnings("MissingPermission")
    private void notify(Notification n) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, n);
        } catch (Exception ignored) {
        }
    }

    static NotificationCompat.Builder base(Context c, String title, String text) {
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hpj)
            .setColor(Color.parseColor("#D21919"))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }

    private Notification build(String title, String text, String chip, String opponent, boolean live) {
        Intent stop = new Intent(this, LiveScoreService.class).setAction(ACTION_STOP);
        PendingIntent del = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return build(this, title, text, chip, opponent, live, del);
    }

    /** The live notification itself (also used by the app's test mode, with no service behind it). */
    static Notification build(Context c, String title, String text, String chip, String opponent, boolean live, PendingIntent del) {
        NotificationCompat.Builder b = base(c, title, text)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (del != null) b.setDeleteIntent(del);
        String nowSecondary = live && opponent != null ? opponent + " · " + text : text;
        Bundle x = new Bundle();
        // Samsung (One UI): colour of the status-bar chip / Now Bar pill
        x.putInt("android.ongoingActivityNoti.chipBgColor", CHIP_COLOR);
        if (Build.VERSION.SDK_INT < 36) {
            // One UI 7 (Android 15): Samsung's own Live Notifications card in the Now Bar. Shown for apps Samsung
            // allows (manifest meta-data com.samsung.android.support.ongoing_activity) or when the phone's
            // Developer options > "Live notifications for all apps" is on.
            x.putInt("android.ongoingActivityNoti.style", 1);
            x.putString("android.ongoingActivityNoti.primaryInfo", title);
            x.putString("android.ongoingActivityNoti.secondaryInfo", text);
            x.putInt("android.ongoingActivityNoti.actionType", 1);
            x.putInt("android.ongoingActivityNoti.actionPrimarySet", 0);
            x.putString("android.ongoingActivityNoti.nowbarPrimaryInfo", chip != null ? chip : title);
            x.putString("android.ongoingActivityNoti.nowbarSecondaryInfo", chip != null ? nowSecondary : text);
            if (chip != null) x.putString("android.ongoingActivityNoti.chipExpandedText", chip);
        }
        // One UI 8+ (Android 16+): no "style" extra – it sends the notification down Samsung's private card path
        // instead of the standard Live Update, which is what the Now Bar shows there.
        b.addExtras(x);
        return promote(c, b.build(), chip);
    }

    private static final int CHIP_COLOR = 0xFFD21919;

    /**
     * Android 16+ "Live Updates": Notification.Builder#setRequestPromotedOngoing(true) and #setShortCriticalText(chip)
     * (status-bar chip, lock screen, Samsung Now Bar). The app is built against an older SDK, so they are called by
     * reflection on the device's own Notification.Builder; on older Android nothing changes.
     */
    static Notification promote(Context c, Notification n, String chip) {
        if (Build.VERSION.SDK_INT < 36) return n;
        Notification.Builder fb;
        try {
            fb = Notification.Builder.recoverBuilder(c, n);
        } catch (Throwable e) {
            n.extras.putBoolean("android.requestPromotedOngoing", true);
            if (chip != null) n.extras.putString("android.shortCriticalText", chip);
            return n;
        }
        try {
            Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(fb, true);
        } catch (Throwable e) {
            fb.getExtras().putBoolean("android.requestPromotedOngoing", true);
        }
        if (chip != null) {
            boolean set = false;
            for (Class<?> t : new Class<?>[]{String.class, CharSequence.class}) {
                try {
                    Notification.Builder.class.getMethod("setShortCriticalText", t).invoke(fb, chip);
                    set = true;
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (!set) fb.getExtras().putString("android.shortCriticalText", chip);
        }
        return fb.build();
    }

    /** Android 16+: whether the user allows this app's Live Updates (Settings > Notifications > the app > Live updates). */
    static boolean canPromote(Context c) {
        if (Build.VERSION.SDK_INT < 36) return false;
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            return Boolean.TRUE.equals(NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(nm));
        } catch (Throwable e) {
            return false;
        }
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "תוצאה בלייב", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("התוצאה של המשחק בזמן אמת, בזמן שהפועל ירושלים משחקת");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) HapoelJerusalemFanApp");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
