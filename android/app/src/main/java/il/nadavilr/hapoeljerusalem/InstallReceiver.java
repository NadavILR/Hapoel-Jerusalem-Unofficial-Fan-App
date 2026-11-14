package il.nadavilr.hapoeljerusalem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/**
 * The result of an in-app update installed through PackageInstaller (UpdaterPlugin). When Android needs the user to
 * confirm, it hands over the confirmation screen here, and it is opened right away – one tap on "עדכון עכשיו" is enough.
 */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return;
        Intent confirm = Build.VERSION.SDK_INT >= 33
            ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
            : intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirm == null) return;
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(confirm); } catch (Exception ignored) { }
    }
}
