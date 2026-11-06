package il.nadavilr.hapoeljerusalem;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Background check for a new app version (every ~6 hours, when online), also while the app is closed.
 * When the latest GitHub release is newer than the installed version it posts one notification per
 * version; tapping it opens the app, which shows the update screen.
 */
public class UpdateCheckWorker extends Worker {

    private static final String RELEASES_API =
        "https://api.github.com/repos/NadavILR/Hapoel-Jerusalem-Unofficial-Fan-App/releases/latest";
    private static final String CHANNEL = "app_updates";
    private static final int NOTIFICATION_ID = 7001;

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Called from MainActivity: keeps one periodic check scheduled. */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(UpdateCheckWorker.class, 6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("hpj-update-check", ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        HttpURLConnection conn = null;
        try {
            Context ctx = getApplicationContext();
            conn = (HttpURLConnection) new URL(RELEASES_API).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() != 200) return Result.success();
            String body;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                body = out.toString("UTF-8");
            }
            JSONObject json = new JSONObject(body);
            String latest = json.optString("tag_name", "").replaceFirst("^v", "");
            if (!latest.matches("\\d+\\.\\d+\\.\\d+") || !hasApk(json.optJSONArray("assets"))) return Result.success();

            String installed = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
            if (compare(latest, installed) <= 0) return Result.success();

            SharedPreferences prefs = ctx.getSharedPreferences("hpj_updates", Context.MODE_PRIVATE);
            if (latest.equals(prefs.getString("notified", ""))) return Result.success();

            if (notifyUpdate(ctx, latest)) prefs.edit().putString("notified", latest).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean hasApk(JSONArray assets) {
        if (assets == null) return false;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && a.optString("name", "").toLowerCase().endsWith(".apk")) return true;
        }
        return false;
    }

    private static int compare(String a, String b) {
        String[] x = a.split("\\."), y = (b == null ? "0.0.0" : b).split("\\.");
        for (int i = 0; i < 3; i++) {
            int xi = i < x.length ? parse(x[i]) : 0, yi = i < y.length ? parse(y[i]) : 0;
            if (xi != yi) return xi - yi;
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean notifyUpdate(Context ctx, String version) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        if (!nm.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "עדכוני אפליקציה", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("הודעה כשיש גרסה חדשה של האפליקציה");
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, flags);
        String text = "גרסה " + version + " זמינה. כדי להמשיך להשתמש באפליקציה יש לעדכן.";
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hpj)
            .setColor(Color.parseColor("#D21919"))
            .setContentTitle("עדכון חדש זמין")
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true);
        try {
            nm.notify(NOTIFICATION_ID, b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
