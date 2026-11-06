package il.nadavilr.hapoeljerusalem;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Shows the app's bottom navigation bar on top of the club's websites (for example the tickets site),
 * so they feel like part of the app. The bar comes from www/native-nav.js and is injected after
 * each club page finishes loading.
 */
public class MainActivity extends BridgeActivity {

    private String navScript;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        navScript = readAsset("public/native-nav.js");
        if (bridge == null) return;
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (navScript != null && isClubSite(url)) {
                    view.evaluateJavascript(navScript, null);
                }
            }
        });
    }

    private static boolean isClubSite(String url) {
        if (url == null) return false;
        String host = Uri.parse(url).getHost();
        return host != null && (host.equals("hapoel.co.il") || host.endsWith(".hapoel.co.il"));
    }

    private String readAsset(String path) {
        try (InputStream in = getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
