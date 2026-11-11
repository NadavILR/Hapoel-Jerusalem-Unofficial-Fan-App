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

    /** The live score card's channel (IMPORTANCE_LOW). "hpj-live" was the old one, at IMPORTANCE_DEFAULT. */
    static final String CHANNEL = "hpj-live";
    /** The Now Bar's channel (Live Update), IMPORTANCE_LOW. */
    static final String NOW_CHANNEL = "hpj-live-low";
    static final int NOTIF_ID = 63401;
    static final String ACTION_STOP = "il.nadavilr.hapoeljerusalem.LIVE_STOP";
    private static final String API = "https://webws.365scores.com/web/games/current/?appTypeId=5&langId=2"
        + "&timezoneName=Asia/Jerusalem&userCountryId=6&competitors=" + LiveGame.HAPOEL;
    private static final long MIN = 60_000L, HOUR = 60 * MIN;

    private HandlerThread thread;
    private Handler handler;
    private long tipOff;
    private boolean wasLive, finalShown, finished;
    /** The live card as last posted (kept when the service is started again). */
    private Notification current;
    private String lastKey = "", lastPlay = "";
    private int lastHs = -1, lastAs = -1;

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
        if (intent == null && tipOff == 0) {
            // Restarted by Android after being stopped (background mode): continue with the game that is on now
            tipOff = LiveScheduler.current(this);
            if (tipOff == 0) {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        if (tipOff == 0) tipOff = System.currentTimeMillis();
        ensureChannel(this);
        // Already showing the live card (another start from the app, the watchdog or the fixtures check): keep it as
        // it is – replacing it with the "starting soon" notification made the card jump
        Notification first = current != null ? current : base(this, LiveGame.TEAM, "\u200Fשריקת הפתיחה בקרוב").setOngoing(true)
            .setContentIntent(LiveGame.open(this, tipOff))
            .setGroup(NotifGroup.KEY).setSortKey("0").setWhen(tipOff)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build();
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, first,
                Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
            NotifGroup.refresh(this);
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
        // Background mode: Android restarts the service if it is stopped, and a watchdog alarm checks every 5 minutes
        if (LiveScheduler.background(this)) {
            LiveScheduler.armWatchdog(this);
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    /** The app was swiped away from the recent apps: in background mode, come back right away. */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (LiveScheduler.background(this) && !finished) LiveScheduler.armWatchdog(this, 2_000L);
    }

    @Override
    public void onDestroy() {
        if (!finalShown) cancel(); // never leave a stale "live" card behind
        NotifGroup.refresh(this);
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        handler = null;
        thread = null;
        super.onDestroy();
    }

    /** How often to check 365scores: every 2 seconds while the game is live, so the card follows the score within
     *  a few seconds of 365scores; every 10 seconds around tip-off; every minute before that. */
    private static final long LIVE_POLL = 2_000L, NEAR_POLL = 10_000L, PRE_POLL = 60_000L;

    /** 365scores' game: names, crests and competition (and the score itself outside the Winner League / Cup). */
    private JSONObject g365;
    private long at365;

    private void poll() {
        long wait = LIVE_POLL, now = System.currentTimeMillis();
        if (!LiveScheduler.allowed(this)) {
            finish(null);
            return;
        }
        JSONObject meta = LiveScheduler.game(this, tipOff);
        String comp = meta.optString("c", "");
        // Winner League / Winner Cup: everything (names, score, quarter, clock, status, play-by-play) from the
        // official live stats, basket.co.il/livestats; from 365scores only the crests (fetched once)
        boolean official = "league".equals(comp) || "wcup".equals(comp);
        // EuroCup: everything from the competition's official live API (live.euroleague.net); from 365scores only the
        // crests, the team names and the competition / stage text (fetched once)
        int eg = "euro".equals(comp) ? meta.optInt("eg", 0) : 0;
        if (!(official || eg > 0) || (g365 == null && now - at365 >= 30_000L)) {
            at365 = now;
            try {
                JSONObject g = pick(new JSONObject(LivePlay.get(API)).optJSONArray("games"));
                if (g != null) g365 = g;
            } catch (Exception ignored) {
            }
        }
        LiveGame x = null;
        int state = 0;
        if (official) {
            try {
                int id = LivePlay.segevId(tipOff);
                if (id > 0) {
                    JSONObject d = LivePlay.segevData(id);
                    x = LivePlay.baseGame(d);
                    if (x != null) {
                        // Crests, team names (without sponsors) and competition / stage from 365scores; the game itself
                        // from livestats
                        LiveGame c365 = g365 != null ? LiveGame.from365(g365) : null;
                        if (c365 != null) {
                            boolean same = c365.meHome == x.meHome;
                            x.homeImg = same ? c365.homeImg : c365.awayImg;
                            x.awayImg = same ? c365.awayImg : c365.homeImg;
                            x.homeName = same ? c365.homeName : c365.awayName;
                            x.awayName = same ? c365.awayName : c365.homeName;
                        } else if (!meta.optString("o", "").isEmpty()) {
                            // Until 365scores answers: the opponent as the app names it (also without sponsors)
                            if (x.meHome) x.awayName = meta.optString("o");
                            else x.homeName = meta.optString("o");
                        }
                        // Competition and stage from 365scores (e.g. "ליגת ווינר – מחזור 5", "גביע ווינר – חצי גמר");
                        // until 365scores answers, as the app writes it
                        String label = meta.optString("l", "");
                        x.comp = c365 != null && !c365.comp.isEmpty() ? c365.comp
                            : !label.isEmpty() ? label : "league".equals(comp) ? "ליגת ווינר סל" : "גביע ווינר סל";
                        state = LivePlay.applyLive(d, x);
                        String play = LivePlay.segevLine(d, x);
                        if (play != null && !play.isEmpty()) lastPlay = play;
                        x.play = lastPlay;
                        x.t = tipOff;
                    }
                }
            } catch (Exception e) {
                x = null; // livestats did not answer: 365scores for this update
            }
        }
        if (eg > 0) {
            try {
                JSONObject h = LivePlay.euroHeader(eg);
                LiveGame e = new LiveGame();
                state = LivePlay.applyEuro(h, e);
                LiveGame c365 = g365 != null ? LiveGame.from365(g365) : null;
                String opp = meta.optString("o", "");
                if (c365 != null) {
                    boolean same = c365.meHome == e.meHome;
                    e.homeImg = same ? c365.homeImg : c365.awayImg;
                    e.awayImg = same ? c365.awayImg : c365.homeImg;
                    e.homeName = same ? c365.homeName : c365.awayName;
                    e.awayName = same ? c365.awayName : c365.homeName;
                } else {
                    e.homeName = e.meHome ? LiveGame.TEAM : opp;
                    e.awayName = e.meHome ? opp : LiveGame.TEAM;
                }
                String label = meta.optString("l", "");
                e.comp = c365 != null && !c365.comp.isEmpty() ? c365.comp : !label.isEmpty() ? label : "יורוקאפ";
                e.t = tipOff;
                if (state == LivePlay.LIVE && (now - lastPlayAt >= 6_000L || (lastHs >= 0 && (e.hs != lastHs || e.as != lastAs)))) {
                    lastPlayAt = now;
                    String play = LivePlay.latest("euro", eg, tipOff, e, LiveScheduler.roster(this));
                    if (play != null && !play.isEmpty()) lastPlay = play;
                }
                e.play = lastPlay;
                x = e;
            } catch (Exception e) {
                x = null; // the EuroCup API did not answer: 365scores for this update
                state = 0;
            }
        }
        if (x == null && g365 != null) {
            state = g365.optInt("statusGroup");
            x = game(g365, lastHs >= 0 && scoreChanged365(g365));
        }
        if (x != null) {
            if (state == LivePlay.FINAL) {
                finish(wasLive ? x : null);
                return;
            }
            if (state == LivePlay.LIVE) {
                wasLive = true;
                show(x);
                alerts(x);
            } else {
                wait = now > tipOff - 5 * MIN ? NEAR_POLL : PRE_POLL; // not started yet
            }
        }
        if (now > tipOff + 4 * HOUR) { // never runs for more than 4 hours after the scheduled tip-off
            finish(null);
            return;
        }
        if (handler != null) handler.postDelayed(this::poll, wait);
    }

    private boolean scoreChanged365(JSONObject g) {
        JSONObject h = g.optJSONObject("homeCompetitor"), a = g.optJSONObject("awayCompetitor");
        int hs = h != null ? (int) Math.max(0, Math.round(h.optDouble("score", 0))) : 0;
        int as = a != null ? (int) Math.max(0, Math.round(a.optDouble("score", 0))) : 0;
        return hs != lastHs || as != lastAs;
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
    private long lastPlayAt;

    /** The game from 365scores, with the latest play-by-play line (fetched every 6 seconds, or right away when the
     *  score changed – those files are bigger than the score) and the tip-off that identifies it. */
    private LiveGame game(JSONObject g, boolean scoreChanged) {
        LiveGame x = LiveGame.from365(g);
        if (x == null) return null;
        x.t = tipOff;
        long now = System.currentTimeMillis();
        if (scoreChanged || now - lastPlayAt >= 6_000L) {
            lastPlayAt = now;
            JSONObject meta = LiveScheduler.game(this, tipOff);
            String play = LivePlay.latest(meta.optString("c", ""), meta.optInt("eg", 0), tipOff, x, LiveScheduler.roster(this));
            if (play != null && !play.isEmpty()) lastPlay = play;
        }
        x.play = lastPlay;
        return x;
    }

    private long popUntil;

    private void show(LiveGame x) {
        boolean scored = lastHs >= 0 && (x.hs != lastHs || x.as != lastAs);
        long now = System.currentTimeMillis();
        // +1 / +2 / +3 next to the score that just changed (not on the first update)
        if (scored) {
            x.popHome = LiveGame.delta(x.hs, lastHs);
            x.popAway = LiveGame.delta(x.as, lastAs);
        }
        lastHs = x.hs;
        lastAs = x.as;
        if (x.popHome > 0 || x.popAway > 0) {
            popUntil = now + 2_600L;
            post(x);
            return;
        }
        // While a +N is on screen (it fades by itself after 2.2 s), clock-only updates wait, so they don't restart it
        if (!scored && now < popUntil) return;
        post(x);
    }

    private void post(LiveGame x) {
        if (x.key().equals(lastKey)) return;
        lastKey = x.key();
        Intent stop = new Intent(this, LiveScoreService.class).setAction(ACTION_STOP);
        PendingIntent del = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        current = LiveGame.card(this, x, LiveGame.crestFor(this, x.homeName, x.homeImg), LiveGame.crestFor(this, x.awayName, x.awayImg), false, del);
        notify(NOTIF_ID, current);
    }

    /** Ends following the game; with a final game, the card stays for 30 minutes with the final score. */
    private void finish(LiveGame x) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (x != null && LiveScheduler.allowed(this)) {
            finalAlert(x);
            notify(NOTIF_ID, LiveGame.card(this, x, LiveGame.crestFor(this, x.homeName, x.homeImg), LiveGame.crestFor(this, x.awayName, x.awayImg), true, null));
            finalShown = true;
        }
        LiveScheduler.done(this, tipOff);
        LiveScheduler.cancelWatchdog(this);
        finished = true;
        NotifGroup.refresh(this);
        stopSelf();
    }

    // ---------- in-game notifications: tip-off, end of each quarter, final ----------

    static final String GAME_CHANNEL = "hpj-game";
    private int lastQuarter;

    /** Quarter in progress from 365scores' status ("רבע 3" → 3, overtime → 5+), 0 during a break. */
    private static int quarter(String status) {
        if (status == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("רבע\\s*(\\d)").matcher(status);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = java.util.regex.Pattern.compile("(?i)^Q(\\d)").matcher(status.trim());
        if (m.find()) return Integer.parseInt(m.group(1));
        if (status.contains("הארכה") || status.toUpperCase(java.util.Locale.ROOT).startsWith("OT")) {
            m = java.util.regex.Pattern.compile("(\\d)").matcher(status);
            return 4 + (m.find() ? Integer.parseInt(m.group(1)) : 1);
        }
        return 0;
    }

    private boolean alertsOn() {
        return LiveScheduler.allowed(this) && LiveScheduler.quarterAlerts(this)
            && LiveScheduler.compOn(this, LiveScheduler.game(this, tipOff).optString("c", ""));
    }

    private int maxQuarter;

    private void alerts(LiveGame x) {
        if (x == null || !alertsOn()) return;
        if (LiveScheduler.alertOnce(this, tipOff, "start")) GameAlerts.start(this, x, tipOff);
        int q = quarter(x.status);
        // The quarter that just ended: a break after it, or the next quarter already started
        int ended = q == 0 ? lastQuarter : (lastQuarter > 0 && q > lastQuarter ? lastQuarter : 0);
        // After the 4th quarter / an overtime only when the score is tied (another overtime follows); otherwise the
        // game is over and the final notification says it
        boolean announce = ended > 0 && (ended < 4 || x.hs == x.as || q > ended);
        if (announce && LiveScheduler.alertOnce(this, tipOff, "q" + ended)) GameAlerts.quarter(this, x, ended, tipOff);
        if (q > 0) {
            lastQuarter = q;
            maxQuarter = Math.max(maxQuarter, q);
        }
    }

    private void finalAlert(LiveGame x) {
        if (!alertsOn() || !LiveScheduler.alertOnce(this, tipOff, "end")) return;
        GameAlerts.fin(this, x, maxQuarter >= 5, tipOff);
    }

    static void ensureGameChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(GAME_CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(GAME_CHANNEL, "עדכוני משחק", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("שריקת הפתיחה, סיום כל רבע וסיום המשחק");
        nm.createNotificationChannel(ch);
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
        if (nm == null) return;
        // The app's live card – as in 1.110 (if the channel was removed by 1.123–1.130, re-creating it restores it
        // with the settings it had)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "תוצאה בלייב", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("התוצאה של המשחק בזמן אמת, בזמן שהפועל ירושלים משחקת");
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        // The Now Bar (when chosen in the settings): low – no sound, no pop-up; a Live Update only rules out MIN
        if (nm.getNotificationChannel(NOW_CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(NOW_CHANNEL, "תוצאה בלייב ב-Now Bar", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("התוצאה של המשחק בזמן אמת ב-Now Bar, כשהאפשרות נבחרה בהגדרות האפליקציה");
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

}
