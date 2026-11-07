package il.nadavilr.hapoeljerusalem;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * The background update check was removed: the app now checks for a new version only when it opens,
 * and shows the update screen right away. This class stays only so that a check that older versions
 * already scheduled can still run once (doing nothing) and is then cancelled.
 */
public class UpdateCheckWorker extends Worker {

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Called from MainActivity: removes the periodic check that versions up to 1.41 scheduled. */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("hpj-update-check");
    }

    @NonNull
    @Override
    public Result doWork() {
        return Result.success();
    }
}
