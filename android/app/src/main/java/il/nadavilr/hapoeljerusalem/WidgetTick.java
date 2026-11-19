package il.nadavilr.hapoeljerusalem;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * The widgets' clock: every 5 seconds, on a 5-second grid (so the turn of each minute is one of the ticks), while the
 * screen is on. The seconds in the widgets are flippers that turn by themselves; a tick puts them back on the real second
 * (they pause while the home screen is hidden), and at a new minute redraws the widgets (the clock, the countdown's
 * minutes, the dates). The alarm does not wake the phone: while the screen is off ticks wait, and the one that came due
 * fires as soon as the screen turns on – so the widgets are right the moment they are seen.
 */
public class WidgetTick extends BroadcastReceiver {

    private static final long STEP = 5_000L;

    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult pr = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                ClockWidget.tick(app);
                NextGameWidget.tick(app);
            } catch (Exception ignored) {
            } finally {
                schedule(app);
                pr.finish();
            }
        }).start();
    }

    /** The next tick – only while there is a widget that shows seconds. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 7310, new Intent(c, WidgetTick.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (!ClockWidget.has(c) && !NextGameWidget.ticking(c)) {
            am.cancel(pi);
            return;
        }
        long now = System.currentTimeMillis();
        // the next point on the 5-second grid at least 5 s away (Android moves sooner alarms to 5 s anyway), + 150 ms
        long at = ((now + 2 * STEP - 1) / STEP) * STEP + 150L;
        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) am.setExact(AlarmManager.RTC, at, pi);
            else am.setWindow(AlarmManager.RTC, at, 1_000L, pi);
        } catch (Exception ignored) {
        }
    }
}
