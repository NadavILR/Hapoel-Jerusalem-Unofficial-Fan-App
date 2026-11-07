package il.nadavilr.hapoeljerusalem;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

/**
 * - Links from the app to outside websites open in a window over the app, with a top bar whose
 *   "חזור" button (top right) closes it and returns to the exact screen the link was tapped on.
 *   The device Back button goes back inside that website first, then closes it.
 * - The tickets tab is a separate screen (TicketsPlugin) that keeps the app's bottom navigation.
 * - Device Back follows the app's own screens (see window.hpjBack in index.html) and closes the app at home.
 */
public class MainActivity extends BridgeActivity {

    private LinearLayout extLayer;
    private WebView extWeb;
    private OnBackPressedCallback extBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(TicketsPlugin.class);
        registerPlugin(UpdaterPlugin.class);
        super.onCreate(savedInstanceState);
        // Status bar: club red (#D21919) with white icons
        getWindow().setStatusBarColor(Color.parseColor("#D21919"));
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        // Check for app updates in the background too, so a notification arrives even when the app is closed
        try {
            UpdateCheckWorker.schedule(this);
        } catch (Exception ignored) {
        }
        if (bridge == null) return;

        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                // Embedded frames (the game highlights video, shop / membership pages) stay inside the page
                if (isExternalWebPage(url) && request.isForMainFrame()) {
                    openExternal(url.toString());
                    return true; // the app itself stays where it is, underneath
                }
                return super.shouldOverrideUrlLoading(view, request);
            }
        });

        // Device Back inside the app: the page decides (window.hpjBack - notifications panel, game/player
        // page, a tab's main screen -> home). At the home screen it returns false and the app closes as usual.
        // Registered first, so the tickets screen and external-site windows (added later) handle Back before it.
        OnBackPressedCallback appBack = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                bridge.getWebView().evaluateJavascript(
                    "(function(){try{return !!(window.hpjBack&&window.hpjBack())}catch(e){return false}})()",
                    result -> {
                        if (!"true".equals(result)) {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                            setEnabled(true);
                        }
                    });
            }
        };
        getOnBackPressedDispatcher().addCallback(this, appBack);
    }

    private static boolean isExternalWebPage(Uri url) {
        if (url == null) return false;
        String scheme = url.getScheme(), host = url.getHost();
        return ("https".equals(scheme) || "http".equals(scheme)) && host != null && !host.equals("localhost");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openExternal(String url) {
        if (extLayer != null) {
            extWeb.loadUrl(url);
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        LinearLayout layer = new LinearLayout(this);
        layer.setOrientation(LinearLayout.VERTICAL);
        layer.setBackgroundColor(dark ? Color.BLACK : Color.WHITE);
        layer.setClickable(true); // touches never reach the app underneath

        // Top bar (right-to-left): "→ חזור" on the right, the page title next to it
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        // Top bar like the app's header: white in light mode, black in dark mode, with a thin red line under it
        bar.setBackgroundColor(dark ? Color.BLACK : Color.WHITE);
        int barText = dark ? Color.WHITE : Color.parseColor("#282828");

        TextView back = new TextView(this);
        back.setText("→  חזור");
        back.setTextColor(barText);
        back.setTextSize(17);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding((int) (16 * d), 0, (int) (16 * d), 0);
        back.setContentDescription("חזור");
        back.setOnClickListener(v -> closeExternal());
        bar.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setTextColor(dark ? 0xB3FFFFFF : 0xB3282828);
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setPadding(0, 0, (int) (16 * d), 0);
        title.setText(Uri.parse(url).getHost());
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        layer.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (52 * d)));
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#D21919"));
        layer.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) (2 * d))));

        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // No pinch / double-tap zoom anywhere in the app
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String t) {
                if (t != null && !t.isEmpty() && !t.startsWith("http")) title.setText(t);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false; // stay in this window
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u)); // tel:, mailto:, whatsapp:...
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        layer.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ViewGroup parent = (ViewGroup) bridge.getWebView().getParent();
        parent.addView(layer, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.bringToFront();
        extLayer = layer;
        extWeb = web;
        web.loadUrl(url);

        // Device Back: back inside the website, then close it (added last, so it runs before other handlers)
        extBack = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (extWeb != null && extWeb.canGoBack()) extWeb.goBack();
                else closeExternal();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, extBack);
    }

    private void closeExternal() {
        if (extLayer == null) return;
        ViewGroup parent = (ViewGroup) extLayer.getParent();
        if (parent != null) parent.removeView(extLayer);
        if (extWeb != null) {
            extWeb.stopLoading();
            extWeb.destroy();
        }
        extLayer = null;
        extWeb = null;
        if (extBack != null) {
            extBack.remove();
            extBack = null;
        }
    }
}
