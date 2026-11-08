package il.nadavilr.hapoeljerusalem;

import android.content.Context;
import android.graphics.Color;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * The in-game notifications (not the live score card) – their wording and how they are posted. Used by the live
 * score service during a game and by the app's notification test screen, so both show exactly the same thing.
 *
 *   tip-off              title "המשחק התחיל"                      text  competition, round/stage ⏎ home מול away
 *   end of quarter 1     "סיום רבע ראשון"                          home 00 - 00 away
 *   end of quarter 2     "מחצית"
 *   end of quarter 3     "סיום רבע שלישי"
 *   end of quarter 4     "סיום רבע רביעי"           (only when overtime follows)
 *   end of overtime N    "סיום הארכה N"             (only when another overtime follows)
 *   final                "סיום המשחק – ניצחון/הפסד"  or "סיום המשחק (לאחר הארכה) - ניצחון/הפסד"
 */
final class GameAlerts {

    static final int START = 63420, QUARTER = 63421, FINAL = 63430;
    private static final String RLM = "‏";

    private GameAlerts() {
    }

    static String quarterTitle(int q) {
        switch (q) {
            case 1: return "סיום רבע ראשון";
            case 2: return "מחצית";
            case 3: return "סיום רבע שלישי";
            case 4: return "סיום רבע רביעי";
            default: return "סיום הארכה " + (q - 4);
        }
    }

    static String finalTitle(boolean win, boolean overtime) {
        return overtime ? "סיום המשחק (לאחר הארכה) - " + (win ? "ניצחון" : "הפסד") : "סיום המשחק – " + (win ? "ניצחון" : "הפסד");
    }

    /** "home 00 - 00 away" (home team and its score on the right). */
    static String score(LiveGame x) {
        return x.homeName + " " + x.hs + " - " + x.as + " " + x.awayName;
    }

    /** "home מול away". */
    static String matchup(LiveGame x) {
        return x.homeName + " מול " + x.awayName;
    }

    static boolean hapoelWins(LiveGame x) {
        return x.meHome ? x.hs > x.as : x.as > x.hs;
    }

    /** Tip-off: the competition and round/stage on the first line, the teams on the second. */
    static void start(Context c, LiveGame x, long tipOff) {
        post(c, START, "המשחק התחיל", x.comp, matchup(x), tipOff);
    }

    static void quarter(Context c, LiveGame x, int q, long tipOff) {
        post(c, QUARTER + q, quarterTitle(q), null, score(x), tipOff);
    }

    static void fin(Context c, LiveGame x, boolean overtime, long tipOff) {
        post(c, FINAL, finalTitle(hapoelWins(x), overtime), null, score(x), tipOff);
    }

    /**
     * Posts one notification: {@code title}, and the text – {@code line1} (may be null) above {@code line2}. Collapsed
     * it shows the second line (the teams / the score); expanded both. In the app's group, under the live card.
     */
    static void post(Context c, int id, String title, String line1, String line2, long tipOff) {
        LiveScoreService.ensureGameChannel(c);
        String text = (line1 != null && !line1.isEmpty() ? RLM + line1 + "\n" : "") + RLM + line2;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, LiveScoreService.GAME_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hpj)
            .setColor(Color.parseColor("#D21919"))
            .setContentTitle(RLM + title)
            .setContentText(RLM + line2)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(LiveGame.open(c, tipOff))
            .setAutoCancel(true)
            .setGroup(NotifGroup.KEY)
            .setSortKey("1")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(c).notify(id, b.build());
        } catch (SecurityException ignored) {
        }
        NotifGroup.refresh(c);
    }
}
