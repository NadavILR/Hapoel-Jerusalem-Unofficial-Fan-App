package il.nadavilr.hapoeljerusalem;

import android.graphics.Color;
import android.net.Uri;
import androidx.core.view.WindowInsetsControllerCompat;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;

/**
 * - Web pages outside the app (the tickets site and the other club sites) open inside the app's own
 *   WebView and load directly, at full speed (not through Capacitor's allowNavigation proxy).
 * - The app's bottom navigation bar (www/native-nav.js) is drawn on top of those pages. It is added
 *   at document start where the WebView supports it, and again when the page becomes visible /
 *   finishes loading (the script runs only once per page and skips the app itself).
 */
public class MainActivity extends BridgeActivity {

    private String navScript;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(TicketsPlugin.class);
        super.onCreate(savedInstanceState);
        // Status bar: club red (#D21919) with white icons
        getWindow().setStatusBarColor(Color.parseColor("#D21919"));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        navScript = readAsset("public/native-nav.js");
        if (bridge == null) return;
        WebView webView = bridge.getWebView();

        if (navScript != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(webView, navScript, Collections.singleton("*"));
            } catch (Exception ignored) {
            }
        }

        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (isExternalWebPage(url)) return false; // load it here, in the app
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                inject(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                inject(view, url);
            }
        });
    }

    private void inject(WebView view, String url) {
        if (navScript != null && url != null && isExternalWebPage(Uri.parse(url))) {
            view.evaluateJavascript(navScript, null);
        }
    }

    private static boolean isExternalWebPage(Uri url) {
        if (url == null) return false;
        String scheme = url.getScheme(), host = url.getHost();
        return ("https".equals(scheme) || "http".equals(scheme)) && host != null && !host.equals("localhost");
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
