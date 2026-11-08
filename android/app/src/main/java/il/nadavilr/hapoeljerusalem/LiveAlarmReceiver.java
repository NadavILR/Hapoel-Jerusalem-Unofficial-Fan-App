package il.nadavilr.hapoeljerusalem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Tip-off alarm: starts the live score notification. After a restart or an app update: re-arms the alarms. */
public class LiveAlarmReceiver extends BroadcastReceiver {

    static final String ACTION_TIPOFF = "il.nadavilr.hapoeljerusalem.LIVE_TIPOFF";

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent != null ? intent.getAction() : null;
        if (ACTION_TIPOFF.equals(a)) {
            long t = intent.getLongExtra("t", 0);
            if (t > 0 && LiveScheduler.allowed(context)) {
                try {
                    LiveScoreService.start(context, t);
                } catch (Exception ignored) {
                    // Android refused to start it from the background (e.g. the exact alarm was not allowed)
                }
            }
            return;
        }
        // BOOT_COMPLETED / MY_PACKAGE_REPLACED: alarms are cleared by a restart
        LiveScheduler.reschedule(context);
        long t = LiveScheduler.current(context);
        if (t > 0 && LiveScheduler.allowed(context)) {
            try {
                LiveScoreService.start(context, t);
            } catch (Exception ignored) {
            }
        }
    }
}
