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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Live score while Hapoel Jerusalem plays: one ongoing notification – the live score card (LiveGame) – that follows
 * the game from tip-off to the final buzzer and updates every 20 seconds: the score from 365scores, the latest
 * play-by-play line from EuroLeague / basket.co.il (LivePlay). Tapping it opens the game's page in the app.
 *
 * Started by LiveAlarmReceiver at tip-off (an exact alarm the app schedules), or by the app itself when it is
 * opened during a game. It runs only while the device allows the app's notifications.
 */
public class LiveScoreService extends Service {

    static final String CHANNEL = "hpj-live";
    static final int NOTIF_ID = 63401;
    static final String ACTION_STOP = "il.nadavilr.hapoeljerusalem.LIVE_STOP";
    private static final String API = "https://webws.365scores.com/web/games/current/?appTypeId=5&langId=2"
        + "&timezoneName=Asia/Jerusalem&userCountryId=6&competitors=" + LiveGame.HAPOEL;
    private static final long MIN = 60_000L, HOUR = 60 * MIN;

    private HandlerThread thread;
    private Handler handler;
    private long tipOff;
    private boolean wasLive, finalShown;
    private String lastKey = "", lastPlay = "";

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
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        long t = intent != null ? intent.getLongExtra("t", 0) : 0;
        if (t > 0) tipOff = t;
        if (tipOff == 0) tipOff = System.currentTimeMillis();
        ensureChannel(this);
        Notification first = base(this, LiveGame.TEAM, "\u200Fשריקת הפתיחה בקרוב").setOngoing(true)
            .setContentIntent(LiveGame.open(this, tipOff))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build();
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
        if (!finalShown) cancel(); // never leave a stale "live" card behind
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
            JSONArray games = new JSONObject(LivePlay.get(API)).optJSONArray("games");
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

    /** The game from 365scores, with the latest play-by-play line and the tip-off that identifies it. */
    private LiveGame game(JSONObject g) {
        LiveGame x = LiveGame.from365(g);
        if (x == null) return null;
        x.t = tipOff;
        JSONObject meta = LiveScheduler.game(this, tipOff);
        String play = LivePlay.latest(meta.optString("c", ""), meta.optInt("eg", 0), tipOff, x, LiveScheduler.roster(this));
        if (play != null && !play.isEmpty()) lastPlay = play;
        x.play = lastPlay;
        return x;
    }

    private void show(JSONObject g) {
        LiveGame x = game(g);
        if (x == null) return;
        if (x.key().equals(lastKey)) return;
        lastKey = x.key();
        Intent stop = new Intent(this, LiveScoreService.class).setAction(ACTION_STOP);
        PendingIntent del = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        notify(NOTIF_ID, LiveGame.card(this, x, LiveGame.crest(x.homeImg), LiveGame.crest(x.awayImg), false, del));
    }

    /** Ends following the game; with a final game, the card stays for 30 minutes with the final score. */
    private void finish(JSONObject finalGame) {
        LiveGame x = finalGame != null ? game(finalGame) : null;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (x != null && LiveScheduler.allowed(this)) {
            notify(NOTIF_ID, LiveGame.card(this, x, LiveGame.crest(x.homeImg), LiveGame.crest(x.awayImg), true, null));
            finalShown = true;
        }
        LiveScheduler.done(this, tipOff);
        stopSelf();
    }

    private void cancel() {
        try {
            NotificationManagerCompat.from(this).cancel(NOTIF_ID);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("MissingPermission")
    private void notify(int id, Notification n) {
        try {
            NotificationManagerCompat.from(this).notify(id, n);
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

}
